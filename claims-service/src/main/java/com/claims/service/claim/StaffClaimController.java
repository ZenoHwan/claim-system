package com.claims.service.claim;

import com.claims.service.identity.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Claim handling for officers and managers (role enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/staff/claims")
class StaffClaimController {

    private final ClaimService service;
    private final InformationRequestService informationRequests;
    private final SettlementService settlement;
    private final ClaimViews views;

    StaffClaimController(
            ClaimService service, InformationRequestService informationRequests, SettlementService settlement, ClaimViews views) {
        this.service = service;
        this.informationRequests = informationRequests;
        this.settlement = settlement;
        this.views = views;
    }

    @GetMapping("/unassigned")
    List<ClaimViews.ListItem> unassigned(@AuthenticationPrincipal Jwt jwt) {
        return views.unassignedIn(CurrentUser.from(jwt).market());
    }

    @GetMapping("/mine")
    List<ClaimViews.ListItem> mine(@AuthenticationPrincipal Jwt jwt) {
        return views.assignedTo(CurrentUser.from(jwt));
    }

    @PostMapping("/{claimId}/assign")
    ClaimViews.Detail pickUp(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId) {
        CurrentUser staff = CurrentUser.from(jwt);
        service.pickUp(staff, claimId);
        return views.detail(staff, claimId);
    }

    @PostMapping("/{claimId}/assessment")
    ClaimViews.Detail assess(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
            @Valid @RequestBody ClaimService.AssessmentRequest request) {
        CurrentUser staff = CurrentUser.from(jwt);
        service.assess(staff, claimId, request);
        return views.detail(staff, claimId);
    }

    @PostMapping("/{claimId}/decision")
    ClaimViews.Detail decide(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
            @Valid @RequestBody ClaimService.DecisionRequest request) {
        CurrentUser staff = CurrentUser.from(jwt);
        service.decide(staff, claimId, request);
        return views.detail(staff, claimId);
    }

    @PostMapping("/{claimId}/information-requests")
    ClaimViews.Detail requestInformation(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
            @Valid @RequestBody InformationRequestService.AskRequest request) {
        CurrentUser staff = CurrentUser.from(jwt);
        informationRequests.ask(staff, claimId, request);
        return views.detail(staff, claimId);
    }

    /** Records that the approved amount has been paid, which settles the claim. */
    @PostMapping("/{claimId}/payment")
    ClaimViews.Detail recordPayment(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
            @Valid @RequestBody SettlementService.RecordPaymentRequest request) {
        CurrentUser staff = CurrentUser.from(jwt);
        settlement.recordPayment(staff, claimId, request);
        return views.detail(staff, claimId);
    }
}
