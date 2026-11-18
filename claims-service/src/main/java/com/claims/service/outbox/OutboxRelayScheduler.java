package com.claims.service.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the relay on a timer. Switched off in tests, which call the relay directly. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "claims.outbox.relay.enabled", matchIfMissing = true)
class OutboxRelayScheduler {

    private final OutboxRelay relay;

    OutboxRelayScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${claims.outbox.poll-interval}")
    void tick() {
        // drain a backlog (for example after a broker outage) without waiting a tick per batch
        while (relay.publishPending() > 0) {
            // keep going
        }
    }
}
