package com.claims.notification;

import static com.claims.events.ClaimEventType.CLAIM_APPROVED;
import static com.claims.events.ClaimEventType.CLAIM_ASSIGNED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InboxApiTest extends IntegrationTestBase {

    @Test
    void aRequestWithoutATokenIsRejected() throws Exception {
        assertThat(get("/api/notifications", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/notifications/stream", null).statusCode()).isEqualTo(401);
        assertThat(post("/api/notifications/" + UUID.randomUUID() + "/read", null).statusCode()).isEqualTo(401);
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() throws Exception {
        String forged = sign(UUID.randomUUID(), "MANAGER", "some-other-secret-that-is-long-enough-000000");

        assertThat(get("/api/notifications", forged).statusCode()).isEqualTo(401);
    }

    @Test
    void theInboxListsOnlyTheCallersNotificationsNewestFirst() throws Exception {
        UUID aisha = UUID.randomUUID();
        UUID ben = UUID.randomUUID();
        var claim = claim(aisha, UUID.randomUUID());
        publish(event(CLAIM_ASSIGNED, claim));
        publish(event(CLAIM_APPROVED, claim));

        var inbox = awaitInbox(aisha, 2);

        assertThat(inbox.get("items").get(0).get("type").asString()).isEqualTo("CLAIM_APPROVED");
        assertThat(inbox.get("items").get(1).get("type").asString()).isEqualTo("CLAIM_ASSIGNED");
        assertThat(inbox.get("unreadCount").asInt()).isEqualTo(2);
        var bensInbox = inboxOf(ben);
        assertThat(bensInbox.get("items").size()).isZero();
        assertThat(bensInbox.get("unreadCount").asInt()).isZero();
    }

    @Test
    void markingAsReadLowersTheUnreadCount() throws Exception {
        UUID aisha = UUID.randomUUID();
        var claim = claim(aisha, UUID.randomUUID());
        publish(event(CLAIM_ASSIGNED, claim));
        publish(event(CLAIM_APPROVED, claim));
        String id = awaitInbox(aisha, 2).get("items").get(0).get("id").asString();

        var response = post("/api/notifications/" + id + "/read", tokenFor(aisha, "CLAIMANT"));

        assertThat(response.statusCode()).isEqualTo(204);
        var inbox = inboxOf(aisha);
        assertThat(inbox.get("unreadCount").asInt()).isEqualTo(1);
        assertThat(inbox.get("items").get(0).get("read").asBoolean()).isTrue();
    }

    @Test
    void anotherUsersNotificationCannotBeMarkedAsRead() throws Exception {
        UUID aisha = UUID.randomUUID();
        UUID ben = UUID.randomUUID();
        publish(event(CLAIM_ASSIGNED, claim(aisha, UUID.randomUUID())));
        String id = awaitInbox(aisha, 1).get("items").get(0).get("id").asString();

        assertThat(post("/api/notifications/" + id + "/read", tokenFor(ben, "CLAIMANT")).statusCode()).isEqualTo(404);
        assertThat(inboxOf(aisha).get("unreadCount").asInt()).isEqualTo(1);
    }

    @Test
    void anUnknownNotificationIsNotFound() throws Exception {
        var response = post("/api/notifications/" + UUID.randomUUID() + "/read", tokenFor(UUID.randomUUID(), "OFFICER"));

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void theOpenApiDocumentListsTheInboxAndStreamEndpoints() throws Exception {
        var response = get("/v3/api-docs", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("/api/notifications\"")
                .contains("/api/notifications/{id}/read")
                .contains("/api/notifications/stream");
    }
}
