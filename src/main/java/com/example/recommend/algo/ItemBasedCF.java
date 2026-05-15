package com.example.recommend.algo;

import java.util.*;
import java.util.stream.Collectors;

public class ItemBasedCF implements RecommenderStrategy {

    private static final double MIN_SIMILARITY = 0.0;
    private static final int TOP_K_SIMILAR_ITEMS = 40;
    private static final int MIN_OVERLAP = 3;
    private static final double GLOBAL_MEAN = 3.5;

    private Map<Long, Map<Long, Double>> similarityCache;
    private int lastUserCount = -1;
    private int lastItemCount = -1;
    private boolean cacheBuilt = false;

    @Override
    public List<Recommendation> recommend(Map<Long, Map<Long, Double>> userItem, Long userId, int topN) {
        if (userItem == null || userItem.isEmpty() || userId == null || topN <= 0) {
            return List.of();
        }

        Map<Long, Double> targetRatings = userItem.getOrDefault(userId, Collections.emptyMap());
        if (targetRatings.isEmpty()) {
            return fallbackToPopularity(userItem, topN);
        }

        Map<Long, Map<Long, Double>> itemUsers = buildItemUsers(userItem);
        if (itemUsers.isEmpty()) {
            return fallbackToPopularity(userItem, topN);
        }

        int currentUserCount = userItem.size();
        int currentItemCount = itemUsers.size();
        if (!cacheBuilt || lastUserCount != currentUserCount || lastItemCount != currentItemCount) {
            buildSimilarityCache(itemUsers);
            lastUserCount = currentUserCount;
            lastItemCount = currentItemCount;
        }

        Map<Long, Double> predictions = predictRatings(targetRatings, itemUsers);

        if (predictions.isEmpty()) {
            return fallbackToPopularity(userItem, topN);
        }

        return predictions.entrySet().stream()
                .map(e -> new Recommendation(e.getKey(), e.getValue()))
                .sorted()
                .limit(topN)
                .collect(Collectors.toList());
    }

    private void buildSimilarityCache(Map<Long, Map<Long, Double>> itemUsers) {
        similarityCache = new HashMap<>();
        List<Long> items = new ArrayList<>(itemUsers.keySet());

        System.out.println("Building item similarity cache...");

        for (int i = 0; i < items.size(); i++) {
            Long item1 = items.get(i);
            Map<Long, Double> users1 = itemUsers.get(item1);

            for (int j = i + 1; j < items.size(); j++) {
                Long item2 = items.get(j);
                Map<Long, Double> users2 = itemUsers.get(item2);

                double similarity = SimilarityMetrics.itemSimilarity(users1, users2);
                if (similarity > MIN_SIMILARITY) {
                    similarityCache.computeIfAbsent(item1, k -> new HashMap<>()).put(item2, similarity);
                    similarityCache.computeIfAbsent(item2, k -> new HashMap<>()).put(item1, similarity);
                }
            }
        }

        cacheBuilt = true;
        System.out.println("Similarity cache built: " + similarityCache.size() + " items");
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

        double userAvgRating = targetRatings.values().stream().mapToDouble(Double::doubleValue).average().orElse(GLOBAL_MEAN);

        for (Map.Entry<Long, Double> entry : targetRatings.entrySet()) {
            Long ratedItem = entry.getKey();
            double userRating = entry.getValue();

            Map<Long, Double> similarItems = similarityCache.getOrDefault(ratedItem, Collections.emptyMap());

            List<Map.Entry<Long, Double>> topSimilarItems = similarItems.entrySet().stream()
                    .filter(e -> !targetRatings.containsKey(e.getKey()))
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(TOP_K_SIMILAR_ITEMS)
                    .collect(Collectors.toList());

            for (Map.Entry<Long, Double> simEntry : topSimilarItems) {
                Long candidateItemId = simEntry.getKey();
                double similarity = simEntry.getValue();

                double weight = similarity;
                double centeredRating = userRating - userAvgRating;
                double predictionContribution = weight * (centeredRating + GLOBAL_MEAN);
                predictions.merge(candidateItemId, predictionContribution, Double::sum);
                weightSums.merge(candidateItemId, weight, Double::sum);
            }
        }

        Map<Long, Double> result = new HashMap<>();
        for (Map.Entry<Long, Double> entry : predictions.entrySet()) {
            Long itemId = entry.getKey();
            double weightSum = weightSums.getOrDefault(itemId, 0.0);
            if (weightSum > 0) {
                double predictedRating = entry.getValue() / weightSum;
                predictedRating = Math.max(1.0, Math.min(5.0, predictedRating));
                result.put(itemId, predictedRating);
            }
        }

        return result;
    }

    private List<Recommendation> fallbackToPopularity(Map<Long, Map<Long, Double>> userItem, int topN) {
        Map<Long, double[]> itemStats = new HashMap<>();

        for (Map<Long, Double> ratings : userItem.values()) {
            for (Map.Entry<Long, Double> ratingEntry : ratings.entrySet()) {
                Long itemId = ratingEntry.getKey();
                double rating = ratingEntry.getValue();
                itemStats.computeIfAbsent(itemId, k -> new double[2]);
                itemStats.get(itemId)[0] += rating;
                itemStats.get(itemId)[1] += 1;
            }
        }

        return itemStats.entrySet().stream()
                .map(e -> {
                    Long itemId = e.getKey();
                    double sum = e.getValue()[0];
                    double count = e.getValue()[1];
                    double avgRating = count > 0 ? sum / count : GLOBAL_MEAN;
                    double popularityScore = (avgRating - 1.0) / 4.0 * Math.log1p(count);
                    return new Recommendation(itemId, popularityScore);
                })
                .sorted()
                .limit(topN)
                .collect(Collectors.toList());
    }

    public void resetCache() {
        cacheBuilt = false;
        similarityCache = null;
        lastItemUsers = null;
    }
}
