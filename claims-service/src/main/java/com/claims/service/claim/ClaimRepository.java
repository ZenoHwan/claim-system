package com.claims.service.claim;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    List<Claim> findByClaimantIdOrderByUpdatedAtDesc(UUID claimantId);

    List<Claim> findByAssignedOfficerIdOrderByUpdatedAtDesc(UUID officerId);

    /** The pick-up queue: oldest first, so nothing is left to age at the bottom. */
    List<Claim> findByMarketCodeAndStatusAndAssignedOfficerIdIsNullOrderBySubmittedAt(String marketCode, ClaimStatus status);
}
