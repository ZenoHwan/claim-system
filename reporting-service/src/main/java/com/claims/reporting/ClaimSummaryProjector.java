package com.claims.reporting;

import com.claims.events.ClaimEvent;
import com.claims.events.ClaimSnapshot;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Folds claim events into claim_summary. Safe under duplicate, out-of-order and replayed delivery. */
@Component
class ClaimSummaryProjector {

    private static final String UPSERT = """
            INSERT INTO claim_summary (claim_id, claim_number, market_code, line_of_business, status, officer_id,
                officer_name, team_id, current_reserve, currency, claim_version, submitted_at, decided_at, settled_at,
                last_event_at)
            VALUES (:claimId, :claimNumber, :market, :line, :status, :officerId, :officerName, :teamId, :reserve,
                :currency, :version, :submittedAt, :decidedAt, :settledAt, :occurredAt)
            ON CONFLICT (claim_id) DO UPDATE SET
                status = EXCLUDED.status, officer_id = EXCLUDED.officer_id, officer_name = EXCLUDED.officer_name,
                team_id = EXCLUDED.team_id, current_reserve = EXCLUDED.current_reserve,
                claim_version = EXCLUDED.claim_version, decided_at = EXCLUDED.decided_at,
                settled_at = EXCLUDED.settled_at, last_event_at = EXCLUDED.last_event_at
            WHERE claim_summary.claim_version < EXCLUDED.claim_version
            """;

    private final JdbcClient jdbc;

    ClaimSummaryProjector(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The event-id insert and the upsert share one transaction: either both happen or neither does. */
    @Transactional
    public void apply(ClaimEvent event) {
        boolean firstDelivery = jdbc.sql("INSERT INTO processed_event (event_id) VALUES (?) ON CONFLICT DO NOTHING")
                .param(event.eventId())
                .update() == 1;
        if (!firstDelivery) {
            return;
        }
        ClaimSnapshot claim = event.claim();
        jdbc.sql(UPSERT)
                .param("claimId", claim.claimId())
                .param("claimNumber", claim.claimNumber())
                .param("market", claim.marketCode())
                .param("line", claim.lineOfBusiness())
                .param("status", claim.status())
                .param("officerId", claim.officerId())
                .param("officerName", claim.officerName())
                .param("teamId", claim.teamId())
                .param("reserve", claim.currentReserve())
                .param("currency", claim.currency())
                .param("version", event.claimVersion())
                .param("submittedAt", timestamp(claim.submittedAt()))
                .param("decidedAt", timestamp(claim.decidedAt()))
                .param("settledAt", timestamp(claim.settledAt()))
                .param("occurredAt", timestamp(event.occurredAt()))
                .update();
    }

    // The PostgreSQL driver does not accept java.time.Instant as a parameter.
    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
