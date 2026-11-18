package com.claims.service.claim;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The money owed on an approved claim. One per claim (unique constraint on claim_id).
 * Holds a payee name only: no bank details are stored anywhere.
 */
// ponytail: REQUESTED -> PAID by hand. PROCESSING/FAILED arrive with a payment service and a bank (docs/state-machine.md).
@Entity
@Table(name = "payment")
class Payment {

    enum Method {
        BANK_TRANSFER,
        CHEQUE
    }

    enum Status {
        REQUESTED,
        PAID
    }

    @Id
    UUID id = UUID.randomUUID();

    UUID claimId;
    String payeeName;

    @Enumerated(EnumType.STRING)
    Method method;

    BigDecimal amount;
    String currency;

    @Enumerated(EnumType.STRING)
    Status status = Status.REQUESTED;

    String reference;
    Instant requestedAt;
    Instant paidAt;

    protected Payment() {}

    Payment(UUID claimId, String payeeName, BigDecimal amount, String currency, Instant requestedAt) {
        this.claimId = claimId;
        this.payeeName = payeeName;
        this.amount = amount;
        this.currency = currency;
        this.requestedAt = requestedAt;
    }

    void markPaid(Method method, String reference, Instant now) {
        this.method = method;
        this.reference = reference;
        this.status = Status.PAID;
        this.paidAt = now;
    }
}
