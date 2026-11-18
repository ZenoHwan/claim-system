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
 * Redis cache for the read every signed-in user repeats: the Follow-ups list is fetched on every page
 * change and costs a query per claim. It is cached per user and cleared by {@link ClaimChanges} when one
 * of that user's claims changes. If Redis cannot be reached the error is logged and the database answers.
 */
@Configuration
@EnableCaching
class CacheConfig implements CachingConfigurer {

    static final String FOLLOW_UPS = "followUps";

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory redis, JsonMapper json, @Value("${claims.cache.ttl}") Duration ttl) {
        // Typed JSON, not Java serialisation: readable in redis-cli and nothing but this type is ever created from it.
        var followUps = new JacksonJsonRedisSerializer<List<ClaimViews.FollowUp>>(
                json, json.getTypeFactory().constructCollectionType(List.class, ClaimViews.FollowUp.class));
        return RedisCacheManager.builder(redis)
                .withCacheConfiguration(FOLLOW_UPS, RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(ttl)
                        .serializeValuesWith(SerializationPair.fromSerializer(followUps)))
                .disableCreateOnMissingCache()
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
