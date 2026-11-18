package com.claims.service.outbox;

import com.claims.events.ClaimEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves committed outbox rows to Kafka. Delivery is at least once: if the process dies after a send
 * but before the row is stamped, the event is sent again, which consumers tolerate by event id.
 */
// ponytail: polling relay, up to one interval of delay. Change data capture (Debezium) is the upgrade path.
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Duration sendTimeout;

    OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka, @Value("${claims.outbox.send-timeout}") Duration sendTimeout) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.sendTimeout = sendTimeout;
    }

    /** Three partitions; the claim id key keeps each claim's events on one partition, in order. */
    @Bean
    NewTopic claimEventsTopic() {
        return TopicBuilder.name(ClaimEvent.TOPIC).partitions(3).replicas(1).build();
    }

    /**
     * Publishes the next batch, oldest first, waiting for the broker to acknowledge each event before
     * the next. Stops at the first failure so later events can never overtake an unpublished one.
     *
     * @return how many events were published
     */
    @Transactional
    public int publishPending() {
        List<OutboxEvent> batch = outbox.lockNextUnpublished(BATCH_SIZE);
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                kafka.send(ClaimEvent.TOPIC, event.getAggregateId().toString(), event.getPayload())
                        .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Broker down or slow: leave this and the rest unpublished and try again next tick.
                log.warn("Could not publish outbox event {}; {} left waiting: {}", event.getId(), batch.size() - published, e.toString());
                break;
            }
            event.markPublished(Instant.now());
            published++;
        }
        return published;
    }
}
