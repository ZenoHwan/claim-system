package com.claims.service.identity;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** The caller, as stated by a verified token. Never built from request parameters. */
public record CurrentUser(UUID id, Role role, String market, UUID teamId, String name) {

    public static CurrentUser from(Jwt jwt) {
        String team = jwt.getClaimAsString("team");
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                Role.valueOf(jwt.getClaimAsString("role")),
                jwt.getClaimAsString("market"),
                team == null ? null : UUID.fromString(team),
                jwt.getClaimAsString("name"));
    }

    public boolean isStaff() {
        return role.isStaff();
    }
}
