package com.claims.notification;

import static com.claims.events.ClaimEventType.CLAIM_APPROVED;
import static com.claims.events.ClaimEventType.CLAIM_ASSIGNED;
import static com.claims.events.ClaimEventType.CLAIM_SUBMITTED;
import static com.claims.events.ClaimEventType.INFORMATION_PROVIDED;
import static com.claims.events.ClaimEventType.INFORMATION_REQUESTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.claims.events.ClaimEvent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.testcontainers.kafka.KafkaContainer;

@ExtendWith(OutputCaptureExtension.class)
class ClaimEventConsumptionTest extends IntegrationTestBase {

    @Autowired
    KafkaContainer kafkaContainer;

    @Test
    void anInformationRequestBecomesAnUnreadNotificationForTheClaimant() throws Exception {
        UUID claimant = UUID.randomUUID();
        var claim = claim(claimant, UUID.randomUUID());

        publish(event(INFORMATION_REQUESTED, claim));

        var inbox = awaitInbox(claimant, 1);
        assertThat(inbox.get("unreadCount").asInt()).isEqualTo(1);
        var item = inbox.get("items").get(0);
        assertThat(item.get("claimId").asString()).isEqualTo(claim.claimId().toString());
        assertThat(item.get("claimNumber").asString()).isEqualTo(claim.claimNumber());
        assertThat(item.get("type").asString()).isEqualTo("INFORMATION_REQUESTED");
        assertThat(item.get("message").asString()).contains(claim.claimNumber()).containsIgnoringCase("more information");
        assertThat(item.get("read").asBoolean()).isFalse();
    }

    @Test
    void aClaimantResponseNotifiesTheAssignedOfficerAndNotTheClaimant() throws Exception {
        UUID claimant = UUID.randomUUID();
        UUID officer = UUID.randomUUID();

        publish(event(INFORMATION_PROVIDED, claim(claimant, officer)));

        awaitInbox(officer, 1);
        assertThat(inboxOf(claimant).get("items").size()).isZero();
    }

    @Test
    void aSubmissionNotifiesNobody() throws Exception {
        UUID claimant = UUID.randomUUID();
        var claim = claim(claimant, UUID.randomUUID());

        publish(event(CLAIM_SUBMITTED, claim));
        publish(event(CLAIM_ASSIGNED, claim)); // same partition, so it is handled after the submission

        var inbox = awaitInbox(claimant, 1);
        assertThat(inbox.get("items").get(0).get("type").asString()).isEqualTo("CLAIM_ASSIGNED");
    }

    @Test
    void theSameEventDeliveredTwiceCreatesOneNotification() throws Exception {
        UUID claimant = UUID.randomUUID();
        var claim = claim(claimant, UUID.randomUUID());
        ClaimEvent requested = event(INFORMATION_REQUESTED, claim);

        publish(requested);
        publish(requested);
        publish(event(CLAIM_APPROVED, claim)); // marker: once this shows up, both copies above were handled

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(typesIn(claimant)).contains("CLAIM_APPROVED"));
        assertThat(typesIn(claimant)).containsExactlyInAnyOrder("INFORMATION_REQUESTED", "CLAIM_APPROVED");
    }

    @Test
    void anUnprocessableEventIsDeadLetteredAndLaterEventsStillFlow() throws Exception {
        UUID claimant = UUID.randomUUID();
        var claim = claim(claimant, UUID.randomUUID());
        String key = claim.claimId().toString();

        String notJson = "this is not a claim event";
        // valid JSON but no claim: fails on every retry, so it exercises the retry path too
        String noClaim = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"type\":\"CLAIM_APPROVED\"}";

        kafka.send(ClaimEvent.TOPIC, key, notJson);
        kafka.send(ClaimEvent.TOPIC, key, noClaim);
        publish(event(CLAIM_ASSIGNED, claim));

        awaitInbox(claimant, 1);
        assertThat(deadLettersFor(key)).containsExactly(notJson, noClaim);
    }

    @Test
    void anEmailIsLoggedNotSent(CapturedOutput output) {
        UUID claimant = UUID.randomUUID();
        var claim = claim(claimant, UUID.randomUUID());

        publish(event(CLAIM_APPROVED, claim));

        awaitInbox(claimant, 1);
        assertThat(output.getOut()).contains("Email stub").contains(claimant.toString()).contains(claim.claimNumber());
    }

    private List<String> typesIn(UUID userId) throws Exception {
        return inboxOf(userId).get("items").valueStream().map(item -> item.get("type").asString()).toList();
    }

    private List<String> deadLettersFor(String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(ClaimEvent.TOPIC + ".dlt"));
            List<String> found = new java.util.ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (key.equals(r.key())) {
                        found.add(r.value());
                    }
                });
                assertThat(found).hasSize(2);
            });
            return found;
        }
    }
}
