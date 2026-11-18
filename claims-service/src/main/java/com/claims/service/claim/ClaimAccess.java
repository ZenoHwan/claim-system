package com.claims.service.claim;

import com.claims.service.common.ApiException;
import com.claims.service.identity.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Loads a claim only if the caller is allowed to see it. Every use case starts here. */
@Component
public class ClaimAccess {

    private final ClaimRepository claims;

    ClaimAccess(ClaimRepository claims) {
        this.claims = claims;
    }

    /**
     * A claimant sees their own claims; staff see claims in their market. Anything else is
     * reported as "not found" so that claim ids cannot be probed.
     */
    public Claim visibleTo(CurrentUser user, UUID claimId) {
        return claims.findById(claimId)
                .filter(claim -> user.isStaff()
                        ? claim.getMarketCode().equals(user.market())
                        : claim.getClaimantId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Claim"));
    }

    /** For actions only the staff member handling the claim may take. */
    public Claim assignedTo(CurrentUser user, UUID claimId) {
        Claim claim = visibleTo(user, claimId);
        if (!user.id().equals(claim.getAssignedOfficerId())) {
            throw ApiException.forbidden("Only the staff member assigned to this claim can do that");
        }
        return claim;
    }
}
