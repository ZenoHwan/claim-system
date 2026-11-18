package com.claims.service.claim;

import com.claims.service.common.ApiException;
import com.claims.service.identity.AppUser;
import com.claims.service.identity.AppUserRepository;
import com.claims.service.identity.CurrentUser;
import com.claims.service.policy.LineOfBusiness;
import com.claims.service.policy.Policy;
import com.claims.service.policy.PolicyRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the claims API: what a claim looks like to the person asking. */
@Component
@Transactional(readOnly = true)
public class ClaimViews {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ListItem(
            UUID id, String claimNumber, LineOfBusiness lineOfBusiness, String marketCode, String currency,
            ClaimStatus status, BigDecimal claimedAmount, LocalDate incidentDate, String claimantName,
            String assignedOfficerName, Instant submittedAt, Instant updatedAt) {}

    public record HistoryEntry(ClaimStatus fromStatus, ClaimStatus toStatus, String actorName, String reason, Instant changedAt) {}

    public record MotorView(String vehicleRegistration, String driverName, Address accidentLocation, boolean thirdPartyInvolved) {}

    public record PropertyView(Address propertyAddress, PropertyClaimDetail.LossCause lossCause) {}

    public record AssessmentView(String findings, BigDecimal recommendedAmount, String assessedByName, Instant assessedAt) {}

    public record DecisionView(Decision.Outcome outcome, BigDecimal approvedAmount, String reason, String decidedByName, Instant decidedAt) {}

