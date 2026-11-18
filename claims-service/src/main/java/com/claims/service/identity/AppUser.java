package com.claims.service.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Read-only here: accounts are seeded, there is no registration flow. */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    private String email;
    private String fullName;
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String marketCode;
    private UUID teamId;

    protected AppUser() {}

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public String getMarketCode() {
        return marketCode;
    }

    public UUID getTeamId() {
        return teamId;
    }
}
