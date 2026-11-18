package com.claims.service.claim;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    List<Claim> findByClaimantIdOrderByUpdatedAtDesc(UUID claimantId);

    List<Claim> findByAssignedOfficerIdOrderByUpdatedAtDesc(UUID officerId);

    List<Claim> findByPolicyIdOrderBySubmittedAtDesc(UUID policyId);

    /** The pick-up queue: oldest first, so nothing is left to age at the bottom. */
    List<Claim> findByMarketCodeAndStatusAndAssignedOfficerIdIsNullOrderBySubmittedAt(String marketCode, ClaimStatus status);

    /** The claim, if any, that currently blocks a new one on this policy. */
    Optional<Claim> findFirstByPolicyIdAndStatusNotIn(UUID policyId, Collection<ClaimStatus> closed);
}
