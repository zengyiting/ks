package com.example.recommend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 推荐缓存管理服务
 *
 * <p>通过版本号进行缓存失效，避免高频行为写入时大量 Redis 删除操作。</p>
 */
@Service
public class RecommendationCacheService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationCacheService.class);
    private static final int MAX_USER_VERSION_ENTRIES = 100_000;
    private static final int PRUNE_TARGET_ENTRIES = 50_000;

    private final AtomicLong globalVersion = new AtomicLong(1);
    private final ConcurrentMap<Long, AtomicLong> userVersions = new ConcurrentHashMap<>();

    public String versionedKey(Long userId, int topN, AlgorithmType type) {
        AlgorithmType safeType = type == null ? AlgorithmType.USER_BASED : type;
        long gv = globalVersion.get();
        long uv = userId == null ? 0L : userVersions.computeIfAbsent(userId, ignored -> new AtomicLong(1)).get();
        String key = userId + ":" + safeType.name() + ":" + topN + ":g" + gv + ":u" + uv;
        log.debug("recommendation cache key generated: userId={}, topN={}, type={}, key={}",
                userId, topN, safeType, key);
        return key;
    }

    public void invalidateUser(Long userId) {
        if (userId == null) {
            return;
        }
        maybePruneUserVersions();
        long newVersion = userVersions.computeIfAbsent(userId, ignored -> new AtomicLong(1)).incrementAndGet();
        log.debug("recommendation cache invalidated for user: userId={}, userVersion={}", userId, newVersion);
    }

    public void invalidateUsers(Iterable<Long> userIds) {
        if (userIds == null) {
            return;
        }
        Set<Long> dedup = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null) {
                dedup.add(userId);
            }
        }
        for (Long userId : dedup) {
            invalidateUser(userId);
        }
    }

    public void invalidateAll() {
        long newGlobalVersion = globalVersion.incrementAndGet();
        userVersions.clear();
        log.debug("recommendation cache globally invalidated: globalVersion={}", newGlobalVersion);
    }

    private void maybePruneUserVersions() {
        int size = userVersions.size();
        if (size <= MAX_USER_VERSION_ENTRIES) {
            return;
        }

        int removed = 0;
        for (Long userId : userVersions.keySet()) {
            if (userVersions.size() <= PRUNE_TARGET_ENTRIES) {
                break;
            }
            if (userId != null && userVersions.remove(userId) != null) {
                removed++;
            }
        }
        if (removed > 0) {
            log.warn("recommendation user-version map pruned: removed={}, remain={}", removed, userVersions.size());
        }
    }
}
