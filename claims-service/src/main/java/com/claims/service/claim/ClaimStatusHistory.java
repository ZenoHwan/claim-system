package com.claims.service.claim;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_status_history")
class ClaimStatusHistory {

    @Id
    UUID id = UUID.randomUUID();

    UUID claimId;

    @Enumerated(EnumType.STRING)
    ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    ClaimStatus toStatus;

    UUID actorId;
    String reason;
    Instant changedAt;

    protected ClaimStatusHistory() {}

    ClaimStatusHistory(UUID claimId, ClaimStatus fromStatus, ClaimStatus toStatus, UUID actorId, String reason, Instant changedAt) {
        this.claimId = claimId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.reason = reason;
        this.changedAt = changedAt;
    }
}
