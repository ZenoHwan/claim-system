package com.claims.service.claim;

import com.claims.service.common.ApiException;
import com.claims.service.identity.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claims")
class ClaimController {

    private final ClaimService service;
    private final InformationRequestService informationRequests;
    private final ClaimViews views;

    ClaimController(ClaimService service, InformationRequestService informationRequests, ClaimViews views) {
        this.service = service;
        this.informationRequests = informationRequests;
        this.views = views;
    }

    /** Report an incident. Claimants only (enforced in SecurityConfig). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ClaimViews.Detail submit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SubmitClaimRequest request) {
        CurrentUser claimant = CurrentUser.from(jwt);
        return views.detail(claimant, service.submit(claimant, request));
    }

    /** The signed-in claimant's own claims. Staff use /api/staff/claims instead. */
    @GetMapping
    List<ClaimViews.ListItem> myClaims(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        if (user.isStaff()) {
            throw ApiException.forbidden("Staff list claims through /api/staff/claims");
        }
        return views.submittedBy(user);
    }

    /** The claimant's questions from staff that are still waiting for a reply. */
    @GetMapping("/follow-ups")
    List<ClaimViews.FollowUp> followUps(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        if (user.isStaff()) {
            throw ApiException.forbidden("Staff see follow-ups through /api/staff/claims/follow-ups");
        }
        return views.followUps(user);
    }

    /** One claim, shaped for the caller: a claimant sees their own, staff see their market's. */
    @GetMapping("/{claimId}")
    ClaimViews.Detail claim(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId) {
        return views.detail(CurrentUser.from(jwt), claimId);
    }

    /** The claimant answers an open information request; the claim goes back to its officer. */
    @PostMapping("/{claimId}/information-requests/{requestId}/response")
    ClaimViews.Detail respond(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId, @PathVariable UUID requestId,
            @Valid @RequestBody InformationRequestService.AnswerRequest request) {
        CurrentUser claimant = CurrentUser.from(jwt);
        informationRequests.answer(claimant, claimId, requestId, request);
        return views.detail(claimant, claimId);
    }
}
