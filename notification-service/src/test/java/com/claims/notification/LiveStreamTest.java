package com.claims.notification;

import static com.claims.events.ClaimEventType.INFORMATION_REQUESTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.http.HttpResponse;
import java.time.Duration;
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
