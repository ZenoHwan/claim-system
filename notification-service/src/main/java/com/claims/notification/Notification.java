package com.claims.notification;

import java.time.Instant;
import java.util.UUID;

/** One inbox entry. Also the JSON shape returned by the inbox and pushed over the live stream. */
record Notification(
        UUID id,
        UUID recipientId,
        UUID claimId,
        String claimNumber,
        String type,
        String message,
        Instant createdAt,
        boolean read) {}
