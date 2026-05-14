package com.example.recommend.algo;

import java.util.*;
import java.util.stream.Collectors;

public class ItemBasedCF implements RecommenderStrategy {

    private static final double MIN_SIMILARITY = 0.0;
    private static final int TOP_K_SIMILAR_ITEMS = 50;
    private static final int MIN_OVERLAP = 2;

    @Override
    public List<Recommendation> recommend(Map<Long, Map<Long, Double>> userItem, Long userId, int topN) {
        if (userItem == null || userItem.isEmpty() || userId == null || topN <= 0) {
            return List.of();
        }

        Map<Long, Double> targetRatings = userItem.getOrDefault(userId, Collections.emptyMap());
        if (targetRatings.isEmpty()) {
            return List.of();
        }

        Map<Long, Map<Long, Double>> itemUsers = buildItemUsers(userItem);
        if (itemUsers.isEmpty()) {
            return fallbackToPopularity(itemUsers, targetRatings, topN);
        }

        Map<Long, Double> predictions = predictRatings(targetRatings, itemUsers);

        if (predictions.isEmpty()) {
            return fallbackToPopularity(itemUsers, targetRatings, topN);
        }

        return predictions.entrySet().stream()
                .map(e -> new Recommendation(e.getKey(), e.getValue()))
                .sorted()
                .limit(topN)
                .collect(Collectors.toList());
    }

    public List<Recommendation> recommend(
            Map<Long, Map<Long, Double>> userItem,
            Map<Long, Map<Long, Double>> itemUsers,
            Long userId,
            int topN
    ) {
        if (userItem == null || userItem.isEmpty() || userId == null || topN <= 0) {
            return List.of();
        }

        Map<Long, Double> targetRatings = userItem.getOrDefault(userId, Collections.emptyMap());
        if (targetRatings.isEmpty()) {
            return List.of();
        }

        if (itemUsers == null || itemUsers.isEmpty()) {
            itemUsers = buildItemUsers(userItem);
        }

        if (itemUsers.isEmpty()) {
            return fallbackToPopularity(itemUsers, targetRatings, topN);
        }

        Map<Long, Double> predictions = predictRatings(targetRatings, itemUsers);

        if (predictions.isEmpty()) {
            return fallbackToPopularity(itemUsers, targetRatings, topN);
        }

        return predictions.entrySet().stream()
                .map(e -> new Recommendation(e.getKey(), e.getValue()))
                .sorted()
                .limit(topN)
                .collect(Collectors.toList());
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

    private Map<Long, Double> predictRatings(Map<Long, Double> targetRatings,
                                            Map<Long, Map<Long, Double>> itemUsers) {
        Map<Long, Double> predictions = new HashMap<>();
        Map<Long, Double> weightSums = new HashMap<>();

        for (Map.Entry<Long, Double> ratedEntry : targetRatings.entrySet()) {
            Long ratedItemId = ratedEntry.getKey();
            double userRating = ratedEntry.getValue();

            Map<Long, Double> ratedItemUsers = itemUsers.getOrDefault(ratedItemId, Collections.emptyMap());
            if (ratedItemUsers.isEmpty()) continue;

            for (Map.Entry<Long, Map<Long, Double>> entry : itemUsers.entrySet()) {
                Long candidateItemId = entry.getKey();
                if (targetRatings.containsKey(candidateItemId)) continue;

                Map<Long, Double> candidateUsers = entry.getValue();

                int overlap = 0;
                for (Long uid : ratedItemUsers.keySet()) {
                    if (candidateUsers.containsKey(uid)) {
                        overlap++;
                    }
                }
                if (overlap < MIN_OVERLAP) continue;

                double similarity = cosineSimilarity(ratedItemUsers, candidateUsers);
                if (similarity <= MIN_SIMILARITY) continue;

                double weight = similarity;
                predictions.merge(candidateItemId, weight * userRating, Double::sum);
                weightSums.merge(candidateItemId, weight, Double::sum);
            }
        }

        Map<Long, Double> result = new HashMap<>();
        for (Map.Entry<Long, Double> entry : predictions.entrySet()) {
            Long itemId = entry.getKey();
            double weightSum = weightSums.getOrDefault(itemId, 0.0);
            if (weightSum > 0) {
                result.put(itemId, entry.getValue() / weightSum);
            }
        }

        return result;
    }

    private double cosineSimilarity(Map<Long, Double> a, Map<Long, Double> b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (Map.Entry<Long, Double> entry : a.entrySet()) {
            Long userId = entry.getKey();
            Double ratingB = b.get(userId);
            if (ratingB != null) {
                dotProduct += entry.getValue() * ratingB;
                normA += entry.getValue() * entry.getValue();
                normB += ratingB * ratingB;
            }
        }

        if (normA == 0 || normB == 0) return 0.0;

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
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
                    double avgRating = count > 0 ? sum / count : 3.5;
                    double popularityScore = (avgRating - 1.0) / 4.0;
                    return new Recommendation(itemId, popularityScore);
                })
                .sorted()
                .limit(topN)
                .collect(Collectors.toList());
    }
}