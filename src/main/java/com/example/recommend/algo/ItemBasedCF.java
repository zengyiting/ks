package com.example.recommend.algo;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于物品的协同过滤推荐算法（Item-Based Collaborative Filtering）
 * <p>
 * 核心思想：通过分析用户对物品的评分模式，计算物品之间的相似度，
 * 然后基于用户已评分的物品推荐相似的物品。
 * <p>
 * 相似度计算：Cosine Similarity + 弱 shrinkage
 * 预测模式：Ranking模式 - score += sim * rating
 */
public class ItemBasedCF implements RecommenderStrategy {

    private static final double MIN_SIMILARITY = 0.01;
    private static final double GLOBAL_MEAN = 3.5;
    private static final int TOP_K_SIMILAR_ITEMS = 80;
    private static final int MIN_OVERLAP = 1;

    @Override
    public List<Recommendation> recommend(Map<Long, Map<Long, Double>> userItem, Long userId, int topN) {
        if (userItem == null || userItem.isEmpty() || userId == null || topN <= 0) {
            return List.of();
        }

        Map<Long, Double> targetRatings = userItem.getOrDefault(userId, Collections.emptyMap());
        if (targetRatings.isEmpty()) {
            return List.of();
        }

        // 构建物品-用户矩阵
        Map<Long, Map<Long, Double>> itemUsers = buildItemUsers(userItem);
        if (itemUsers.isEmpty()) {
            return List.of();
        }

        // 候选物品池：从用户已评分物品的TopK相似物品中召回
        Map<Long, Double> candidateScores = new HashMap<>();
        Map<Long, Double> simSums = new HashMap<>();

        // 对每个用户已评分的物品，找到相似的物品
        for (Map.Entry<Long, Double> ratedEntry : targetRatings.entrySet()) {
            Long ratedItemId = ratedEntry.getKey();
            double userRating = ratedEntry.getValue();

            Map<Long, Double> ratedItemUsers = itemUsers.getOrDefault(ratedItemId, Collections.emptyMap());
            if (ratedItemUsers.isEmpty()) continue;

            // 找到与当前物品最相似的物品
            List<ItemSim> topSimilarItems = new ArrayList<>();

            for (Map.Entry<Long, Map<Long, Double>> entry : itemUsers.entrySet()) {
                Long candidateItemId = entry.getKey();
                if (Objects.equals(candidateItemId, ratedItemId)) continue;
                if (targetRatings.containsKey(candidateItemId)) continue;

                Map<Long, Double> candidateUsers = entry.getValue();

                // 计算重叠数
                int overlap = 0;
                for (Long uid : ratedItemUsers.keySet()) {
                    if (candidateUsers.containsKey(uid)) {
                        overlap++;
                    }
                }
                if (overlap < MIN_OVERLAP) continue;

                // Cosine相似度（直接使用评分，不减去均值）
                double similarity = cosineSimilarity(ratedItemUsers, candidateUsers, overlap);

                if (similarity > MIN_SIMILARITY) {
                    topSimilarItems.add(new ItemSim(candidateItemId, similarity));
                }
            }

            // 取TopK相似物品
            topSimilarItems.sort((a, b) -> Double.compare(b.similarity, a.similarity));
            int limit = Math.min(TOP_K_SIMILAR_ITEMS, topSimilarItems.size());
            for (int i = 0; i < limit; i++) {
                ItemSim sim = topSimilarItems.get(i);
                // Ranking模式：score += sim * rating（直接用评分）
                candidateScores.merge(sim.itemId, sim.similarity * userRating, Double::sum);
                simSums.merge(sim.itemId, sim.similarity, Double::sum);
            }
        }

        if (candidateScores.isEmpty()) {
            return fallbackToPopularity(itemUsers, targetRatings, topN);
        }

        // 计算最终得分并排序
        List<Recommendation> result = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : candidateScores.entrySet()) {
            Long itemId = entry.getKey();
            double simSum = simSums.getOrDefault(itemId, 0.0);
            if (simSum > 0) {
                // Ranking模式：finalScore = score / simSum
                double finalScore = entry.getValue() / simSum;
                result.add(new Recommendation(itemId, finalScore));
            }
        }

        if (result.isEmpty()) {
            return fallbackToPopularity(itemUsers, targetRatings, topN);
        }

        return result.stream().sorted().limit(topN).collect(Collectors.toList());
    }

    /**
     * Cosine相似度计算（直接使用评分）
     */
    private double cosineSimilarity(Map<Long, Double> a, Map<Long, Double> b, int overlap) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (Map.Entry<Long, Double> entry : a.entrySet()) {
            Double other = b.get(entry.getKey());
            if (other != null) {
                double va = entry.getValue();
                double vb = other;
                dot += va * vb;
                normA += va * va;
                normB += vb * vb;
            }
        }

        if (normA == 0 || normB == 0) return 0.0;

        double sim = dot / (Math.sqrt(normA) * Math.sqrt(normB));
        // 较弱的shrinkage
        return sim * overlap / (overlap + 50.0);
    }

    public List<Recommendation> recommend(
            Map<Long, Map<Long, Double>> userItem,
            Map<Long, Map<Long, Double>> itemUsers,
            Long userId,
            int topN
    ) {
        return recommend(userItem, userId, topN);
    }

    private Map<Long, Map<Long, Double>> buildItemUsers(Map<Long, Map<Long, Double>> userItem) {
        Map<Long, Map<Long, Double>> itemUsers = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Double>> userEntry : userItem.entrySet()) {
            Long userId = userEntry.getKey();
            for (Map.Entry<Long, Double> ratingEntry : userEntry.getValue().entrySet()) {
                itemUsers.computeIfAbsent(ratingEntry.getKey(), k -> new HashMap<>())
                        .put(userId, ratingEntry.getValue());
            }
        }
        return itemUsers;
    }

    private List<Recommendation> fallbackToPopularity(Map<Long, Map<Long, Double>> itemUsers,
                                                       Map<Long, Double> targetRatings,
                                                       int topN) {
        Map<Long, double[]> itemStats = new HashMap<>();

        for (Map.Entry<Long, Map<Long, Double>> entry : itemUsers.entrySet()) {
            Long itemId = entry.getKey();
            for (Double score : entry.getValue().values()) {
                itemStats.computeIfAbsent(itemId, k -> new double[2]);
                itemStats.get(itemId)[0] += score;
                itemStats.get(itemId)[1] += 1;
            }
        }

        return itemStats.entrySet().stream()
                .filter(e -> !targetRatings.containsKey(e.getKey()))
                .map(e -> {
                    Long itemId = e.getKey();
                    double sum = e.getValue()[0];
                    double count = e.getValue()[1];
                    double score = (sum + GLOBAL_MEAN * 10) / (count + 10);
                    return new Recommendation(itemId, score);
                })
                .sorted()
                .limit(topN)
                .collect(Collectors.toList());
    }

    private static class ItemSim {
        final Long itemId;
        final double similarity;

        ItemSim(Long itemId, double similarity) {
            this.itemId = itemId;
            this.similarity = similarity;
        }
    }
}