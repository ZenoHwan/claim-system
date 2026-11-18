package com.claims.reporting;

import java.time.Duration;
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
 * Redis cache for the three reports, which aggregate over every claim and grow with the book.
 * Workload and performance are cached per market, exposure once; {@link ClaimSummaryProjector} clears
 * them when it applies an event. If Redis cannot be reached the error is logged and the database answers.
 */
@Configuration
@EnableCaching
class CacheConfig implements CachingConfigurer {

    static final String WORKLOAD = "workload";
    static final String PERFORMANCE = "performance";
    static final String EXPOSURE = "exposure";
    /** Exposure covers every market, so it has one entry; ReportQueries.exposure uses the same key. */
    static final String ALL_MARKETS = "all";

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory redis, JsonMapper json, @Value("${claims.cache.ttl}") Duration ttl) {
        return RedisCacheManager.builder(redis)
                .withCacheConfiguration(WORKLOAD, typed(json, ttl, ReportQueries.Workload.class))
                .withCacheConfiguration(PERFORMANCE, typed(json, ttl, ReportQueries.Performance.class))
                .withCacheConfiguration(EXPOSURE, typed(json, ttl, ReportQueries.Exposure.class))
                .disableCreateOnMissingCache()
                .build();
    }

    // Typed JSON, not Java serialisation: readable in redis-cli and nothing but this type is ever created from it.
    private static RedisCacheConfiguration typed(JsonMapper json, Duration ttl, Class<?> type) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(json, type)));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
