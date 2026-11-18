package com.claims.service.claim;

import java.util.Set;

/** The claim state machine. Every permitted transition is listed here and nowhere else. */
public enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    INFO_REQUESTED,
    ASSESSED,
    APPROVED,
    REJECTED,
    SETTLED;

    private Set<ClaimStatus> next() {
        return switch (this) {
            case SUBMITTED -> Set.of(UNDER_REVIEW);
            case UNDER_REVIEW -> Set.of(INFO_REQUESTED, ASSESSED);
            case INFO_REQUESTED -> Set.of(UNDER_REVIEW);
            case ASSESSED -> Set.of(APPROVED, REJECTED);
            case APPROVED -> Set.of(SETTLED);
            case REJECTED, SETTLED -> Set.of();
        };
    }

    public boolean canMoveTo(ClaimStatus target) {
        return next().contains(target);
    }

    public static final Set<ClaimStatus> CLOSED = Set.of(REJECTED, SETTLED);

    /** Open claims count toward workload and exposure, and block a new claim on the same policy. */
    public boolean isOpen() {
        return !CLOSED.contains(this);
    }
}
