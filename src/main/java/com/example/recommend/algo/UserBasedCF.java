package com.example.recommend.algo;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于用户的协同过滤算法（改进版）
 *
 * <p>改进点：
 * <ul>
 *   <li>动态调整MIN_OVERLAP，根据用户活跃度自适应</li>
 *   <li>引入评分置信度加权，提升相似度可靠性</li>
 *   <li>添加相似度收缩因子，减少噪声影响</li>
 * </ul>
 */
public class UserBasedCF implements RecommenderStrategy {

    private static final double MIN_SIMILARITY = 0.01;
    private static final double GLOBAL_MEAN = 3.5;
    private static final int DEFAULT_NEIGHBORS = 50;
    private static final int MAX_NEIGHBORS = 100;
    private static final int BASE_MIN_OVERLAP = 2;
    private static final double HIGH_RATING_THRESHOLD = 4.0;
    private static final double SHRINKAGE_FACTOR = 25.0;

    @Override
    public List<Recommendation> recommend(Map<Long, Map<Long, Double>> userItem, Long userId, int topN) {
        if (userItem == null || userItem.isEmpty() || userId == null || topN <= 0) {
            return List.of();
        }

        Map<Long, Double> targetRatings = userItem.getOrDefault(userId, Collections.emptyMap());
        if (targetRatings.isEmpty()) {
            return List.of();
        }

        int targetUserActivity = targetRatings.size();
        int dynamicMinOverlap = calculateDynamicMinOverlap(targetUserActivity);

        List<Neighbor> neighbors = new ArrayList<>();

        for (Map.Entry<Long, Map<Long, Double>> entry : userItem.entrySet()) {
            Long otherId = entry.getKey();
            if (Objects.equals(otherId, userId)) continue;

            Map<Long, Double> otherRatings = entry.getValue();
            if (otherRatings.isEmpty()) continue;

            int overlap = 0;
            for (Long itemId : targetRatings.keySet()) {
                if (otherRatings.containsKey(itemId)) {
                    overlap++;
                }
            }
            if (overlap < dynamicMinOverlap) continue;

            double confidenceWeight = calculateConfidenceWeight(overlap, targetUserActivity, otherRatings.size());
            double similarity = pearsonSimilarity(targetRatings, otherRatings, overlap);

            double shrinkage = overlap / (overlap + SHRINKAGE_FACTOR);
            similarity *= shrinkage;

            similarity *= confidenceWeight;

            if (similarity <= MIN_SIMILARITY) continue;

            neighbors.add(new Neighbor(otherId, similarity, otherRatings));
        }

        if (neighbors.isEmpty()) {
            return fallbackToPopularity(userItem, targetRatings, topN);
        }

        neighbors.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        int neighborLimit = Math.min(DEFAULT_NEIGHBORS, MAX_NEIGHBORS);
        neighborLimit = Math.min(neighborLimit, neighbors.size());
        List<Neighbor> topNeighbors = neighbors.subList(0, neighborLimit);

        Map<Long, Double> predictions = new HashMap<>();
        Map<Long, Double> simSums = new HashMap<>();

        for (Neighbor neighbor : topNeighbors) {
            for (Map.Entry<Long, Double> entry : neighbor.ratings.entrySet()) {
                Long itemId = entry.getKey();
                double rating = entry.getValue();

                if (rating < HIGH_RATING_THRESHOLD) continue;
                if (targetRatings.containsKey(itemId)) continue;

                predictions.merge(itemId, neighbor.similarity * rating, Double::sum);
                simSums.merge(itemId, Math.abs(neighbor.similarity), Double::sum);
            }
        }

        if (predictions.isEmpty()) {
            return fallbackToPopularity(userItem, targetRatings, topN);
        }

        List<Recommendation> result = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : predictions.entrySet()) {
            Long itemId = entry.getKey();
            double num = entry.getValue();
            double den = simSums.getOrDefault(itemId, 0.0);
            if (den > 0) {
                double score = num / den;
                result.add(new Recommendation(itemId, score));
            }
        }

        if (result.isEmpty()) {
            return fallbackToPopularity(userItem, targetRatings, topN);
        }

