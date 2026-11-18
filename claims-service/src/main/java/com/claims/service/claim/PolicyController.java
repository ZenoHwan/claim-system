package com.claims.service.claim;

import com.claims.service.identity.CurrentUser;
import com.claims.service.policy.LineOfBusiness;
import com.claims.service.policy.PolicyRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The policies a claimant can report an incident against. Lives with claims because it answers "can I claim on this?". */
@RestController
@RequestMapping("/api/policies")
class PolicyController {

    /** {@code openClaimId}/{@code openClaimNumber} are present when the policy already has a claim in progress. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PolicyView(
            UUID id,
            String policyNumber,
            LineOfBusiness lineOfBusiness,
            String marketCode,
            String currency,
            BigDecimal sumInsured,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            UUID openClaimId,
            String openClaimNumber) {}

    private final PolicyRepository policies;
    private final ClaimRepository claims;

    PolicyController(PolicyRepository policies, ClaimRepository claims) {
        this.policies = policies;
        this.claims = claims;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<PolicyView> myPolicies(@AuthenticationPrincipal Jwt jwt) {
        return policies.findByHolderIdOrderByPolicyNumber(CurrentUser.from(jwt).id()).stream()
                .map(p -> {
                    Claim open = claims.findFirstByPolicyIdAndStatusNotIn(p.getId(), ClaimStatus.CLOSED).orElse(null);
                    return new PolicyView(
                            p.getId(), p.getPolicyNumber(), p.getLineOfBusiness(), p.getMarketCode(),
                            p.getCurrency(), p.getSumInsured(), p.getEffectiveFrom(), p.getEffectiveTo(),
                            open == null ? null : open.getId(), open == null ? null : open.getClaimNumber());
                })
                .toList();
    }
}
