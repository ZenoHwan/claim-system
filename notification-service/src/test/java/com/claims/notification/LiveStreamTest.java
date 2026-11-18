package com.claims.notification;

import static com.claims.events.ClaimEventType.INFORMATION_REQUESTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class LiveStreamTest extends IntegrationTestBase {

    @Test
    void aSubscribedUserReceivesANotificationCreatedAfterSubscribing() {
        UUID claimant = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        var claim = claim(claimant, UUID.randomUUID());
        List<String> received = subscribe(claimant);
        List<String> receivedByOther = subscribe(someoneElse);
        await().atMost(Duration.ofSeconds(10)).until(() -> received.contains("event:ready") && receivedByOther.contains("event:ready"));

        publish(event(INFORMATION_REQUESTED, claim));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(dataLines(received)).hasSize(1));
        var pushed = JSON.readTree(dataLines(received).get(0).substring("data:".length()));
        assertThat(pushed.get("claimId").asString()).isEqualTo(claim.claimId().toString());
        assertThat(pushed.get("type").asString()).isEqualTo("INFORMATION_REQUESTED");
        assertThat(pushed.get("message").asString()).contains(claim.claimNumber());
        assertThat(dataLines(receivedByOther)).isEmpty();
        // delivered once: not through Redis and directly as well
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).until(() -> dataLines(received).size() == 1);
    }

    @Test
    void aNotificationHandledByAnotherInstanceReachesAUserConnectedHere() {
        UUID claimant = UUID.randomUUID();
        List<String> received = subscribe(claimant);
        await().atMost(Duration.ofSeconds(10)).until(() -> received.contains("event:ready"));

        // What another instance does once it has stored a notification: publish it on the shared channel.
        var fromElsewhere = new Notification(UUID.randomUUID(), claimant, UUID.randomUUID(), "CLM-2026-000777",
                "CLAIM_APPROVED", "Your claim CLM-2026-000777 was approved", Instant.now(), false);
        redisTemplate.convertAndSend("claim-notifications", JSON.writeValueAsString(fromElsewhere));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(dataLines(received)).hasSize(1));
        assertThat(dataLines(received).get(0)).contains("CLM-2026-000777");
    }

    @Test
    void usersConnectedHereStillGetLiveUpdatesWhenRedisIsDown() throws Exception {
        UUID claimant = UUID.randomUUID();
        List<String> received = subscribe(claimant);
        await().atMost(Duration.ofSeconds(10)).until(() -> received.contains("event:ready"));

        // One update while Redis is up, so the connection to it is already open when it goes down.
        publish(event(INFORMATION_REQUESTED, claim(claimant, UUID.randomUUID())));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(dataLines(received)).hasSize(1));

        whileRedisIsDown(() -> {
            publish(event(INFORMATION_REQUESTED, claim(claimant, UUID.randomUUID())));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(dataLines(received)).hasSize(2));
        });
        // A timed-out publish can still be delivered once Redis resumes; the user must not get it twice.
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4)).until(() -> dataLines(received).size() == 2);
    }

    /** Opens the stream the way the Angular client will: a plain GET with the Authorization header. */
    private List<String> subscribe(UUID userId) {
        List<String> lines = new CopyOnWriteArrayList<>();
        var request = request("/api/notifications/stream", tokenFor(userId, "CLAIMANT"))
                .timeout(Duration.ofMinutes(2)).header("Accept", "text/event-stream").GET().build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    response.body().forEach(lines::add);
                });
        return lines;
    }

    private static List<String> dataLines(List<String> lines) {
        return lines.stream().filter(line -> line.startsWith("data:{")).toList();
    }
}
