package com.claims.notification;

import com.claims.events.ClaimEvent;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationStore store;

    NotificationService(NotificationStore store) {
        this.store = store;
    }

    /**
     * Records the event as handled and creates the notification it calls for, in one transaction,
     * so a redelivered event can never create a second notification.
     *
     * @return the notification created, or empty if the event was a duplicate or notifies nobody
     */
    @Transactional
    Optional<Notification> handle(ClaimEvent event) {
        if (!store.markProcessed(event.eventId())) {
            return Optional.empty();
        }
        return NotificationRules.forEvent(event).map(draft -> {
            var claim = event.claim();
            Notification created = store.insert(
                    draft.recipientId(), claim.claimId(), claim.claimNumber(), event.type().name(), draft.message());
            // ponytail: email is a stub by design; swap this line for a mail client when a provider exists
            log.info("Email stub: to={} subject=\"Update on claim {}\"", draft.recipientId(), claim.claimNumber());
            return created;
        });
    }
}
