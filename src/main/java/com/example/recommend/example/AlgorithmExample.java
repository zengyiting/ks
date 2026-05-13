package com.example.recommend.example;

import com.example.recommend.algo.*;

import java.util.*;

/**
 * 算法示例（独立于数据库）：构造小规模评分矩阵，演示两种推荐算法
 * 运行方式：mvn -q -DskipTests package && java -cp target/recommend-0.0.1-SNAPSHOT.jar com.example.recommend.example.AlgorithmExample
 */
public class AlgorithmExample {
    public static void main(String[] args) {
        Map<Long, Map<Long, Double>> userItem = new HashMap<>();
        userItem.put(1L, Map.of(101L, 5.0, 102L, 4.0, 201L, 3.0));
        userItem.put(2L, Map.of(101L, 4.0, 103L, 5.0, 202L, 4.0));
        userItem.put(3L, Map.of(102L, 5.0, 201L, 4.0, 203L, 4.0));
        userItem.put(4L, Map.of(101L, 2.0, 202L, 5.0, 203L, 3.0));

        int topN = 3;
        RecommenderStrategy userBased = new UserBasedCF();
        RecommenderStrategy itemBased = new ItemBasedCF();

        System.out.println("User-Based CF for user 1:");
        userBased.recommend(userItem, 1L, topN)
                .forEach(r -> System.out.printf("item=%d score=%.4f%n", r.getItemId(), r.getScore()));

        System.out.println("\nItem-Based CF for user 1:");
        itemBased.recommend(userItem, 1L, topN)
                .forEach(r -> System.out.printf("item=%d score=%.4f%n", r.getItemId(), r.getScore()));
    }
}

