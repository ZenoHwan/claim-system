package com.claims.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope for everything published on the claim-events topic.
 *
 * @param eventId unique per event; consumers use it to drop duplicates
 * @param claimVersion the claim's version after the change; consumers ignore older versions
 */
public record ClaimEvent(UUID eventId, ClaimEventType type, Instant occurredAt, long claimVersion, ClaimSnapshot claim) {

    public static final String TOPIC = "claim-events";
}
