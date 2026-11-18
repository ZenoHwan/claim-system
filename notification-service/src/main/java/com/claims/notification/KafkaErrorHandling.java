package com.claims.notification;

import com.claims.events.ClaimEvent;
import java.time.Duration;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.core.JacksonException;

@Configuration(proxyBeanMethods = false)
class KafkaErrorHandling {

    static final String DEAD_LETTER_TOPIC = ClaimEvent.TOPIC + ".dlt";
    private static final int RETRIES = 3;

    /**
     * A failing event is retried with a doubling wait, then parked on the dead-letter topic so the
     * events behind it keep flowing. Spring Boot applies this bean to the listener container.
     */
    @Bean
    DefaultErrorHandler claimEventErrorHandler(
            KafkaTemplate<String, String> kafka, @Value("${claims.kafka.retry-interval}") Duration firstWait) {
        var deadLetters = new DeadLetterPublishingRecoverer(
                kafka, (failed, error) -> new TopicPartition(DEAD_LETTER_TOPIC, -1)); // -1: let Kafka pick the partition
        var backOff = new ExponentialBackOffWithMaxRetries(RETRIES);
        backOff.setInitialInterval(firstWait.toMillis());
        backOff.setMultiplier(2.0);
        var handler = new DefaultErrorHandler(deadLetters, backOff);
        handler.addNotRetryableExceptions(JacksonException.class); // unreadable now is unreadable later
        return handler;
    }
}
