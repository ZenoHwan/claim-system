package com.claims.service.claim;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "property_claim_detail")
class PropertyClaimDetail {

    enum LossCause {
        FIRE,
        WATER,
        THEFT,
        STORM,
        OTHER
    }

    @Id
    UUID claimId;

    String propertyAddress;

    @Enumerated(EnumType.STRING)
    LossCause lossCause;

    protected PropertyClaimDetail() {}

    PropertyClaimDetail(UUID claimId, SubmitClaimRequest.Property property) {
        this.claimId = claimId;
        this.propertyAddress = property.propertyAddress();
        this.lossCause = property.lossCause();
    }
}
