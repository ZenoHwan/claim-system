package com.claims.notification;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Open server-sent-event connections, by user.
 *
 * ponytail: held in memory, so it only reaches users connected to this instance. With more than
 * one instance, give each its own consumer group (or put a pub/sub in front) so every instance
 * sees every event.
 */
@Component
class LiveStream {

    private final ConcurrentHashMap<UUID, Set<SseEmitter>> connections = new ConcurrentHashMap<>();

    SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(0L); // no server-side timeout; the client closes it
        Set<SseEmitter> mine = connections.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet());
        mine.add(emitter);
        Runnable forget = () -> mine.remove(emitter);
        emitter.onCompletion(forget);
        emitter.onTimeout(forget);
        emitter.onError(error -> forget.run());
        send(emitter, SseEmitter.event().name("ready").data(""), forget);
        return emitter;
    }

    void push(Notification notification) {
        for (SseEmitter emitter : connections.getOrDefault(notification.recipientId(), Set.of())) {
            send(emitter,
                    SseEmitter.event().name("notification").data(notification, MediaType.APPLICATION_JSON),
                    () -> connections.get(notification.recipientId()).remove(emitter));
        }
    }

    /**
     * Ends every stream as soon as shutdown begins. Graceful shutdown waits for active requests, and
     * a stream never finishes on its own, so without this every restart would hang for the full
     * grace period whenever a user has the page open.
     */
    @EventListener(ContextClosedEvent.class)
    void closeAll() {
        connections.values().forEach(emitters -> emitters.forEach(SseEmitter::complete));
    }

    private static void send(SseEmitter emitter, SseEmitter.SseEventBuilder event, Runnable onDeadConnection) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException gone) {
            onDeadConnection.run();
        }
    }
}
