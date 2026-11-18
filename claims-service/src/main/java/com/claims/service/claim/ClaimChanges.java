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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one place a claim state change is persisted. It writes the claim, its history row and its
 * outbox event together, inside the caller's transaction, so none can exist without the others.
 */
@Component
public class ClaimChanges {

    private final ClaimRepository claims;
    private final ClaimRecords.History history;
    private final OutboxRepository outbox;
    private final AppUserRepository users;
    private final JsonMapper json;

    ClaimChanges(ClaimRepository claims, ClaimRecords.History history, OutboxRepository outbox, AppUserRepository users, JsonMapper json) {
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
