package com.claims.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claims.events.ClaimEvent;
import com.claims.service.IntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/** The relay against a real Kafka broker, including a broker outage. */
class OutboxRelayIntegrationTest extends IntegrationTest {

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.0.0");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // fail fast when the broker is paused, so the outage test takes seconds not minutes
        registry.add("spring.kafka.producer.properties.max.block.ms", () -> "2000");
        registry.add("spring.kafka.producer.properties.request.timeout.ms", () -> "1000");
        registry.add("spring.kafka.producer.properties.delivery.timeout.ms", () -> "2500");
        registry.add("claims.outbox.send-timeout", () -> "4s");
    }

    @Autowired
    OutboxRelay relay;

    @Test
    void aFullLifecycleIsPublishedInOrderKeyedByClaimId() throws Exception {
        String id = approvedClaim("7200.00");
        recordPayment(OFFICER_1, id, "TT-RELAY-1").andExpect(status().isOk());

        drain();

        List<ConsumerRecord<String, String>> records = recordsFor(id, 5);
        assertThat(records).extracting(ConsumerRecord::key).containsOnly(id);
        assertThat(records).extracting(record -> json.readValue(record.value(), ClaimEvent.class).type().name())
                .containsExactly("CLAIM_SUBMITTED", "CLAIM_ASSIGNED", "CLAIM_ASSESSED", "CLAIM_APPROVED", "CLAIM_SETTLED");
        assertThat(records).extracting(ConsumerRecord::partition).containsOnly(records.get(0).partition());
        assertThat(unpublished(id)).isZero();
    }

    @Test
    void anEventIsNotPublishedAgainOnceMarkedPublished() throws Exception {
        String id = submitMotorClaim("100.00");
        drain();

        assertThat(relay.publishPending()).isZero();

        assertThat(recordsFor(id, 1)).hasSize(1);
    }

    @Test
    void whenTheBrokerIsDownTheClaimIsStillAcceptedAndItsEventIsPublishedOnceTheBrokerReturns() throws Exception {
        drain();
        String container = KAFKA.getContainerId();
        KAFKA.getDockerClient().pauseContainerCmd(container).exec();
        String id;
        try {
            id = submitMotorClaim("100.00"); // 201 even though nothing can be published

            assertThat(relay.publishPending()).isZero();
            assertThat(unpublished(id)).isEqualTo(1);
        } finally {
            KAFKA.getDockerClient().unpauseContainerCmd(container).exec();
        }

        drain();

        assertThat(unpublished(id)).isZero();
        assertThat(recordsFor(id, 1))
                .extracting(record -> json.readValue(record.value(), ClaimEvent.class).type().name())
                .contains("CLAIM_SUBMITTED");
    }

    /** Other test classes share the database, so publish until nothing is left. */
    private void drain() {
        for (int i = 0; i < 100 && relay.publishPending() > 0; i++) {
            // keep going
        }
    }

    private int unpublished(String claimId) {
        return jdbc.queryForObject(
                "select count(*) from outbox_event where aggregate_id = ?::uuid and published_at is null", Integer.class, claimId);
    }

    private List<ConsumerRecord<String, String>> recordsFor(String claimId, int atLeast) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(ClaimEvent.TOPIC));
            long deadline = System.currentTimeMillis() + 20_000;
            int idlePolls = 0;
            // read to the end of the topic: stop after the expected count plus one quiet poll
            while (System.currentTimeMillis() < deadline && (found.size() < atLeast || idlePolls < 2)) {
                var batch = consumer.poll(Duration.ofMillis(500));
                idlePolls = batch.isEmpty() ? idlePolls + 1 : 0;
                batch.forEach(record -> {
                    if (claimId.equals(record.key())) {
                        found.add(record);
                    }
                });
            }
        }
        return found;
    }
}
