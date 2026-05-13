package com.example.recommend;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.recommend.service.AlgorithmType;
import com.example.recommend.service.RecommendationCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
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
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RedisCacheConfig implements CachingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);
    private static final String RECOMMENDATION_CACHE = "recommendationResults";

    /**
     * 创建并配置Redis缓存管理器Bean
     *
     * <p>该方法创建一个基于Redis的缓存管理器，用于管理应用程序的缓存操作。
     * 配置的默认缓存策略包括：
     * <ul>
     *   <li>缓存条目过期时间：10分钟</li>
     *   <li>值序列化方式：使用GenericJackson2JsonRedisSerializer进行JSON序列化</li>
     * </ul>
     *
     * @param connectionFactory Redis连接工厂，用于建立与Redis服务器的连接
     * @return 配置好的RedisCacheManager实例，用于管理Redis缓存
     */
    @Bean("cacheManager")
    @Primary
    @ConditionalOnBean(RedisConnectionFactory.class)
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper cacheObjectMapper = new ObjectMapper();
        cacheObjectMapper.registerModule(new JavaTimeModule());
        cacheObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        cacheObjectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(cacheObjectMapper);

        // 配置默认缓存策略：设置过期时间和序列化方式
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "recommend:" + cacheName + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put(RECOMMENDATION_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // 基于连接工厂和默认配置构建缓存管理器
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
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

    @Override
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
