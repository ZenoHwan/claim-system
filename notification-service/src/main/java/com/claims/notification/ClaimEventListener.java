package com.claims.notification;

import com.claims.events.ClaimEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class ClaimEventListener {

    private final JsonMapper json;
    private final NotificationService notifications;
    private final LiveBroadcast broadcast;

    ClaimEventListener(JsonMapper json, NotificationService notifications, LiveBroadcast broadcast) {
        this.json = json;
        this.notifications = notifications;
        this.broadcast = broadcast;
    }

    @KafkaListener(topics = ClaimEvent.TOPIC)
    void onClaimEvent(String payload) {
        ClaimEvent event = json.readValue(payload, ClaimEvent.class);
        // broadcast only after handle() has committed, so a pushed notification is always in the inbox
        notifications.handle(event).ifPresent(broadcast::publish);
    }
}
