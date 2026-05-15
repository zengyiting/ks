package com.example.recommend.algo;

import java.util.*;
import java.util.stream.Collectors;

public class UserBasedCF implements RecommenderStrategy {

    private static final double MIN_SIMILARITY = 0.0;
    private static final double GLOBAL_MEAN = 3.5;
    private static final int DEFAULT_NEIGHBORS = 30;
    private static final int MIN_OVERLAP = 2;

    @Override
    public List<Recommendation> recommend(Map<Long, Map<Long, Double>> userItem, Long userId, int topN) {
        if (userItem == null || userItem.isEmpty() || userId == null || topN <= 0) {
            return List.of();
        }

        Map<Long, Double> targetRatings = userItem.getOrDefault(userId, Collections.emptyMap());
        if (targetRatings.isEmpty()) {
            return fallbackToPopularity(userItem, targetRatings, topN);
        }

        List<Neighbor> neighbors = findNeighbors(userItem, targetRatings, userId);

        if (neighbors.isEmpty()) {
            return fallbackToPopularity(userItem, targetRatings, topN);
        }

        Map<Long, Double> predictions = predictRatings(neighbors, targetRatings);

        if (predictions.isEmpty()) {
            return fallbackToPopularity(userItem, targetRatings, topN);
        }

        return predictions.entrySet().stream()
                .map(e -> new Recommendation(e.getKey(), e.getValue()))
                .sorted()
                .limit(topN)
                .collect(Collectors.toList());
    }

    private List<Neighbor> findNeighbors(Map<Long, Map<Long, Double>> userItem,
                                         Map<Long, Double> targetRatings,
                                         Long userId) {
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
            if (overlap < MIN_OVERLAP) continue;

            double similarity = cosineSimilarity(targetRatings, otherRatings);

            if (similarity <= MIN_SIMILARITY) continue;

            neighbors.add(new Neighbor(otherId, similarity, otherRatings));
        }

        neighbors.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        int neighborLimit = Math.min(DEFAULT_NEIGHBORS, neighbors.size());
        return neighbors.subList(0, neighborLimit);
    }

    private double cosineSimilarity(Map<Long, Double> a, Map<Long, Double> b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (Map.Entry<Long, Double> entry : a.entrySet()) {
            Long itemId = entry.getKey();
            Double ratingB = b.get(itemId);
            if (ratingB != null) {
                dotProduct += entry.getValue() * ratingB;
                normA += entry.getValue() * entry.getValue();
                normB += ratingB * ratingB;
            }
        }

        if (normA == 0 || normB == 0) return 0.0;

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private Map<Long, Double> predictRatings(List<Neighbor> neighbors,
                                              Map<Long, Double> targetRatings) {
        Map<Long, Double> predictions = new HashMap<>();
        Map<Long, Double> weightSums = new HashMap<>();

        for (Neighbor neighbor : neighbors) {
            double sim = neighbor.similarity;
            if (sim <= 0) continue;

            for (Map.Entry<Long, Double> entry : neighbor.ratings.entrySet()) {
                Long itemId = entry.getKey();
                if (targetRatings.containsKey(itemId)) continue;

                double rating = entry.getValue();
                double weight = sim;

                predictions.merge(itemId, weight * rating, Double::sum);
                weightSums.merge(itemId, weight, Double::sum);
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

    private List<Recommendation> fallbackToPopularity(Map<Long, Map<Long, Double>> userItem,
                                                     Map<Long, Double> targetRatings,
                                                     int topN) {
        Map<Long, double[]> itemStats = new HashMap<>();

        for (Map<Long, Double> ratings : userItem.values()) {
            for (Map.Entry<Long, Double> entry : ratings.entrySet()) {
                Long itemId = entry.getKey();
                double rating = entry.getValue();
                itemStats.computeIfAbsent(itemId, k -> new double[2]);
                itemStats.get(itemId)[0] += rating;
                itemStats.get(itemId)[1] += 1;
            }
        }

        return itemStats.entrySet().stream()
                .filter(e -> !targetRatings.containsKey(e.getKey()))
                .map(e -> {
                    Long itemId = e.getKey();
                    double sum = e.getValue()[0];
                    double count = e.getValue()[1];
                    double avgRating = count > 0 ? sum / count : GLOBAL_MEAN;
                    double popularityScore = (avgRating - 1.0) / 4.0;
                    return new Recommendation(itemId, popularityScore);
                })
                .sorted()
                .limit(topN)
                .collect(Collectors.toList());
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