    public record InformationRequestView(
            UUID id, String question, InformationRequest.Status status, String requestedByName, Instant requestedAt,
            String responseText, Instant answeredAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DocumentView(
            UUID id, String fileName, String contentType, long sizeBytes, UUID informationRequestId,
            String uploadedByName, Instant uploadedAt) {}

    /** {@code reference} is the insurer's own payment reference: staff only. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentView(
            String payeeName, Payment.Method method, BigDecimal amount, String currency, Payment.Status status,
            String reference, Instant requestedAt, Instant paidAt) {}

    /** {@code currentReserve} and {@code assessment} are internal figures: null, and so omitted, for a claimant. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(
            UUID id, String claimNumber, UUID policyId, String policyNumber, BigDecimal sumInsured,
            LineOfBusiness lineOfBusiness, String marketCode, String currency, ClaimStatus status,
            LocalDate incidentDate, String incidentDescription, BigDecimal claimedAmount,
            UUID claimantId, String claimantName, UUID assignedOfficerId, String assignedOfficerName,
            long version, Instant submittedAt, Instant updatedAt,
            MotorView motor, PropertyView property, List<HistoryEntry> history,
            BigDecimal currentReserve, AssessmentView assessment, DecisionView decision,
            List<InformationRequestView> informationRequests, List<DocumentView> documents, PaymentView payment) {}

    private final ClaimAccess access;
    private final ClaimRepository claims;
    private final AppUserRepository users;
    private final PolicyRepository policies;
    private final ClaimRecords.MotorDetails motorDetails;
    private final ClaimRecords.PropertyDetails propertyDetails;
    private final ClaimRecords.History history;
    private final ClaimRecords.Assessments assessments;
    private final ClaimRecords.Decisions decisions;
    private final ClaimRecords.InformationRequests informationRequests;
    private final ClaimRecords.Documents documents;
    private final ClaimRecords.Payments payments;

    ClaimViews(
            ClaimAccess access, ClaimRepository claims, AppUserRepository users, PolicyRepository policies,
            ClaimRecords.MotorDetails motorDetails, ClaimRecords.PropertyDetails propertyDetails,
            ClaimRecords.History history, ClaimRecords.Assessments assessments, ClaimRecords.Decisions decisions,
            ClaimRecords.InformationRequests informationRequests, ClaimRecords.Documents documents,
            ClaimRecords.Payments payments) {
        this.access = access;
        this.claims = claims;
        this.users = users;
        this.policies = policies;
        this.motorDetails = motorDetails;
        this.propertyDetails = propertyDetails;
        this.history = history;
        this.assessments = assessments;
        this.decisions = decisions;
        this.informationRequests = informationRequests;
        this.documents = documents;
        this.payments = payments;
    }

    /** A question to or from a claimant that still needs someone to act. */
    public record FollowUp(
            UUID claimId, String claimNumber, UUID requestId, String question, String requestedByName,
            InformationRequest.Status status, Instant requestedAt, String responseText, Instant answeredAt) {}

    public record PolicyDetail(
            UUID id, String policyNumber, LineOfBusiness lineOfBusiness, String marketCode, String currency,
            BigDecimal sumInsured, LocalDate effectiveFrom, LocalDate effectiveTo, List<ListItem> claims) {}

    /**
     * A claimant's follow-ups are the questions waiting for their reply. An officer's are the questions on
     * their claims still waiting for the claimant (OPEN), and replies they have not acted on yet (ANSWERED,
     * claim back under review). Either way it is the claim's latest question; acting on the claim clears it.
     */
    @Cacheable(cacheNames = CacheConfig.FOLLOW_UPS, key = "#user.id()")
    public List<FollowUp> followUps(CurrentUser user) {
        List<Claim> found = user.isStaff()
                ? claims.findByAssignedOfficerIdOrderByUpdatedAtDesc(user.id())
                : claims.findByClaimantIdOrderByUpdatedAtDesc(user.id());
        return found.stream()
                .flatMap(c -> {
                    List<InformationRequest> asked = informationRequests.findByClaimIdOrderByRequestedAt(c.getId());
                    if (asked.isEmpty()) {
                        return java.util.stream.Stream.empty();
                    }
                    InformationRequest latest = asked.getLast();
                    boolean waiting = c.getStatus() == ClaimStatus.INFO_REQUESTED && latest.status == InformationRequest.Status.OPEN;
                    boolean toReview = user.isStaff()
                            && c.getStatus() == ClaimStatus.UNDER_REVIEW
                            && latest.status == InformationRequest.Status.ANSWERED;
                    return waiting || toReview
                            ? java.util.stream.Stream.of(new FollowUp(
                                    c.getId(), c.getClaimNumber(), latest.id, latest.question, name(latest.requestedBy),
                                    latest.status, latest.requestedAt, latest.responseText, latest.answeredAt))
                            : java.util.stream.Stream.empty();
                })
                .toList();
    }

    /** One of the holder's own policies with every claim made on it, newest first. */
    public PolicyDetail policy(CurrentUser holder, UUID policyId) {
        Policy p = policies.findById(policyId)
                .filter(found -> found.getHolderId().equals(holder.id()))
                .orElseThrow(() -> ApiException.notFound("Policy"));
        return new PolicyDetail(
                p.getId(), p.getPolicyNumber(), p.getLineOfBusiness(), p.getMarketCode(), p.getCurrency(),
                p.getSumInsured(), p.getEffectiveFrom(), p.getEffectiveTo(),
                list(claims.findByPolicyIdOrderBySubmittedAtDesc(p.getId())));
    }

    public List<ListItem> submittedBy(CurrentUser claimant) {
        return list(claims.findByClaimantIdOrderByUpdatedAtDesc(claimant.id()));
    }

    public List<ListItem> assignedTo(CurrentUser staff) {
        return list(claims.findByAssignedOfficerIdOrderByUpdatedAtDesc(staff.id()));
    }

    public List<ListItem> unassignedIn(String market) {
        return list(claims.findByMarketCodeAndStatusAndAssignedOfficerIdIsNullOrderBySubmittedAt(market, ClaimStatus.SUBMITTED));
    }

    private List<ListItem> list(List<Claim> found) {
        // ponytail: one name lookup per row; fetch names in one query if lists grow past a page
        return found.stream()
                .map(c -> new ListItem(
                        c.getId(), c.getClaimNumber(), c.getLineOfBusiness(), c.getMarketCode(), c.getCurrency(),
                        c.getStatus(), c.getClaimedAmount(), c.getIncidentDate(), name(c.getClaimantId()),
                        name(c.getAssignedOfficerId()), c.getSubmittedAt(), c.getUpdatedAt()))
                .toList();
    }

    public Detail detail(CurrentUser user, UUID claimId) {
        Claim c = access.visibleTo(user, claimId);
        Policy policy = policies.findById(c.getPolicyId()).orElseThrow();
        boolean internal = user.isStaff();
        return new Detail(
                c.getId(), c.getClaimNumber(), policy.getId(), policy.getPolicyNumber(), policy.getSumInsured(),
                c.getLineOfBusiness(), c.getMarketCode(), c.getCurrency(), c.getStatus(),
                c.getIncidentDate(), c.getIncidentDescription(), c.getClaimedAmount(),
                c.getClaimantId(), name(c.getClaimantId()), c.getAssignedOfficerId(), name(c.getAssignedOfficerId()),
                c.getVersion(), c.getSubmittedAt(), c.getUpdatedAt(),
                motorDetails.findById(claimId)
                        .map(m -> new MotorView(m.vehicleRegistration, m.driverName, m.accidentLocation, m.thirdPartyInvolved))
                        .orElse(null),
                propertyDetails.findById(claimId).map(p -> new PropertyView(p.propertyAddress, p.lossCause)).orElse(null),
                history.findByClaimIdOrderByChangedAt(claimId).stream()
                        .map(h -> new HistoryEntry(h.fromStatus, h.toStatus, name(h.actorId), h.reason, h.changedAt))
                        .toList(),
                internal ? c.getCurrentReserve() : null,
                internal
                        ? assessments.findByClaimId(claimId)
                                .map(a -> new AssessmentView(a.findings, a.recommendedAmount, name(a.assessedBy), a.assessedAt))
                                .orElse(null)
                        : null,
                decisions.findByClaimId(claimId)
                        .map(d -> new DecisionView(d.outcome, d.approvedAmount, d.reason, name(d.decidedBy), d.decidedAt))
                        .orElse(null),
                informationRequests.findByClaimIdOrderByRequestedAt(claimId).stream()
                        .map(r -> new InformationRequestView(
                                r.id, r.question, r.status, name(r.requestedBy), r.requestedAt, r.responseText, r.answeredAt))
                        .toList(),
                documents.findByClaimIdOrderByUploadedAt(claimId).stream().map(this::document).toList(),
                payments.findByClaimId(claimId)
                        .map(p -> new PaymentView(
                                p.payeeName, p.method, p.amount, p.currency, p.status,
                                internal ? p.reference : null, p.requestedAt, p.paidAt))
                        .orElse(null));
    }

    DocumentView document(ClaimDocument d) {
        return new DocumentView(d.id, d.fileName, d.contentType, d.sizeBytes, d.informationRequestId, name(d.uploadedBy), d.uploadedAt);
    }

    private String name(UUID userId) {
        return userId == null ? null : users.findById(userId).map(AppUser::getFullName).orElse(null);
    }
}