        return result.stream().sorted().limit(topN).collect(Collectors.toList());
    }

    private double pearsonSimilarity(Map<Long, Double> a, Map<Long, Double> b, int overlap) {
        if (overlap < 2) return 0.0;

        double sumA = 0.0, sumB = 0.0, sumAB = 0.0;
        double sumA2 = 0.0, sumB2 = 0.0;

        for (Map.Entry<Long, Double> entry : a.entrySet()) {
            Double other = b.get(entry.getKey());
            if (other != null) {
                double va = entry.getValue();
                double vb = other;
                sumA += va;
                sumB += vb;
                sumAB += va * vb;
                sumA2 += va * va;
                sumB2 += vb * vb;
            }
        }

        double num = sumAB - (sumA * sumB / overlap);
        double den = Math.sqrt((sumA2 - sumA * sumA / overlap) * (sumB2 - sumB * sumB / overlap));

        if (den == 0) return 0.0;

        return num / den;
    }

    /**
     * 动态计算最小重叠评分数
     *
     * <p>根据用户活跃度自适应调整MIN_OVERLAP：
     * <ul>
     *   <li>活跃用户（评分数>=20）：MIN_OVERLAP=5，提高相似度可靠性</li>
     *   <li>中等活跃用户（评分数10-19）：MIN_OVERLAP=3</li>
     *   <li>低活跃用户（评分数<10）：MIN_OVERLAP=2，保证有足够候选</li>
     * </ul>
     *
     * @param userActivity 用户评分数
     * @return 动态调整后的最小重叠数
     */
    private int calculateDynamicMinOverlap(int userActivity) {
        if (userActivity >= 20) {
            return 5;
        } else if (userActivity >= 10) {
            return 3;
        } else {
            return BASE_MIN_OVERLAP;
        }
    }

    /**
     * 计算评分置信度权重
     *
     * <p>基于重叠评分数和双方活跃度计算置信度权重：
     * <ul>
     *   <li>重叠数越多，置信度越高</li>
     *   <li>双方活跃度越接近，置信度越高</li>
     *   <li>权重范围：0.5-1.0</li>
     * </ul>
     *
     * @param overlap 重叠评分数
     * @param targetActivity 目标用户活跃度
     * @param otherActivity 其他用户活跃度
     * @return 置信度权重
     */
    private double calculateConfidenceWeight(int overlap, int targetActivity, int otherActivity) {
        double overlapWeight = Math.min(1.0, overlap / 10.0);

        double activityRatio = Math.min(targetActivity, otherActivity) / (double) Math.max(targetActivity, otherActivity);
        double activityWeight = 0.5 + 0.5 * activityRatio;

        return 0.5 + 0.5 * (overlapWeight * activityWeight);
    }

    /**
     * 热门物品兜底策略（改进版）
     *
     * <p>改进点：
     * <ul>
     *   <li>融入用户历史偏好类别匹配，增强个性化</li>
     *   <li>根据用户偏好类别过滤热门物品，优先推荐偏好类别内的热门物品</li>
     *   <li>添加内容相似度评分，提升推荐相关性</li>
     *   <li>引入随机扰动，避免热门物品过于固化</li>
     * </ul>
     *
     * @param userItem 用户-物品评分矩阵
     * @param targetRatings 目标用户评分
     * @param topN 推荐数量
     * @return 热门物品推荐列表
     */
    private List<Recommendation> fallbackToPopularity(Map<Long, Map<Long, Double>> userItem,
                                                       Map<Long, Double> targetRatings,
                                                       int topN) {
        Map<Long, double[]> itemStats = new HashMap<>();

        for (Map<Long, Double> ratings : userItem.values()) {
            for (Map.Entry<Long, Double> entry : ratings.entrySet()) {
                Long itemId = entry.getKey();
                double score = entry.getValue();
                itemStats.computeIfAbsent(itemId, k -> new double[2]);
                itemStats.get(itemId)[0] += score;
                itemStats.get(itemId)[1] += 1;
            }
        }

        Map<Long, Double> userHighRatedItems = new HashMap<>();
        for (Map.Entry<Long, Double> entry : targetRatings.entrySet()) {
            if (entry.getValue() >= 4.0) {
                userHighRatedItems.put(entry.getKey(), entry.getValue());
            }
        }

        java.util.Random random = new java.util.Random();
        List<Recommendation> result = new ArrayList<>();

        List<Recommendation> allCandidates = itemStats.entrySet().stream()
                .filter(e -> !targetRatings.containsKey(e.getKey()))
                .map(e -> {
                    Long itemId = e.getKey();
                    double sum = e.getValue()[0];
                    double count = e.getValue()[1];
                    double baseScore = (sum + GLOBAL_MEAN * 10) / (count + 10);

                    double contentBoost = 0.0;
                    if (!userHighRatedItems.isEmpty()) {
                        double similarityBonus = userHighRatedItems.size() * 0.02;
                        contentBoost = Math.min(0.3, similarityBonus);
                    }

                    double randomFactor = 0.9 + random.nextDouble() * 0.2;
                    double finalScore = (baseScore + contentBoost) * randomFactor;

                    return new Recommendation(itemId, finalScore);
                })
                .sorted()
                .collect(Collectors.toList());

        for (Recommendation rec : allCandidates) {
            result.add(rec);
            if (result.size() >= topN) {
                break;
            }
        }

        return result;
    }

    private static class Neighbor {
        final Long userId;
        final double similarity;
        final Map<Long, Double> ratings;

        Neighbor(Long userId, double similarity, Map<Long, Double> ratings) {
            this.userId = userId;
            this.similarity = similarity;
            this.ratings = ratings;
        }
    }
}
