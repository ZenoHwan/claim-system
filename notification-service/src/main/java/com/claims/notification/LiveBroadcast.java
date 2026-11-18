package com.claims.notification;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends a new notification to every instance of this service over a Redis channel, so it reaches the
 * user whichever instance holds their live connection. Kafka gives each event to one instance only;
 * that instance stores the notification and publishes it here, and every instance (itself included)
 * pushes it to its own connections.
 *
 * If Redis cannot be reached, the notification is pushed to this instance's connections only. It is
 * already in the inbox, so a user connected elsewhere sees it on their next page load. A publish that
 * timed out can still arrive once Redis recovers, so those notifications are remembered and not pushed twice.
 */
@Component
class LiveBroadcast implements MessageListener {

    static final String CHANNEL = "claim-notifications";

    private static final Logger log = LoggerFactory.getLogger(LiveBroadcast.class);

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final LiveStream liveStream;
    private final RedisConnectionFactory connections;
    private volatile RedisMessageListenerContainer subscription;

    /** Notifications pushed directly because the publish failed; the newest 1,000 are plenty to cover a late arrival. */
    private final Set<UUID> pushedDirectly = Collections.synchronizedSet(Collections.newSetFromMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                    return size() > 1_000;
                }
            }));

    LiveBroadcast(StringRedisTemplate redis, JsonMapper json, LiveStream liveStream, RedisConnectionFactory connections) {
        this.connections = connections;
        this.redis = redis;
        this.json = json;
        this.liveStream = liveStream;
    }

    void publish(Notification notification) {
        try {
            redis.convertAndSend(CHANNEL, json.writeValueAsString(notification));
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, pushing to this instance's connections only: {}", e.getMessage());
            pushedDirectly.add(notification.id());
            liveStream.push(notification);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        Notification notification = json.readValue(message.getBody(), Notification.class);
        if (!pushedDirectly.remove(notification.id())) {
            liveStream.push(notification);
        }
    }

    /**
     * Subscribes once the application is up, not as part of starting it: a missing Redis must not stop
     * the service, and its inbox, from starting. If Redis is not there yet this keeps trying in the
     * background; after the first subscription the container restores it whenever the connection drops.
     */
    @EventListener
    void subscribe(ApplicationReadyEvent ready) {
        if (subscribed()) {
            return;
        }
        var context = ready.getApplicationContext();
        Thread.ofVirtual().name("live-broadcast-subscribe").start(() -> {
            while (context.isActive()) {
                try {
                    Thread.sleep(Duration.ofSeconds(5));
                } catch (InterruptedException stop) {
                    return;
                }
                if (subscribed()) {
                    return;
                }
            }
        });
    }

    /** Each attempt uses a new container: one that failed to start reports itself as started and never subscribes. */
    private boolean subscribed() {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connections);
        container.addMessageListener(this, new ChannelTopic(CHANNEL));
        container.afterPropertiesSet();
        try {
            container.start();
            subscription = container;
            log.info("Subscribed to live notifications on Redis");
            return true;
        } catch (RuntimeException redisDown) {
            log.warn("Cannot subscribe to live notifications yet, will retry: {}", redisDown.getMessage());
            close(container);
            return false;
        }
    }

    @PreDestroy
    void unsubscribe() {
        if (subscription != null) {
            close(subscription);
        }
    }

    private static void close(RedisMessageListenerContainer container) {
        try {
            container.destroy();
        } catch (Exception alreadyClosed) {
            // nothing left to release
        }
    }
}
