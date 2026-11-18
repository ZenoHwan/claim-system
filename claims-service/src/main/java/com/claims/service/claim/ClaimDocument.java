package com.claims.service.claim;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Metadata only. The bytes live in the document store under {@link #storageKey}. */
@Entity
@Table(name = "claim_document")
class ClaimDocument {

    @Id
    UUID id;

    UUID claimId;
    UUID informationRequestId;
    UUID uploadedBy;
    String fileName;
    String contentType;
    long sizeBytes;
    String storageKey;
    Instant uploadedAt;

    protected ClaimDocument() {}

    ClaimDocument(
            UUID id, UUID claimId, UUID informationRequestId, UUID uploadedBy, String fileName,
            String contentType, long sizeBytes, String storageKey, Instant uploadedAt) {
        this.id = id;
        this.claimId = claimId;
        this.informationRequestId = informationRequestId;
        this.uploadedBy = uploadedBy;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.uploadedAt = uploadedAt;
    }
}
