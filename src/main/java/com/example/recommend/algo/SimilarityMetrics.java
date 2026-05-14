package com.example.recommend.algo;

import java.util.Map;

/**
 * 相似度计算工具类 - 优化版本
 * 拆分为User相似度和Item相似度，针对不同场景优化
 */
public final class SimilarityMetrics {
    private SimilarityMetrics() {}

    private static final double GLOBAL_MEAN = 3.5;
    private static final int MIN_OVERLAP = 3;

    public static int overlapCount(Map<Long, Double> a, Map<Long, Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0;
        int count = 0;
        Map<Long, Double> smaller = a.size() <= b.size() ? a : b;
        Map<Long, Double> larger = a.size() <= b.size() ? b : a;
        for (Long k : smaller.keySet()) {
            if (larger.containsKey(k)) {
                count++;
            }
        }
        return count;
    }

    /**
     * UserCF专用的Pearson相似度
     * 不使用shrinkage，因为UserCF用户评分重叠度较低，shrinkage会过度惩罚
     * 适用于用户-用户相似度计算
     */
    public static double userSimilarity(Map<Long, Double> a, Map<Long, Double> b) {
        int overlap = overlapCount(a, b);
        if (overlap < MIN_OVERLAP) return 0.0;

        double sumA = 0, sumB = 0, sumA2 = 0, sumB2 = 0, sumAB = 0;
        int n = 0;

        Map<Long, Double> smaller = a.size() <= b.size() ? a : b;
        Map<Long, Double> larger = a.size() <= b.size() ? b : a;

        for (Map.Entry<Long, Double> e : smaller.entrySet()) {
            Double other = larger.get(e.getKey());
            if (other != null) {
                double va = e.getValue();
                double vb = other;
                sumA += va;
                sumB += vb;
                sumA2 += va * va;
                sumB2 += vb * vb;
                sumAB += va * vb;
                n++;
            }
        }

        if (n < MIN_OVERLAP) return 0.0;

        double num = sumAB - (sumA * sumB / n);
        double den = Math.sqrt((sumA2 - (sumA * sumA / n)) * (sumB2 - (sumB * sumB / n)));

        if (den <= 1e-12) return 0.0;

        double sim = num / den;
        if (den <= 1e-12) return 0.0;

        // 温和shrinkage：sim * overlap / (overlap + 5)
        return sim * overlap / (overlap + 5.0);
    }

    /**
     * ItemCF专用的Adjusted Cosine相似度
     * 减去用户平均分：(r_ui - mean_u)
     * 适用于物品-物品相似度计算
     * 使用shrinkage：sim * overlap / (overlap + 8)
     */
    public static double itemSimilarity(Map<Long, Double> a, Map<Long, Double> b) {
        int overlap = overlapCount(a, b);
        if (overlap < 3) return 0.0;

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        int n = 0;

        Map<Long, Double> smaller = a.size() <= b.size() ? a : b;
        Map<Long, Double> larger = a.size() <= b.size() ? b : a;

        for (Map.Entry<Long, Double> e : smaller.entrySet()) {
            Double other = larger.get(e.getKey());
            if (other != null) {
                double va = e.getValue() - GLOBAL_MEAN;
                double vb = other - GLOBAL_MEAN;
                dot += va * vb;
                normA += va * va;
                normB += vb * vb;
                n++;
            }
        }

        if (n < 3 || normA == 0 || normB == 0) return 0.0;

        double sim = dot / (Math.sqrt(normA) * Math.sqrt(normB));
        return sim * overlap / (overlap + 8.0);
    }

    /**
     * 优化的相似度计算
     * 综合使用多种相似度计算方法，根据数据特点自动选择最优方案
     */
    public static double optimizedSimilarity(Map<Long, Double> a, Map<Long, Double> b) {
        int overlap = overlapCount(a, b);
        if (overlap < MIN_OVERLAP) return 0.0;

        double sim = 0.0;

        sim = pearson(a, b);
        if (Math.abs(sim) < 1e-6) {
            sim = adjustedCosine(a, b);
        }
        if (Math.abs(sim) < 1e-6) {
            sim = cosine(a, b);
        }

        return confidenceWeighted(sim, overlap);
    }

    private static double confidenceWeighted(double similarity, int overlap) {
        if (overlap <= 0) return 0.0;
        double confidence = Math.log1p(overlap) / Math.log1p(100);
        return similarity * confidence;
    }

    /**
     * 标准余弦相似度
     */
    public static double cosine(Map<Long, Double> a, Map<Long, Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        Map<Long, Double> smaller = a.size() <= b.size() ? a : b;
        Map<Long, Double> larger = a.size() <= b.size() ? b : a;

        for (Map.Entry<Long, Double> e : smaller.entrySet()) {
            Double other = larger.get(e.getKey());
            if (other != null) {
                dot += e.getValue() * other;
            }
        }

        if (dot == 0.0) return 0.0;

        for (double va : a.values()) normA += va * va;
        for (double vb : b.values()) normB += vb * vb;

        if (normA == 0 || normB == 0) return 0.0;

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 调整余弦相似度（基于全局均值）
     */
    public static double adjustedCosine(Map<Long, Double> a, Map<Long, Double> b) {
        return adjustedCosine(a, b, GLOBAL_MEAN);
    }

    /**
     * 调整余弦相似度（基于指定均值）
     */
    public static double adjustedCosine(Map<Long, Double> a, Map<Long, Double> b, double mean) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        int overlap = 0;

        Map<Long, Double> smaller = a.size() <= b.size() ? a : b;
        Map<Long, Double> larger = a.size() <= b.size() ? b : a;

        for (Map.Entry<Long, Double> e : smaller.entrySet()) {
            Double other = larger.get(e.getKey());
            if (other != null) {
                double va = e.getValue() - mean;
                double vb = other - mean;
                dot += va * vb;
                normA += va * va;
                normB += vb * vb;
                overlap++;
            }
        }

        if (overlap < 2 || normA == 0 || normB == 0) return 0.0;

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 皮尔逊相关系数
     */
    public static double pearson(Map<Long, Double> a, Map<Long, Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;

        double sumA = 0, sumB = 0, sumA2 = 0, sumB2 = 0, sumAB = 0;
        int n = 0;

        Map<Long, Double> smaller = a.size() <= b.size() ? a : b;
        Map<Long, Double> larger = a.size() <= b.size() ? b : a;

        for (Map.Entry<Long, Double> e : smaller.entrySet()) {
            Double other = larger.get(e.getKey());
            if (other != null) {
                double va = e.getValue();
                double vb = other;
                sumA += va;
                sumB += vb;
                sumA2 += va * va;
                sumB2 += vb * vb;
                sumAB += va * vb;
                n++;
            }
        }

        if (n < 2) return 0.0;

        double num = sumAB - (sumA * sumB / n);
        double den = Math.sqrt((sumA2 - (sumA * sumA / n)) * (sumB2 - (sumB * sumB / n)));

        if (den <= 1e-12) return 0.0;

        return num / den;
    }
}
