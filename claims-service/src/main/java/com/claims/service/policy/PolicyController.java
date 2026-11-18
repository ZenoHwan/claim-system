package com.claims.service.policy;

import com.claims.service.identity.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/policies")
class PolicyController {

    record PolicyView(
            UUID id,
            String policyNumber,
            LineOfBusiness lineOfBusiness,
            String marketCode,
            String currency,
            BigDecimal sumInsured,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {}

    private final PolicyRepository policies;

    PolicyController(PolicyRepository policies) {
        this.policies = policies;
    }

    /** The signed-in claimant's own policies, to choose from when reporting an incident. */
    @GetMapping
    List<PolicyView> myPolicies(@AuthenticationPrincipal Jwt jwt) {
        return policies.findByHolderIdOrderByPolicyNumber(CurrentUser.from(jwt).id()).stream()
                .map(p -> new PolicyView(
                        p.getId(), p.getPolicyNumber(), p.getLineOfBusiness(), p.getMarketCode(),
                        p.getCurrency(), p.getSumInsured(), p.getEffectiveFrom(), p.getEffectiveTo()))
                .toList();
    }
}
