package com.example.recommend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CacheConfig {

    @Bean
    @Primary
    public CacheService cacheService(InMemoryCacheServiceImpl inMemoryCacheService) {
        return inMemoryCacheService;
    }
}
