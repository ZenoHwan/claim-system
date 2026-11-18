package com.claims.service.claim;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "decision")
class Decision {

    enum Outcome {
        APPROVED,
        REJECTED
    }

    @Id
    UUID id = UUID.randomUUID();

    UUID claimId;

    @Enumerated(EnumType.STRING)
    Outcome outcome;

    BigDecimal approvedAmount;
    String reason;
    UUID decidedBy;
    Instant decidedAt;

    protected Decision() {}

    Decision(UUID claimId, Outcome outcome, BigDecimal approvedAmount, String reason, UUID decidedBy, Instant decidedAt) {
        this.claimId = claimId;
        this.outcome = outcome;
        this.approvedAmount = approvedAmount;
        this.reason = reason;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }
}
