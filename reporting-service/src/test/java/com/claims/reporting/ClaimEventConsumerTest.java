package com.claims.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.claims.events.ClaimEvent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

class ClaimEventConsumerTest extends IntegrationTest {

    @Autowired
    KafkaTemplate<String, String> kafka;

    private final JsonMapper json = JsonMapper.builder().build();

    private void publish(ClaimEvent event) {
        kafka.send(ClaimEvent.TOPIC, event.claim().claimId().toString(), json.writeValueAsString(event));
    }

    private long summaries(TestClaim claim, String status) {
        return jdbc.sql("SELECT count(*) FROM claim_summary WHERE claim_id = ? AND status = ?")
                .param(claim.claimId).param(status).query(Long.class).single();
    }

    @Test
    void anEventPublishedAsJsonOnTheTopicIsAppliedToTheReadModel() {
        var claim = TestClaim.motorMy(0);
        publish(claim.submitted("8500.00"));
        publish(claim.assigned(TestClaim.TAN, "Tan Wei Ming"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(summaries(claim, "UNDER_REVIEW")).isEqualTo(1));
    }

    @Test
    void anUnprocessableMessageIsDeadLetteredAndLaterEventsAreStillProcessed() {
        var claim = TestClaim.motorMy(0);
        String sameKey = claim.claimId.toString(); // same partition, so the good event queues behind the bad one
        kafka.send(ClaimEvent.TOPIC, sameKey, "{ this is not a claim event");
        publish(claim.submitted("8500.00"));

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(summaries(claim, "SUBMITTED")).isEqualTo(1));

        try (var deadLetters = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dead-letter-test",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            deadLetters.subscribe(List.of(ClaimEventListener.DEAD_LETTER_TOPIC));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                    assertThat(deadLetters.poll(Duration.ofMillis(500)))
                            .anySatisfy(r -> assertThat(r.value()).isEqualTo("{ this is not a claim event")));
        }
    }
}
