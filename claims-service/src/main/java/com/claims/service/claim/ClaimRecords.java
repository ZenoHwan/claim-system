package com.claims.service.claim;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositories for the rows that hang off a claim. Grouped because each is a one-liner. */
final class ClaimRecords {

    private ClaimRecords() {}

    interface MotorDetails extends JpaRepository<MotorClaimDetail, UUID> {}

    interface PropertyDetails extends JpaRepository<PropertyClaimDetail, UUID> {}

    interface History extends JpaRepository<ClaimStatusHistory, UUID> {
        List<ClaimStatusHistory> findByClaimIdOrderByChangedAt(UUID claimId);
    }

    interface Reserves extends JpaRepository<Reserve, UUID> {}

    interface Assessments extends JpaRepository<Assessment, UUID> {
        Optional<Assessment> findByClaimId(UUID claimId);
    }

    interface Decisions extends JpaRepository<Decision, UUID> {
        Optional<Decision> findByClaimId(UUID claimId);
    }
}
