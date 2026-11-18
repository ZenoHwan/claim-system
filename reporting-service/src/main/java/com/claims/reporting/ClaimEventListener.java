package com.claims.reporting;

import com.claims.events.ClaimEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
class ClaimEventListener {

    static final String DEAD_LETTER_TOPIC = ClaimEvent.TOPIC + ".dlt";

    private final ClaimSummaryProjector projector;
    private final JsonMapper json;

    ClaimEventListener(ClaimSummaryProjector projector, JsonMapper json) {
        this.projector = projector;
        this.json = json;
    }

    @KafkaListener(topics = ClaimEvent.TOPIC)
    void onClaimEvent(String payload) {
        projector.apply(json.readValue(payload, ClaimEvent.class));
    }

    @Bean
    NewTopic deadLetterTopic() {
        return TopicBuilder.name(DEAD_LETTER_TOPIC).partitions(1).replicas(1).build();
    }

    /**
     * Three retries with backoff (0.5s, 1s, 2s) for failures that might pass, then the message is parked on the
     * dead-letter topic so later events keep flowing. A payload that cannot be parsed will never pass, so it is
     * parked at once.
     */
    @Bean
    CommonErrorHandler deadLetterAfterRetries(KafkaTemplate<String, String> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka, (rec, ex) -> new TopicPartition(DEAD_LETTER_TOPIC, 0));
        var backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(500);
        backOff.setMultiplier(2);
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JacksonException.class);
        return handler;
    }
}
