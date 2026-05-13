package com.example.recommend.config;

public interface CacheService {
    void set(String key, String value, int expireSeconds);
    String get(String key);
    void delete(String key);
    boolean exists(String key);
}
