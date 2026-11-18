package com.claims.service.claim;

import static com.claims.service.claim.ClaimStatus.APPROVED;
import static com.claims.service.claim.ClaimStatus.ASSESSED;
import static com.claims.service.claim.ClaimStatus.INFO_REQUESTED;
import static com.claims.service.claim.ClaimStatus.REJECTED;
import static com.claims.service.claim.ClaimStatus.SETTLED;
import static com.claims.service.claim.ClaimStatus.SUBMITTED;
import static com.claims.service.claim.ClaimStatus.UNDER_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClaimStatusTest {

    /** The whole state machine, stated once. Anything not listed here must be refused. */
    private static final Map<ClaimStatus, Set<ClaimStatus>> PERMITTED = Map.of(
            SUBMITTED, Set.of(UNDER_REVIEW),
            UNDER_REVIEW, Set.of(INFO_REQUESTED, ASSESSED),
            INFO_REQUESTED, Set.of(UNDER_REVIEW),
            ASSESSED, Set.of(APPROVED, REJECTED),
            APPROVED, Set.of(SETTLED),
            REJECTED, Set.of(),
            SETTLED, Set.of());

    @Test
    void everyPairOfStatusesIsEitherPermittedOrRefused() {
        for (ClaimStatus from : ClaimStatus.values()) {
            for (ClaimStatus to : ClaimStatus.values()) {
                assertThat(from.canMoveTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(PERMITTED.get(from).contains(to));
            }
        }
    }

    @Test
    void onlySettledAndRejectedAreClosed() {
        assertThat(ClaimStatus.values()).filteredOn(status -> !status.isOpen()).containsExactlyInAnyOrder(SETTLED, REJECTED);
    }
}
