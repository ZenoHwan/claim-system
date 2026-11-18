package com.claims.service.identity;

public enum Role {
    CLAIMANT,
    OFFICER,
    MANAGER;

    /** A manager can do everything an officer can. */
    public boolean isStaff() {
        return this != CLAIMANT;
    }
}
