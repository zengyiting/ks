package com.example.recommend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存缓存服务实现 - 用于存储验证码和Token
 */
@Service
public class InMemoryCacheServiceImpl implements CacheService {
    private static final Logger log = LoggerFactory.getLogger(InMemoryCacheServiceImpl.class);

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
    private final AtomicInteger hitCount = new AtomicInteger(0);
    private final AtomicInteger missCount = new AtomicInteger(0);

    @Override
    public void set(String key, String value, int expireSeconds) {
        long expireTime = System.currentTimeMillis() + (expireSeconds * 1000L);
        cache.put(key, new CacheEntry(value, expireTime));
    }

    @Override
    public String get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            missCount.incrementAndGet();
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(key);
            missCount.incrementAndGet();
            return null;
        }
        hitCount.incrementAndGet();
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
     * 每60秒定时清理过期缓存项
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpired() {
        int before = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removed = before - cache.size();
        if (removed > 0) {
            log.debug("In-memory cache cleanup: removed {} expired entries, remaining {}", removed, cache.size());
        }
    }

    /**
     * 获取缓存统计信息（用于监控）
     */
    public int getCacheSize() {
        return cache.size();
    }

    public int getHitCount() {
        return hitCount.get();
    }

    public int getMissCount() {
        return missCount.get();
    }
}
