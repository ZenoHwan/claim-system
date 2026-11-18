package com.claims.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Every operation is scoped to the signed-in user: the token's subject is the recipient. */
@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    record Inbox(int unreadCount, List<Notification> items) {}

    private final NotificationStore store;
    private final LiveStream liveStream;

    NotificationController(NotificationStore store, LiveStream liveStream) {
        this.store = store;
        this.liveStream = liveStream;
    }

    @GetMapping
    Inbox inbox(@AuthenticationPrincipal Jwt token) {
        UUID me = userId(token);
        return new Inbox(store.unreadCount(me), store.inbox(me));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@PathVariable UUID id, @AuthenticationPrincipal Jwt token) {
        if (!store.markRead(id, userId(token))) {
            // same answer for "not yours" and "does not exist", so ids cannot be probed
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@AuthenticationPrincipal Jwt token) {
        return liveStream.subscribe(userId(token));
    }

    private static UUID userId(Jwt token) {
        return UUID.fromString(token.getSubject());
    }
}
