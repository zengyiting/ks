package com.example.recommend;

import com.example.recommend.service.AlgorithmType;
import com.example.recommend.service.RecommendationCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisCacheConfig {
    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);

    @Bean("cacheManager")
    @Primary
    @ConditionalOnBean(RedisConnectionFactory.class)
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper cacheObjectMapper = new ObjectMapper();
        cacheObjectMapper.registerModule(new JavaTimeModule());
        cacheObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(cacheObjectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "recommend:" + cacheName + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .build();
    }

    @Bean("recommendationCacheKeyGenerator")
    public KeyGenerator recommendationCacheKeyGenerator(RecommendationCacheService recommendationCacheService) {
        return (target, method, params) -> {
            Long userId = params.length > 0 && params[0] instanceof Long id ? id : null;
            int topN = params.length > 1 && params[1] instanceof Number n ? n.intValue() : 0;
            AlgorithmType type = params.length > 2 && params[2] instanceof AlgorithmType t ? t : null;
            return recommendationCacheService.versionedKey(userId, topN, type);
        };
    }

    @Bean
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET failed, fallback to direct execution. cache={}, key={}, reason={}",
                    cacheName(cache), key, exception.getMessage(), exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed, continue without caching. cache={}, key={}, valueType={}, reason={}",
                    cacheName(cache), key, valueType(value), exception.getMessage(), exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache EVICT failed. cache={}, key={}, reason={}",
                        cacheName(cache), key, exception.getMessage(), exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR failed. cache={}, reason={}",
                        cacheName(cache), exception.getMessage(), exception);
            }

            private String cacheName(Cache cache) {
                return cache == null ? "unknown" : cache.getName();
            }

            private String valueType(Object value) {
                return value == null ? "null" : value.getClass().getName();
            }
        };
    }
}
