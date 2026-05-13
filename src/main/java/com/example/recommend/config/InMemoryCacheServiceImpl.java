package com.example.recommend.config;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存缓存服务实现 - 用于替代Redis存储验证码和Token
 */
@Service
public class InMemoryCacheServiceImpl implements CacheService {
    
    private static class CacheEntry {
        final String value;
        final long expireTime;
        
        CacheEntry(String value, long expireTime) {
            this.value = value;
            this.expireTime = expireTime;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
    
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    
    @Override
    public void set(String key, String value, int expireSeconds) {
        long expireTime = System.currentTimeMillis() + (expireSeconds * 1000L);
        cache.put(key, new CacheEntry(value, expireTime));
        cleanupExpired();
    }
    
    @Override
    public String get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            if (entry != null) {
                cache.remove(key);
            }
            return null;
        }
        return entry.value;
    }
    
    @Override
    public void delete(String key) {
        cache.remove(key);
    }
    
    @Override
    public boolean exists(String key) {
        CacheEntry entry = cache.get(key);
        return entry != null && !entry.isExpired();
    }
    
    /**
     * 清理过期的缓存项
     */
    private void cleanupExpired() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
