package com.claims.service.claim;

import com.claims.events.ClaimEventType;
import com.claims.service.common.ApiException;
import com.claims.service.identity.CurrentUser;
import com.claims.service.policy.LineOfBusiness;
import com.claims.service.policy.Policy;
import com.claims.service.policy.PolicyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The claim use cases that change state. One transaction each. */
@Service
@Transactional
class ClaimService {

    record AssessmentRequest(
            @jakarta.validation.constraints.NotBlank String findings,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero BigDecimal recommendedAmount) {}

    record DecisionRequest(@jakarta.validation.constraints.NotNull Decision.Outcome outcome, BigDecimal approvedAmount, String reason) {}

    private final ClaimAccess access;
    private final ClaimChanges changes;
    private final PolicyRepository policies;
    private final ClaimRecords.MotorDetails motorDetails;
    private final ClaimRecords.PropertyDetails propertyDetails;
    private final ClaimRecords.Reserves reserves;
    private final ClaimRecords.Assessments assessments;
    private final ClaimRecords.Decisions decisions;
    private final SettlementService settlement;
    private final JdbcTemplate jdbc;
    private final Map<LineOfBusiness, BigDecimal> defaultReserveRates;

    ClaimService(
            ClaimAccess access, ClaimChanges changes, PolicyRepository policies,
            ClaimRecords.MotorDetails motorDetails, ClaimRecords.PropertyDetails propertyDetails,
            ClaimRecords.Reserves reserves, ClaimRecords.Assessments assessments, ClaimRecords.Decisions decisions,
            SettlementService settlement, JdbcTemplate jdbc,
            @Value("${claims.reserve.default-rate.motor}") BigDecimal motorRate,
            @Value("${claims.reserve.default-rate.property}") BigDecimal propertyRate) {
        this.access = access;
        this.changes = changes;
        this.policies = policies;
        this.motorDetails = motorDetails;
        this.propertyDetails = propertyDetails;
        this.reserves = reserves;
        this.assessments = assessments;
        this.decisions = decisions;
        this.settlement = settlement;
        this.jdbc = jdbc;
        this.defaultReserveRates = Map.of(LineOfBusiness.MOTOR, motorRate, LineOfBusiness.PROPERTY, propertyRate);
    }

    UUID submit(CurrentUser claimant, SubmitClaimRequest request) {
        Policy policy = policies.findById(request.policyId())
                .filter(found -> found.getHolderId().equals(claimant.id()))
                .orElseThrow(() -> ApiException.notFound("Policy"));
        if (!policy.covers(request.incidentDate())) {
            throw ApiException.invalid("incidentDate", "Incident date is outside the policy period "
                    + policy.getEffectiveFrom() + " to " + policy.getEffectiveTo());
        }
        boolean motor = policy.getLineOfBusiness() == LineOfBusiness.MOTOR;
        if (motor && request.motor() == null) {
            throw ApiException.invalid("motor", "Motor details are required for a motor policy");
        }
        if (!motor && request.property() == null) {
            throw ApiException.invalid("property", "Property details are required for a property policy");
        }

        Instant now = Instant.now();
        Claim claim = Claim.submit(
                nextClaimNumber(now), policy, claimant.id(), request.incidentDate(),
                request.incidentDescription(), request.claimedAmount(), initialReserve(policy, request.claimedAmount()), now);
        changes.record(claim, null, claimant.id(), null, ClaimEventType.CLAIM_SUBMITTED);
        if (motor) {
            motorDetails.save(new MotorClaimDetail(claim.getId(), request.motor()));
        } else {
            propertyDetails.save(new PropertyClaimDetail(claim.getId(), request.property()));
        }
        reserves.save(new Reserve(claim.getId(), claim.getCurrentReserve(), Reserve.Reason.INITIAL, null, now));
        return claim.getId();
    }

    /**
     * The claimant's estimate when they gave one; otherwise a share of the sum insured for that line.
     * A percentage keeps the default meaningful in every market's currency.
     */
    // ponytail: flat rate per line. Real insurers use average claim cost by market and peril; replace the rates with a table.
    private BigDecimal initialReserve(Policy policy, BigDecimal claimedAmount) {
        if (claimedAmount != null) {
            return claimedAmount;
        }
        return policy.getSumInsured()
                .multiply(defaultReserveRates.get(policy.getLineOfBusiness()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String nextClaimNumber(Instant now) {
        long sequence = jdbc.queryForObject("select nextval('claim_number_seq')", Long.class);
        return "CLM-%d-%06d".formatted(now.atZone(ZoneOffset.UTC).getYear(), sequence);
    }

    void pickUp(CurrentUser staff, UUID claimId) {
        Claim claim = access.visibleTo(staff, claimId);
        ClaimStatus from = claim.getStatus();
        claim.pickUpBy(staff.id(), Instant.now());
        changes.record(claim, from, staff.id(), null, ClaimEventType.CLAIM_ASSIGNED);
    }

    void assess(CurrentUser staff, UUID claimId, AssessmentRequest request) {
        Claim claim = access.assignedTo(staff, claimId);
        ClaimStatus from = claim.getStatus();
        Instant now = Instant.now();
        claim.assess(request.recommendedAmount(), now);
        changes.record(claim, from, staff.id(), null, ClaimEventType.CLAIM_ASSESSED);
        assessments.save(new Assessment(claimId, staff.id(), request.findings(), request.recommendedAmount(), now));
        reserves.save(new Reserve(claimId, request.recommendedAmount(), Reserve.Reason.ASSESSMENT, staff.id(), now));
    }

    void decide(CurrentUser staff, UUID claimId, DecisionRequest request) {
        Claim claim = access.assignedTo(staff, claimId);
        ClaimStatus from = claim.getStatus();
        Instant now = Instant.now();
        if (request.outcome() == Decision.Outcome.APPROVED) {
            approve(claim, request.approvedAmount(), now);
            changes.record(claim, from, staff.id(), null, ClaimEventType.CLAIM_APPROVED);
            decisions.save(new Decision(claimId, Decision.Outcome.APPROVED, request.approvedAmount(), request.reason(), staff.id(), now));
            settlement.raisePayment(claim, request.approvedAmount(), now);
        } else {
            if (request.reason() == null || request.reason().isBlank()) {
                throw ApiException.invalid("reason", "A reason is required to reject a claim");
            }
            claim.reject(now);
            changes.record(claim, from, staff.id(), request.reason(), ClaimEventType.CLAIM_REJECTED);
            decisions.save(new Decision(claimId, Decision.Outcome.REJECTED, null, request.reason(), staff.id(), now));
            reserves.save(new Reserve(claimId, BigDecimal.ZERO, Reserve.Reason.RELEASED, staff.id(), now));
        }
    }

    private void approve(Claim claim, BigDecimal amount, Instant now) {
        // Status first: "not assessed yet" is the more useful error than a complaint about the amount.
        claim.approve(now);
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.invalid("approvedAmount", "An approved amount greater than zero is required");
        }
        BigDecimal sumInsured = policies.findById(claim.getPolicyId()).orElseThrow().getSumInsured();
        if (amount.compareTo(sumInsured) > 0) {
            throw ApiException.invalid("approvedAmount", "Approved amount exceeds the sum insured of " + sumInsured);
        }
    }
}
