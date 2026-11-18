package com.claims.service.claim;

import com.claims.events.ClaimEvent;
import com.claims.events.ClaimEventType;
import com.claims.events.ClaimSnapshot;
import com.claims.service.identity.AppUser;
import com.claims.service.identity.AppUserRepository;
import com.claims.service.outbox.OutboxEvent;
import com.claims.service.outbox.OutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one place a claim state change is persisted. It writes the claim, its history row and its
 * outbox event together, inside the caller's transaction, so none can exist without the others.
 * It is also where the cached lists that show this claim are cleared: the claimant's and officer's
 * Follow-ups and claim lists, and the market's unassigned queue.
 */
@Component
public class ClaimChanges {

    private static final Logger log = LoggerFactory.getLogger(ClaimChanges.class);

    private final ClaimRepository claims;
    private final ClaimRecords.History history;
    private final OutboxRepository outbox;
    private final AppUserRepository users;
    private final JsonMapper json;
    private final CacheManager caches;

    ClaimChanges(ClaimRepository claims, ClaimRecords.History history, OutboxRepository outbox, AppUserRepository users,
            JsonMapper json, CacheManager caches) {
        this.caches = caches;
        this.claims = claims;
        this.history = history;
        this.outbox = outbox;
        this.users = users;
        this.json = json;
    }

    /**
     * @param from the status before the change, or null for a new claim
     * @param reason free text shown on the timeline, may be null
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Claim claim, ClaimStatus from, UUID actorId, String reason, ClaimEventType eventType) {
        // Flush now: this is where a lost optimistic-lock race surfaces, and it gives us the new version.
        claims.saveAndFlush(claim);
        Instant now = claim.getUpdatedAt();
        history.save(new ClaimStatusHistory(claim.getId(), from, claim.getStatus(), actorId, reason, now));

        ClaimEvent event = new ClaimEvent(UUID.randomUUID(), eventType, now, claim.getVersion(), snapshot(claim));
        outbox.save(new OutboxEvent(event.eventId(), claim.getId(), eventType.name(), json.writeValueAsString(event), now));
        forgetCachedLists(claim);
    }

    /**
     * After commit, so a read cannot cache the old list between the eviction and the commit.
     * ponytail: a read that started before the commit can still put the old list back; the cache TTL bounds that.
     */
    private void forgetCachedLists(Claim claim) {
        UUID claimant = claim.getClaimantId();
        UUID officer = claim.getAssignedOfficerId();
        String market = claim.getMarketCode();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                forget(CacheConfig.FOLLOW_UPS, claimant);
                forget(CacheConfig.FOLLOW_UPS, officer);
                forget(CacheConfig.CLAIMANT_CLAIMS, claimant);
                forget(CacheConfig.OFFICER_CLAIMS, officer);
                forget(CacheConfig.QUEUE, market);
            }
        });
    }

    private void forget(String cache, Object key) {
        if (key == null) {
            return; // an unassigned claim has no officer yet
        }
        try {
            caches.getCache(cache).evict(key);
        } catch (RuntimeException e) {
            // the change is committed; a failed eviction only leaves the old list until the TTL
            log.warn("Could not clear cached {} for {}: {}", cache, key, e.getMessage());
        }
    }

    private ClaimSnapshot snapshot(Claim claim) {
        AppUser officer = claim.getAssignedOfficerId() == null
                ? null
                : users.findById(claim.getAssignedOfficerId()).orElseThrow();
        return new ClaimSnapshot(
                claim.getId(),
                claim.getClaimNumber(),
                claim.getMarketCode(),
                claim.getLineOfBusiness().name(),
                claim.getStatus().name(),
                claim.getClaimantId(),
                claim.getAssignedOfficerId(),
                officer == null ? null : officer.getFullName(),
                officer == null ? null : officer.getTeamId(),
                claim.getCurrentReserve(),
                claim.getCurrency(),
                claim.getSubmittedAt(),
                claim.getDecidedAt(),
                claim.getSettledAt());
    }
}
