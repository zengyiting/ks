package com.example.recommend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CacheConfig {
    
    @Value("${cache.type:memory}")
    private String cacheType;
    
    @Bean
    @Primary
    public CacheService cacheService(InMemoryCacheServiceImpl inMemoryCacheService) {
        // 优先使用内存缓存（用于开发/测试环境）
        return inMemoryCacheService;
    }
}
