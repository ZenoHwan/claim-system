package com.claims.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class NotificationStore {

    // ponytail: inbox is capped, not paged; add paging when someone has more than this many
    private static final int INBOX_LIMIT = 100;

    private static final RowMapper<Notification> ROW = (rs, n) -> new Notification(
            rs.getObject("id", UUID.class),
            rs.getObject("recipient_id", UUID.class),
            rs.getObject("claim_id", UUID.class),
            rs.getString("claim_number"),
            rs.getString("type"),
            rs.getString("message"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("read_at") != null);

    private final JdbcClient jdbc;

    NotificationStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return false if this event was already handled, in which case the caller must do nothing */
    boolean markProcessed(UUID eventId) {
        return jdbc.sql("INSERT INTO processed_event (event_id, processed_at) VALUES (?, now()) ON CONFLICT DO NOTHING")
                .param(eventId)
                .update() == 1;
    }

    Notification insert(UUID recipientId, UUID claimId, String claimNumber, String type, String message) {
        return jdbc.sql("""
                        INSERT INTO notification (id, recipient_id, claim_id, claim_number, type, message, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, now())
                        RETURNING *
                        """)
                .params(UUID.randomUUID(), recipientId, claimId, claimNumber, type, message)
                .query(ROW)
                .single();
    }

    List<Notification> inbox(UUID recipientId) {
        return jdbc.sql("SELECT * FROM notification WHERE recipient_id = ? ORDER BY created_at DESC LIMIT ?")
                .params(recipientId, INBOX_LIMIT)
                .query(ROW)
                .list();
    }

    int unreadCount(UUID recipientId) {
        return jdbc.sql("SELECT count(*) FROM notification WHERE recipient_id = ? AND read_at IS NULL")
                .param(recipientId)
                .query(Integer.class)
                .single();
    }

    /** @return false if there is no such notification for this recipient */
    boolean markRead(UUID id, UUID recipientId) {
        return jdbc.sql("UPDATE notification SET read_at = coalesce(read_at, now()) WHERE id = ? AND recipient_id = ?")
                .params(id, recipientId)
                .update() == 1;
    }

    void markAllRead(UUID recipientId) {
        jdbc.sql("UPDATE notification SET read_at = now() WHERE recipient_id = ? AND read_at IS NULL")
                .param(recipientId)
                .update();
    }
}
