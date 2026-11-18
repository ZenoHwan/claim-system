package com.claims.service.claim;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Append-only history of what the insurer expects a claim to cost. The newest row is current. */
@Entity
@Table(name = "reserve")
class Reserve {

    enum Reason {
        INITIAL,
        ASSESSMENT,
        RELEASED
    }

    @Id
    UUID id = UUID.randomUUID();

    UUID claimId;
    BigDecimal amount;

    @Enumerated(EnumType.STRING)
    Reason reason;

    UUID setBy;
    Instant createdAt;

    protected Reserve() {}

    Reserve(UUID claimId, BigDecimal amount, Reason reason, UUID setBy, Instant createdAt) {
        this.claimId = claimId;
        this.amount = amount;
        this.reason = reason;
        this.setBy = setBy;
        this.createdAt = createdAt;
    }
}
