package com.claims.service.claim;

import com.claims.service.common.ApiException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "information_request")
class InformationRequest {

    enum Status {
        OPEN,
        ANSWERED
    }

    @Id
    UUID id = UUID.randomUUID();

    UUID claimId;
    UUID requestedBy;
    String question;

    @Enumerated(EnumType.STRING)
    Status status = Status.OPEN;

    String responseText;
    Instant requestedAt;
    Instant answeredAt;

    protected InformationRequest() {}

    InformationRequest(UUID claimId, UUID requestedBy, String question, Instant requestedAt) {
        this.claimId = claimId;
        this.requestedBy = requestedBy;
        this.question = question;
        this.requestedAt = requestedAt;
    }

    void answer(String text, Instant now) {
        if (status != Status.OPEN) {
            throw ApiException.conflict("This information request has already been answered");
        }
        status = Status.ANSWERED;
        responseText = text;
        answeredAt = now;
    }
}
