package com.example.recommend.service;

import com.example.recommend.model.Item;
import com.example.recommend.model.UserItemFlag;
import com.example.recommend.repository.ItemRepository;
import com.example.recommend.repository.UserItemFlagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 基于 Redis ZSet 的商品收藏排行榜服务
 */
@Service
public class RankingService {
    private static final Logger log = LoggerFactory.getLogger(RankingService.class);
    private static final String RANKING_KEY = "ranking:favorite";
    private static final Duration RANKING_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final UserItemFlagRepository flagRepository;
    private final ItemRepository itemRepository;
    private volatile boolean redisAvailable = true;

    public RankingService(StringRedisTemplate redisTemplate,
                          UserItemFlagRepository flagRepository,
                          ItemRepository itemRepository) {
        this.redisTemplate = redisTemplate;
        this.flagRepository = flagRepository;
        this.itemRepository = itemRepository;
    }

    @PostConstruct
    public void init() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            rebuildRanking();
        } catch (Exception e) {
            log.warn("Redis not available, ranking service will use DB fallback: {}", e.getMessage());
            redisAvailable = false;
        }
    }

    /**
     * 每5分钟重建排行榜
     * 使用临时Key原子切换，避免并发写入丢失
     */
    @Scheduled(fixedRate = 300000)
    public void rebuildRanking() {
        String tempKey = RANKING_KEY + ":rebuild";
        try {
            log.info("Rebuilding favorite ranking...");
            var zSetOps = redisTemplate.opsForZSet();

            // 分页统计，避免全表加载到内存
            Map<Long, Long> favoriteCounts = countFavoritesPaginated();

            // 写入临时Key
            for (var entry : favoriteCounts.entrySet()) {
                zSetOps.add(tempKey, String.valueOf(entry.getKey()), entry.getValue());
            }

            // 设置TTL
            redisTemplate.expire(tempKey, RANKING_TTL);

            // 原子切换：重命名临时Key为正式Key
            redisTemplate.rename(tempKey, RANKING_KEY);

            redisAvailable = true;
            log.info("Favorite ranking rebuilt with {} items", favoriteCounts.size());
        } catch (Exception e) {
            log.warn("Failed to rebuild ranking: {}", e.getMessage());
            redisAvailable = false;
            // 清理可能残留的临时Key
            try {
                redisTemplate.delete(tempKey);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 分页统计收藏数量，避免全表加载
     */
    private Map<Long, Long> countFavoritesPaginated() {
        Map<Long, Long> counts = new java.util.HashMap<>();
        int page = 0;
        int pageSize = 1000;

        while (true) {
            var pageRequest = PageRequest.of(page, pageSize);
            var pageResult = flagRepository.findAll(pageRequest);
            var content = pageResult.getContent();
            if (content.isEmpty()) {
                break;
            }

            content.stream()
                    .filter(UserItemFlag::isFavorite)
                    .collect(Collectors.groupingBy(UserItemFlag::getItemId, Collectors.counting()))
                    .forEach((itemId, count) -> counts.merge(itemId, count, Long::sum));

            if (!pageResult.hasNext()) {
                break;
            }
            page++;
        }

        return counts;
    }

    /**
     * 增加商品收藏分数
     */
    public void incrementFavorite(Long itemId) {
        if (!redisAvailable) return;
        try {
            redisTemplate.opsForZSet().incrementScore(RANKING_KEY, String.valueOf(itemId), 1);
            redisTemplate.expire(RANKING_KEY, RANKING_TTL);
        } catch (Exception e) {
            log.warn("Failed to increment ranking score: {}", e.getMessage());
        }
    }

    /**
     * 减少商品收藏分数
     */
    public void decrementFavorite(Long itemId) {
        if (!redisAvailable) return;
        try {
            redisTemplate.opsForZSet().incrementScore(RANKING_KEY, String.valueOf(itemId), -1);
            redisTemplate.expire(RANKING_KEY, RANKING_TTL);
        } catch (Exception e) {
            log.warn("Failed to decrement ranking score: {}", e.getMessage());
        }
    }

    /**
     * 获取排行榜 Top N
     */
    public List<RankingItem> getTopRanking(int topN) {
        if (!redisAvailable) {
            return getDbFallbackRanking(topN);
        }
        try {
            var zSetOps = redisTemplate.opsForZSet();
            Set<String> ids = zSetOps.reverseRange(RANKING_KEY, 0, topN - 1);
            if (ids == null || ids.isEmpty()) {
                return getDbFallbackRanking(topN);
            }

            Set<Long> itemIds = ids.stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toSet());

            Map<Long, Item> itemMap = itemRepository.findAllById(itemIds).stream()
                    .collect(Collectors.toMap(Item::getId, Function.identity()));

            List<RankingItem> result = new ArrayList<>();
            for (String idStr : ids) {
                Long itemId = Long.parseLong(idStr);
                Item item = itemMap.get(itemId);
                Double score = zSetOps.score(RANKING_KEY, idStr);
                if (item != null && score != null) {
                    result.add(new RankingItem(
                            item.getId(),
                            item.getName(),
                            item.getCategory(),
                            item.getImageUrl(),
                            item.getPrice() == null ? 0.0 : item.getPrice().doubleValue(),
                            item.getDescription() == null ? "" : item.getDescription(),
                            score.longValue()
                    ));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Redis ranking failed, using DB fallback: {}", e.getMessage());
            return getDbFallbackRanking(topN);
        }
    }

    /**
     * 数据库降级方案：分页统计收藏数量
     */
    private List<RankingItem> getDbFallbackRanking(int topN) {
        Map<Long, Long> favoriteCounts = countFavoritesPaginated();

        Set<Long> itemIds = favoriteCounts.keySet();
        Map<Long, Item> itemMap = itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        return favoriteCounts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> {
                    Item item = itemMap.get(entry.getKey());
                    if (item == null) return null;
                    return new RankingItem(
                            item.getId(),
                            item.getName(),
                            item.getCategory(),
                            item.getImageUrl(),
                            item.getPrice() == null ? 0.0 : item.getPrice().doubleValue(),
                            item.getDescription() == null ? "" : item.getDescription(),
                            entry.getValue()
                    );
                })
                .filter(item -> item != null)
                .collect(Collectors.toList());
    }

    public record RankingItem(
            Long id,
            String name,
            String category,
            String imageUrl,
            double price,
            String description,
            long favoriteCount
    ) {}
}
