package com.claims.service.claim;

import com.claims.events.ClaimEventType;
import com.claims.service.identity.AppUserRepository;
import com.claims.service.identity.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Raises the payment when a claim is approved and settles the claim once the payment is recorded. */
@Service
@Transactional
class SettlementService {

    record RecordPaymentRequest(@NotNull Payment.Method method, @NotBlank @Size(max = 100) String reference) {}

    private final ClaimAccess access;
    private final ClaimChanges changes;
    private final ClaimRecords.Payments payments;
    private final ClaimRecords.Reserves reserves;
    private final AppUserRepository users;

    SettlementService(
            ClaimAccess access, ClaimChanges changes, ClaimRecords.Payments payments,
            ClaimRecords.Reserves reserves, AppUserRepository users) {
        this.access = access;
        this.changes = changes;
        this.payments = payments;
        this.reserves = reserves;
        this.users = users;
    }

    /** Called inside the approval transaction, so an approved claim always has its payment. */
    void raisePayment(Claim claim, BigDecimal approvedAmount, Instant now) {
        String payee = users.findById(claim.getClaimantId()).orElseThrow().getFullName();
        payments.save(new Payment(claim.getId(), payee, approvedAmount, claim.getCurrency(), now));
    }

    void recordPayment(CurrentUser staff, UUID claimId, RecordPaymentRequest request) {
        Claim claim = access.assignedTo(staff, claimId);
        ClaimStatus from = claim.getStatus();
        Instant now = Instant.now();
        // Refuses anything but APPROVED, which also makes recording twice a conflict.
        claim.settle(now);
        changes.record(claim, from, staff.id(), null, ClaimEventType.CLAIM_SETTLED);
        payments.findByClaimId(claimId).orElseThrow().markPaid(request.method(), request.reference(), now);
        reserves.save(new Reserve(claimId, BigDecimal.ZERO, Reserve.Reason.RELEASED, staff.id(), now));
    }
}
