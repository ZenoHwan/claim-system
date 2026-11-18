package com.claims.events;

/** One type per claim state change. Adding a type is a contract change for every consumer. */
public enum ClaimEventType {
    CLAIM_SUBMITTED,
    CLAIM_ASSIGNED,
    INFORMATION_REQUESTED,
    INFORMATION_PROVIDED,
    CLAIM_ASSESSED,
    CLAIM_APPROVED,
    CLAIM_REJECTED,
    CLAIM_SETTLED
}
