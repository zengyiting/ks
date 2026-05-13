package com.example.recommend.service;

import com.example.recommend.repository.RatingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 离线预计算 item-item 共现相似度，在线阶段只做轻量读取与融合。
 */
@Service
public class ItemAssociationPrecomputeService {

    /**
     * 应用启动完成后先执行一次离线构建，避免首个请求触发重计算。
     */   private static final Logger log = LoggerFactory.getLogger(ItemAssociationPrecomputeService.class);

    private final RatingRepository ratingRepository;
    private final int neighborLimit;
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private volatile Snapshot snapshot = Snapshot.empty();

    public ItemAssociationPrecomputeService(
        RatingRepository ratingRepository,
        @Value("${recommend.association.neighbor-limit:120}") int neighborLimit
    ) {
        this.ratingRepository = ratingRepository;
        this.neighborLimit = Math.max(10, neighborLimit);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        refreshNow();
    }

    /**
     * 定时离线刷新，默认每 15 分钟执行一次。
     */
    @Scheduled(
            initialDelayString = "${recommend.association.initial-delay-ms:60000}",
            fixedDelayString = "${recommend.association.precompute-interval-ms:900000}"
    )
    public void scheduledRefresh() {
        if (!dirty.get() && !snapshot.isEmpty()) {
            return;
        }
        refreshNow();
    }

    /**
     * 行为写入后标脏，下一轮定时任务会增量刷新快照。
     */
    public void markDirty() {
        dirty.set(true);
    }

    public Map<Long, Double> neighbors(Long itemId) {
        if (itemId == null) {
            return Map.of();
        }
        return snapshot.similarityByItem().getOrDefault(itemId, Map.of());
    }

    public Instant lastUpdatedAt() {
        return snapshot.updatedAt();
    }

    @Transactional(readOnly = true)
    public synchronized void refreshNow() {
        try {
            dirty.set(false);
            Snapshot rebuilt = buildSnapshot();
            snapshot = rebuilt;
            log.info("Refreshed item association snapshot: items={}, updatedAt={}",
                    rebuilt.similarityByItem().size(),
                    rebuilt.updatedAt());
        } catch (RuntimeException ex) {
            dirty.set(true);
            log.warn("Failed to refresh item association snapshot, keep previous snapshot", ex);
        }
    }

    private Snapshot buildSnapshot() {
        List<RatingRepository.UserItemScoreView> rows = ratingRepository.findAllUserItemScores();
        if (rows == null || rows.isEmpty()) {
            return Snapshot.empty();
        }

        Map<Long, Set<Long>> userItems = new HashMap<>();
        for (RatingRepository.UserItemScoreView row : rows) {
            Long userId = row.getUserId();
            Long itemId = row.getItemId();
            if (userId == null || itemId == null) {
                continue;
            }
            userItems.computeIfAbsent(userId, ignored -> new HashSet<>()).add(itemId);
        }
        if (userItems.isEmpty()) {
            return Snapshot.empty();
        }

        Map<Long, Integer> itemUserCount = new HashMap<>();
        Map<Long, Map<Long, Integer>> coCount = new HashMap<>();
        for (Set<Long> items : userItems.values()) {
            if (items.isEmpty()) {
                continue;
            }
            List<Long> list = new ArrayList<>(items);
            for (Long itemId : list) {
                itemUserCount.merge(itemId, 1, Integer::sum);
            }
            for (int i = 0; i < list.size(); i++) {
                Long left = list.get(i);
                for (int j = i + 1; j < list.size(); j++) {
                    Long right = list.get(j);
                    coCount.computeIfAbsent(left, ignored -> new HashMap<>()).merge(right, 1, Integer::sum);
                    coCount.computeIfAbsent(right, ignored -> new HashMap<>()).merge(left, 1, Integer::sum);
                }
            }
        }

        Map<Long, Map<Long, Double>> similarityByItem = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Integer>> entry : coCount.entrySet()) {
            Long itemId = entry.getKey();
            int itemUsers = itemUserCount.getOrDefault(itemId, 0);
            if (itemUsers <= 0) {
                continue;
            }

            PriorityQueue<Map.Entry<Long, Double>> top = new PriorityQueue<>(Comparator.comparingDouble(Map.Entry::getValue));
            for (Map.Entry<Long, Integer> neighborEntry : entry.getValue().entrySet()) {
                Long neighborId = neighborEntry.getKey();
                int neighborUsers = itemUserCount.getOrDefault(neighborId, 0);
                if (neighborUsers <= 0) {
                    continue;
                }
                double sim = neighborEntry.getValue() / Math.sqrt((double) itemUsers * neighborUsers);
                if (sim <= 1e-12) {
                    continue;
                }
                Map.Entry<Long, Double> simEntry = Map.entry(neighborId, sim);
                if (top.size() < neighborLimit) {
                    top.offer(simEntry);
                } else if (top.peek() != null && top.peek().getValue() < sim) {
                    top.poll();
                    top.offer(simEntry);
                }
            }
            if (top.isEmpty()) {
                continue;
            }

            List<Map.Entry<Long, Double>> sorted = new ArrayList<>(top);
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            Map<Long, Double> neighbors = new LinkedHashMap<>();
            for (Map.Entry<Long, Double> simEntry : sorted) {
                neighbors.put(simEntry.getKey(), simEntry.getValue());
            }
            similarityByItem.put(itemId, Map.copyOf(neighbors));
        }
        return new Snapshot(Map.copyOf(similarityByItem), Instant.now());
    }

    private record Snapshot(Map<Long, Map<Long, Double>> similarityByItem, Instant updatedAt) {
        static Snapshot empty() {
            return new Snapshot(Map.of(), Instant.EPOCH);
        }

        boolean isEmpty() {
            return similarityByItem.isEmpty();
        }
    }
}
