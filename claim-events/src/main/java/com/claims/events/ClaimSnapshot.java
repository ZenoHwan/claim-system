package com.claims.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The claim as it stood right after the change. Carries everything a consumer needs so that
 * no consumer has to call back into claims-service.
 *
 * @param officerId null until the claim is picked up
 * @param currentReserve zero once the claim is rejected or settled
 */
public record ClaimSnapshot(
        UUID claimId,
        String claimNumber,
        String marketCode,
        String lineOfBusiness,
        String status,
        UUID claimantId,
        UUID officerId,
        String officerName,
        UUID teamId,
        BigDecimal currentReserve,
        String currency,
        Instant submittedAt,
        Instant decidedAt,
        Instant settledAt) {}
