package com.example.recommend.algo;

import java.util.List;
import java.util.Map;

/**
 * 推荐算法策略接口
 * 输入：user-item 评分矩阵；输出：给定用户的 Top-N 推荐
 */
public interface RecommenderStrategy {
    List<Recommendation> recommend(Map<Long, Map<Long, Double>> userItemRatings, Long userId, int topN);
}

