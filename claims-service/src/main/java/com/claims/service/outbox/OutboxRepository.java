package com.claims.service.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Oldest unpublished events, locked for this transaction. SKIP LOCKED lets a second relay
     * instance take a different batch instead of waiting on, or double-publishing, this one.
     */
    @Query(value = """
            select * from outbox_event
            where published_at is null
            order by created_at, id
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockNextUnpublished(int limit);
}
