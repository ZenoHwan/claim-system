package com.claims.reporting;

import com.claims.events.ClaimEvent;
import com.claims.events.ClaimEventType;
import com.claims.events.ClaimSnapshot;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Builds the event stream of one claim the way claims-service would publish it. */
final class TestClaim {

    static final UUID TAN = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID KUMAR = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    final UUID claimId = UUID.randomUUID();
    private final String market;
    private final String line;
    private final String currency;
    private final Instant submittedAt;
    private long version = -1;
    private UUID officerId;
    private String officerName;
    private BigDecimal reserve;
    private Instant decidedAt;
    private Instant settledAt;

    private TestClaim(String market, String line, String currency, Instant submittedAt) {
        this.market = market;
        this.line = line;
        this.currency = currency;
        this.submittedAt = submittedAt;
    }

    static TestClaim motorMy(int submittedDaysAgo) {
        return new TestClaim("MY", "MOTOR", "MYR", Instant.now().minus(Duration.ofDays(submittedDaysAgo)).minusSeconds(60));
    }

    static TestClaim of(String market, String line, String currency) {
        return new TestClaim(market, line, currency, Instant.now().minusSeconds(60));
    }

    ClaimEvent submitted(String claimedAmount) {
        reserve = new BigDecimal(claimedAmount);
        return event(ClaimEventType.CLAIM_SUBMITTED, "SUBMITTED");
    }

    ClaimEvent assigned(UUID officer, String name) {
        officerId = officer;
        officerName = name;
        return event(ClaimEventType.CLAIM_ASSIGNED, "UNDER_REVIEW");
    }

    ClaimEvent informationRequested() {
        return event(ClaimEventType.INFORMATION_REQUESTED, "INFO_REQUESTED");
    }

    ClaimEvent assessed(String recommendedAmount) {
        reserve = new BigDecimal(recommendedAmount);
        return event(ClaimEventType.CLAIM_ASSESSED, "ASSESSED");
    }

    ClaimEvent approved(int daysAfterSubmission) {
        decidedAt = submittedAt.plus(Duration.ofDays(daysAfterSubmission));
        return event(ClaimEventType.CLAIM_APPROVED, "APPROVED");
    }

    ClaimEvent rejected(int daysAfterSubmission) {
        decidedAt = submittedAt.plus(Duration.ofDays(daysAfterSubmission));
        reserve = BigDecimal.ZERO;
        return event(ClaimEventType.CLAIM_REJECTED, "REJECTED");
    }

    ClaimEvent settled() {
        settledAt = Instant.now();
        reserve = BigDecimal.ZERO;
        return event(ClaimEventType.CLAIM_SETTLED, "SETTLED");
    }

    private ClaimEvent event(ClaimEventType type, String status) {
        version++;
        var snapshot = new ClaimSnapshot(claimId, "CLM-" + claimId.toString().substring(0, 8), market, line, status,
                UUID.randomUUID(), officerId, officerName, null, reserve, currency, submittedAt, decidedAt, settledAt);
        return new ClaimEvent(UUID.randomUUID(), type, Instant.now(), version, snapshot);
    }
}
