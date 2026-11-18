package com.claims.service.claim;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assessment")
class Assessment {

    @Id
    UUID id = UUID.randomUUID();

    UUID claimId;
    UUID assessedBy;
    String findings;
    BigDecimal recommendedAmount;
    Instant assessedAt;

    protected Assessment() {}

    Assessment(UUID claimId, UUID assessedBy, String findings, BigDecimal recommendedAmount, Instant assessedAt) {
        this.claimId = claimId;
        this.assessedBy = assessedBy;
        this.findings = findings;
        this.recommendedAmount = recommendedAmount;
        this.assessedAt = assessedAt;
    }
}
