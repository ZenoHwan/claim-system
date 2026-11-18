package com.claims.service.claim;

import com.claims.service.common.ApiException;
import com.claims.service.policy.LineOfBusiness;
import com.claims.service.policy.Policy;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The claim aggregate. Status only ever changes through the methods below, each of which goes
 * through {@link #moveTo}, so an illegal transition cannot be written by any caller.
 */
@Entity
@Table(name = "claim")
public class Claim {

    @Id
    private UUID id;

    private String claimNumber;
    private UUID policyId;
    private UUID claimantId;
    private UUID assignedOfficerId;

    @Enumerated(EnumType.STRING)
    private LineOfBusiness lineOfBusiness;

    private String marketCode;
    private String currency;

    @Enumerated(EnumType.STRING)
    private ClaimStatus status;

    private LocalDate incidentDate;
    private String incidentDescription;
    private BigDecimal claimedAmount;
    private BigDecimal currentReserve;

    /** Optimistic lock: of two concurrent changes to one claim, the second to commit is refused. */
    @Version
    private long version;

    private Instant submittedAt;
    private Instant updatedAt;
    private Instant decidedAt;
    private Instant settledAt;

    protected Claim() {}

    public static Claim submit(
            String claimNumber, Policy policy, UUID claimantId, LocalDate incidentDate,
            String incidentDescription, BigDecimal claimedAmount, BigDecimal initialReserve, Instant now) {
        Claim claim = new Claim();
        claim.id = UUID.randomUUID();
        claim.claimNumber = claimNumber;
        claim.policyId = policy.getId();
        claim.claimantId = claimantId;
        claim.lineOfBusiness = policy.getLineOfBusiness();
        claim.marketCode = policy.getMarketCode();
        claim.currency = policy.getCurrency();
        claim.status = ClaimStatus.SUBMITTED;
        claim.incidentDate = incidentDate;
        claim.incidentDescription = incidentDescription;
        claim.claimedAmount = claimedAmount;
        // An unassessed claim must still count toward exposure, whether or not the claimant gave a figure.
        claim.currentReserve = initialReserve;
        claim.submittedAt = now;
        claim.updatedAt = now;
        return claim;
    }

    public void pickUpBy(UUID officerId, Instant now) {
        moveTo(ClaimStatus.UNDER_REVIEW, now);
        assignedOfficerId = officerId;
    }

    public void requestInformation(Instant now) {
        moveTo(ClaimStatus.INFO_REQUESTED, now);
    }

    public void informationProvided(Instant now) {
        moveTo(ClaimStatus.UNDER_REVIEW, now);
    }

    public void assess(BigDecimal recommendedAmount, Instant now) {
        moveTo(ClaimStatus.ASSESSED, now);
        currentReserve = recommendedAmount;
    }

    public void approve(Instant now) {
        moveTo(ClaimStatus.APPROVED, now);
        decidedAt = now;
    }

    public void reject(Instant now) {
        moveTo(ClaimStatus.REJECTED, now);
        decidedAt = now;
        currentReserve = BigDecimal.ZERO;
    }

    public void settle(Instant now) {
        moveTo(ClaimStatus.SETTLED, now);
        settledAt = now;
        currentReserve = BigDecimal.ZERO;
    }

    private void moveTo(ClaimStatus target, Instant now) {
        if (!status.canMoveTo(target)) {
            throw ApiException.conflict("A claim that is " + status + " cannot become " + target);
        }
        status = target;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public UUID getClaimantId() {
        return claimantId;
    }

    public UUID getAssignedOfficerId() {
        return assignedOfficerId;
    }

    public LineOfBusiness getLineOfBusiness() {
        return lineOfBusiness;
    }

    public String getMarketCode() {
        return marketCode;
    }

    public String getCurrency() {
        return currency;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public LocalDate getIncidentDate() {
        return incidentDate;
    }

    public String getIncidentDescription() {
        return incidentDescription;
    }

    public BigDecimal getClaimedAmount() {
        return claimedAmount;
    }

    public BigDecimal getCurrentReserve() {
        return currentReserve;
    }

    public long getVersion() {
        return version;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
