package com.example.recommend.algo;

import java.util.*;
import java.util.stream.Collectors;

public class ItemBasedCF implements RecommenderStrategy {

    private static final double MIN_SIMILARITY = 0.001;
    private static final int TOP_K_SIMILAR_ITEMS = 30;

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

        Map<Long, Double> predictions = predictRatings(targetRatings, itemUsers, userItem);

        if (predictions.isEmpty()) {
            return fallbackToPopularity(userItem, topN);
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
            return fallbackToPopularity(userItem, topN);
        }

        if (itemUsers == null || itemUsers.isEmpty()) {
            itemUsers = buildItemUsers(userItem);
        }

        if (itemUsers.isEmpty()) {
            return fallbackToPopularity(userItem, topN);
        }

        Map<Long, Double> predictions = predictRatings(targetRatings, itemUsers, userItem);

        if (predictions.isEmpty()) {
            return fallbackToPopularity(userItem, topN);
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
                                            Map<Long, Map<Long, Double>> itemUsers,
                                            Map<Long, Map<Long, Double>> userItem) {
        Map<Long, Double> predictions = new HashMap<>();
        Map<Long, Double> weightSums = new HashMap<>();

        double userAvgRating = targetRatings.values().stream().mapToDouble(Double::doubleValue).average().orElse(3.5);

        for (Map.Entry<Long, Double> ratedEntry : targetRatings.entrySet()) {
            Long ratedItemId = ratedEntry.getKey();
            double userRating = ratedEntry.getValue();
            double ratingDeviation = userRating - userAvgRating;

            Map<Long, Double> ratedItemUsers = itemUsers.getOrDefault(ratedItemId, Collections.emptyMap());
            if (ratedItemUsers.isEmpty()) continue;

            Map<Long, Double> similarities = computeSimilarities(ratedItemId, ratedItemUsers, itemUsers, targetRatings.keySet());

            for (Map.Entry<Long, Double> simEntry : similarities.entrySet()) {
                Long candidateItemId = simEntry.getKey();
                double similarity = simEntry.getValue();

                double weight = similarity;
                double predictionContribution = weight * (ratingDeviation + userAvgRating);
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

    private Map<Long, Double> computeSimilarities(Long sourceItem,
                                                  Map<Long, Double> sourceUsers,
                                                  Map<Long, Map<Long, Double>> itemUsers,
                                                  Set<Long> excludeItems) {
        Map<Long, Double> similarities = new HashMap<>();

        int sourceSize = sourceUsers.size();
        if (sourceSize == 0) return similarities;

        for (Map.Entry<Long, Map<Long, Double>> entry : itemUsers.entrySet()) {
            Long candidateItem = entry.getKey();
            if (candidateItem.equals(sourceItem) || excludeItems.contains(candidateItem)) {
                continue;
            }

            Map<Long, Double> candidateUsers = entry.getValue();
            int candidateSize = candidateUsers.size();

            int overlap = 0;
            double dotProduct = 0.0;
            double normSource = 0.0;
            double normCandidate = 0.0;

            for (Map.Entry<Long, Double> sourceEntry : sourceUsers.entrySet()) {
                Long userId = sourceEntry.getKey();
                Double candidateRating = candidateUsers.get(userId);
                
                if (candidateRating != null) {
                    overlap++;
                    double sourceRating = sourceEntry.getValue();
                    dotProduct += sourceRating * candidateRating;
                    normSource += sourceRating * sourceRating;
                    normCandidate += candidateRating * candidateRating;
                }
            }

            if (overlap < 1) continue;

            double similarity = 0.0;
            if (normSource > 0 && normCandidate > 0) {
                similarity = dotProduct / (Math.sqrt(normSource) * Math.sqrt(normCandidate));
            }

            if (similarity > MIN_SIMILARITY) {
                similarities.put(candidateItem, similarity);
            }
        }

        return similarities.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(TOP_K_SIMILAR_ITEMS)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
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
                    double avgRating = count > 0 ? sum / count : 3.5;
                    double popularityScore = (avgRating / 5.0) * Math.log(1 + count);
                    return new Recommendation(itemId, popularityScore);
                })
                .sorted()
                .limit(topN)
                .collect(Collectors.toList());
    }
}
