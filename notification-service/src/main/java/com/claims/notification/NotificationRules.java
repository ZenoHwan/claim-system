package com.claims.notification;

import com.claims.events.ClaimEvent;
import com.claims.events.ClaimSnapshot;
import java.util.Optional;
import java.util.UUID;

/** Decides who, if anyone, is told about a claim event, and in what words. */
final class NotificationRules {

    record Draft(UUID recipientId, String message) {}

    private NotificationRules() {}

    static Optional<Draft> forEvent(ClaimEvent event) {
        ClaimSnapshot claim = event.claim();
        String number = claim.claimNumber();
        return switch (event.type()) {
            case CLAIM_ASSIGNED -> toClaimant(claim, "Your claim " + number + " has been picked up and is under review.");
            case INFORMATION_REQUESTED -> toClaimant(claim, "More information is needed for your claim " + number + ".");
            case CLAIM_APPROVED -> toClaimant(claim, "Your claim " + number + " has been approved.");
            case CLAIM_REJECTED -> toClaimant(claim, "Your claim " + number + " has been rejected.");
            case CLAIM_SETTLED -> toClaimant(claim, "Payment for your claim " + number + " has been made.");
            case INFORMATION_PROVIDED -> Optional.ofNullable(claim.officerId())
                    .map(officer -> new Draft(officer, "The claimant has responded on claim " + number + "."));
            case CLAIM_SUBMITTED, CLAIM_ASSESSED -> Optional.empty();
        };
    }

    private static Optional<Draft> toClaimant(ClaimSnapshot claim, String message) {
        return Optional.of(new Draft(claim.claimantId(), message));
    }
}
