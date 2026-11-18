package com.claims.service.claim;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis cache for the reads every signed-in user repeats: the Follow-ups list (fetched on every page
 * change, a query per claim), each person's claim list and each market's unassigned queue. All of them
 * change only when a claim changes, so {@link ClaimChanges} clears the affected entries on that one path.
 * If Redis cannot be reached the error is logged and the database answers.
 */
@Configuration
@EnableCaching
class CacheConfig implements CachingConfigurer {

    /** Per user. */
    static final String FOLLOW_UPS = "followUps";
    /** Per claimant: the claims they submitted. */
    static final String CLAIMANT_CLAIMS = "claimantClaims";
    /** Per officer: the claims assigned to them. */
    static final String OFFICER_CLAIMS = "officerClaims";
    /** Per market: submitted claims nobody has picked up. */
    static final String QUEUE = "queue";

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory redis, JsonMapper json, @Value("${claims.cache.ttl}") Duration ttl) {
        // Typed JSON, not Java serialisation: readable in redis-cli and nothing but these types is ever created from it.
        var followUps = typed(json, ttl, ClaimViews.FollowUp.class);
        var claimList = typed(json, ttl, ClaimViews.ListItem.class);
        return RedisCacheManager.builder(redis)
                .withCacheConfiguration(FOLLOW_UPS, followUps)
                .withCacheConfiguration(CLAIMANT_CLAIMS, claimList)
                .withCacheConfiguration(OFFICER_CLAIMS, claimList)
                .withCacheConfiguration(QUEUE, claimList)
                .disableCreateOnMissingCache()
                .build();
    }

    private static RedisCacheConfiguration typed(JsonMapper json, Duration ttl, Class<?> element) {
        var listOf = json.getTypeFactory().constructCollectionType(List.class, element);
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(json, listOf)));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
