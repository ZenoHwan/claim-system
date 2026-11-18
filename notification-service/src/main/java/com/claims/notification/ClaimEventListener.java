package com.claims.notification;

import com.claims.events.ClaimEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class ClaimEventListener {

    private final JsonMapper json;
    private final NotificationService notifications;
    private final LiveStream liveStream;

    ClaimEventListener(JsonMapper json, NotificationService notifications, LiveStream liveStream) {
        this.json = json;
        this.notifications = notifications;
        this.liveStream = liveStream;
    }

    @KafkaListener(topics = ClaimEvent.TOPIC)
    void onClaimEvent(String payload) {
        ClaimEvent event = json.readValue(payload, ClaimEvent.class);
        // push only after handle() has committed, so a pushed notification is always in the inbox
        notifications.handle(event).ifPresent(liveStream::push);
    }
}
