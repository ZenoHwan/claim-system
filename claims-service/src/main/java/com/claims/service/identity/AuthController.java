package com.claims.service.identity;

import com.claims.service.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    /** Which sign-in screen the request came from. */
    enum Door {
        CLAIMANT,
        STAFF
    }

    record LoginRequest(@NotBlank String email, @NotBlank String password, @NotNull Door door) {}

    record LoginResponse(String token, UUID userId, String name, Role role, String market) {}

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Duration tokenTtl;

    AuthController(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            @Value("${claims.jwt.ttl}") Duration tokenTtl) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.tokenTtl = tokenTtl;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        // One generic failure for unknown account, wrong password and wrong door,
        // so the response never reveals which accounts exist or what role they have.
        AppUser user = users.findByEmailIgnoreCase(request.email())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .filter(found -> found.getRole().isStaff() == (request.door() == Door.STAFF))
                .orElseThrow(() -> ApiException.unauthorized("Invalid credentials"));

        return new LoginResponse(issueToken(user), user.getId(), user.getFullName(), user.getRole(), user.getMarketCode());
    }

    private String issueToken(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(tokenTtl))
                .claim("role", user.getRole().name())
                .claim("market", user.getMarketCode())
                .claim("name", user.getFullName());
        if (user.getTeamId() != null) {
            claims.claim("team", user.getTeamId().toString());
        }
        return jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }
}
