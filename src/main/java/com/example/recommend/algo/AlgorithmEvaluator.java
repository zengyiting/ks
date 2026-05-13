package com.example.recommend.algo;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 算法评估器 - 直接在内存中评估协同过滤算法
 * 使用 ml-100k 数据集进行离线评估
 */
public class AlgorithmEvaluator {

    private static final int TOP_N = 10;
    private static final double TEST_RATIO = 0.2;
    private static final double RELEVANCE_THRESHOLD = 4.0;
    private static final int RANDOM_SEED = 42;

    /**
     * 评估结果记录
     */
    public static class EvaluationResult {
        public final String algorithm;
        public final double precisionAtK;
        public final double recallAtK;
        public final double ndcgAtK;
        public final double coverage;
        public final int evaluableUsers;

        public EvaluationResult(String algorithm, double precisionAtK, double recallAtK,
                               double ndcgAtK, double coverage, int evaluableUsers) {
            this.algorithm = algorithm;
            this.precisionAtK = precisionAtK;
            this.recallAtK = recallAtK;
            this.ndcgAtK = ndcgAtK;
            this.coverage = coverage;
            this.evaluableUsers = evaluableUsers;
        }

        @Override
        public String toString() {
            return String.format("%-12s | Precision@%d: %.4f | Recall@%d: %.4f | NDCG@%d: %.4f | Coverage: %.4f | Users: %d",
                    algorithm, TOP_N, precisionAtK, TOP_N, recallAtK, TOP_N, ndcgAtK, coverage, evaluableUsers);
        }
    }

    /**
     * 加载 ml-100k 数据集
     */
    public static Map<Long, Map<Long, Double>> loadMovieLens100k(String dataPath) throws IOException {
        Map<Long, Map<Long, Double>> userItem = new HashMap<>();
        Path path = Paths.get(dataPath);

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length >= 3) {
                    long userId = Long.parseLong(parts[0]);
                    long itemId = Long.parseLong(parts[1]);
                    double rating = Double.parseDouble(parts[2]);
                    userItem.computeIfAbsent(userId, k -> new HashMap<>()).put(itemId, rating);
                }
            }
        }

        int totalRatings = userItem.values().stream().mapToInt(Map::size).sum();
        System.out.printf("数据集加载完成：%d 用户，%d 物品，%d 条评分%n",
                userItem.size(),
                userItem.values().stream().flatMap(m -> m.keySet().stream()).distinct().count(),
                totalRatings);

        return userItem;
    }

    /**
     * 划分训练集和测试集
     */
    public static Map<String, Map<Long, Map<Long, Double>>> splitTrainTest(
            Map<Long, Map<Long, Double>> userItem, double testRatio) {

        Random random = new Random(RANDOM_SEED);
        Map<Long, Map<Long, Double>> train = new HashMap<>();
        Map<Long, Map<Long, Double>> test = new HashMap<>();

        for (Map.Entry<Long, Map<Long, Double>> entry : userItem.entrySet()) {
            Long userId = entry.getKey();
            Map<Long, Double> ratings = entry.getValue();

            train.put(userId, new HashMap<>());
            test.put(userId, new HashMap<>());

            List<Long> items = new ArrayList<>(ratings.keySet());
            Collections.shuffle(items, random);

            int testSize = (int) (items.size() * testRatio);

            for (int i = 0; i < items.size(); i++) {
                Long itemId = items.get(i);
                if (i < testSize) {
                    test.get(userId).put(itemId, ratings.get(itemId));
                } else {
                    train.get(userId).put(itemId, ratings.get(itemId));
                }
            }
        }

        int trainCount = train.values().stream().mapToInt(Map::size).sum();
        int testCount = test.values().stream().mapToInt(Map::size).sum();
        System.out.printf("训练集：%d 条评分，测试集：%d 条评分%n", trainCount, testCount);

        Map<String, Map<Long, Map<Long, Double>>> result = new HashMap<>();
        result.put("train", train);
        result.put("test", test);
        return result;
    }

    /**
     * 获取所有物品ID
     */
    public static Set<Long> getAllItems(Map<Long, Map<Long, Double>> userItem) {
        return userItem.values().stream()
                .flatMap(m -> m.keySet().stream())
                .collect(Collectors.toSet());
    }

    /**
     * 计算 Precision@K
     */
    public static double precisionAtK(List<Long> recommended, Set<Long> relevant, int k) {
        if (recommended.isEmpty()) return 0.0;
        List<Long> topK = recommended.size() > k ? recommended.subList(0, k) : recommended;
        int hits = 0;
        for (Long itemId : topK) {
            if (relevant.contains(itemId)) hits++;
        }
        return (double) hits / topK.size();
    }

    /**
     * 计算 Recall@K
     */
    public static double recallAtK(List<Long> recommended, Set<Long> relevant, int k) {
        if (relevant.isEmpty()) return 0.0;
        List<Long> topK = recommended.size() > k ? recommended.subList(0, k) : recommended;
        int hits = 0;
        for (Long itemId : topK) {
            if (relevant.contains(itemId)) hits++;
        }
        return (double) hits / relevant.size();
    }

    /**
     * 计算 NDCG@K
     */
    public static double ndcgAtK(List<Long> recommended, Set<Long> relevant, int k) {
        if (recommended.isEmpty()) return 0.0;

        List<Long> topK = recommended.size() > k ? recommended.subList(0, k) : recommended;

        // DCG
        double dcg = 0.0;
        for (int i = 0; i < topK.size(); i++) {
            if (relevant.contains(topK.get(i))) {
                dcg += 1.0 / Math.log(i + 2);
            }
        }

        // IDCG
        double idcg = 0.0;
        int idealSize = Math.min(k, relevant.size());
        for (int i = 0; i < idealSize; i++) {
            idcg += 1.0 / Math.log(i + 2);
        }

        return idcg > 0 ? dcg / idcg : 0.0;
    }

    /**
     * 评估单个算法
     */
    public static EvaluationResult evaluate(RecommenderStrategy algorithm, String name,
                                            Map<Long, Map<Long, Double>> train,
                                            Map<Long, Map<Long, Double>> test,
                                            Set<Long> allItems, int topN, double relevanceThreshold) {

        double totalPrecision = 0.0;
        double totalRecall = 0.0;
        double totalNdcg = 0.0;
        Set<Long> allRecommended = new HashSet<>();
        int evaluableUsers = 0;

        System.out.printf("\n正在评估 %s 算法...%n", name);

        for (Long userId : test.keySet()) {
            // 获取用户在测试集中的相关物品
            Set<Long> relevantItems = test.get(userId).entrySet().stream()
                    .filter(e -> e.getValue() >= relevanceThreshold)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            if (relevantItems.isEmpty()) continue;

            // 获取用户在训练集中的评分（用于推荐）
            Map<Long, Double> trainRatings = train.getOrDefault(userId, Collections.emptyMap());
            if (trainRatings.isEmpty()) continue;

            // 生成推荐
            List<Recommendation> recommendations = algorithm.recommend(train, userId, topN);

            // 使用正确的 getter 方法
            List<Long> recommendedItems = new ArrayList<>();
            for (Recommendation r : recommendations) {
                recommendedItems.add(r.getItemId());
            }

            if (recommendedItems.isEmpty()) continue;

            // 计算指标
            totalPrecision += precisionAtK(recommendedItems, relevantItems, topN);
            totalRecall += recallAtK(recommendedItems, relevantItems, topN);
            totalNdcg += ndcgAtK(recommendedItems, relevantItems, topN);

            // 更新覆盖率
            allRecommended.addAll(recommendedItems);
            evaluableUsers++;
        }

        double avgPrecision = evaluableUsers > 0 ? totalPrecision / evaluableUsers : 0.0;
        double avgRecall = evaluableUsers > 0 ? totalRecall / evaluableUsers : 0.0;
        double avgNdcg = evaluableUsers > 0 ? totalNdcg / evaluableUsers : 0.0;
        double coverage = allItems.isEmpty() ? 0.0 : (double) allRecommended.size() / allItems.size();

        System.out.printf("  评估完成！可评估用户: %d%n", evaluableUsers);
        System.out.printf("  Precision@%d: %.4f%n", topN, avgPrecision);
        System.out.printf("  Recall@%d: %.4f%n", topN, avgRecall);
        System.out.printf("  NDCG@%d: %.4f%n", topN, avgNdcg);
        System.out.printf("  Coverage: %.4f%n", coverage);

        return new EvaluationResult(name, avgPrecision, avgRecall, avgNdcg, coverage, evaluableUsers);
    }

    /**
     * 混合推荐算法实现
     */
    public static class HybridRecommender implements RecommenderStrategy {
        private final RecommenderStrategy userBased;
        private final RecommenderStrategy itemBased;

        public HybridRecommender(RecommenderStrategy userBased, RecommenderStrategy itemBased) {
            this.userBased = userBased;
            this.itemBased = itemBased;
        }

        @Override
        public List<Recommendation> recommend(Map<Long, Map<Long, Double>> userItemMatrix, Long userId, int topN) {
            List<Recommendation> userRecs = userBased.recommend(userItemMatrix, userId, topN);
            List<Recommendation> itemRecs = itemBased.recommend(userItemMatrix, userId, topN);

            // 合并推荐结果，去重并加权评分
            Map<Long, Double> combinedScores = new HashMap<>();

            // User-Based CF 权重 0.4
            for (Recommendation rec : userRecs) {
                combinedScores.merge(rec.getItemId(), rec.getScore() * 0.4, Double::sum);
            }

            // Item-Based CF 权重 0.6
            for (Recommendation rec : itemRecs) {
                combinedScores.merge(rec.getItemId(), rec.getScore() * 0.6, Double::sum);
            }

            // 转换为推荐列表并排序
            List<Recommendation> result = combinedScores.entrySet().stream()
                    .map(e -> new Recommendation(e.getKey(), e.getValue()))
                    .sorted()
                    .limit(topN)
                    .collect(Collectors.toList());

            return result;
        }
    }

    /**
     * 主方法 - 运行评估
     */
    public static void main(String[] args) {
        try {
            // 数据路径
            String dataPath = "docs/ml-100k/u.data";

            // 1. 加载数据集
            System.out.println("=" + "=".repeat(70));
            System.out.println("加载 MovieLens 100k 数据集");
            System.out.println("=" + "=".repeat(70));
            Map<Long, Map<Long, Double>> userItem = loadMovieLens100k(dataPath);

            // 2. 划分训练集和测试集
            System.out.println("\n" + "=".repeat(75));
            System.out.println("划分训练集和测试集 (测试比例: " + TEST_RATIO + ")");
            System.out.println("=" + "=".repeat(75));
            Map<String, Map<Long, Map<Long, Double>>> split = splitTrainTest(userItem, TEST_RATIO);
            Map<Long, Map<Long, Double>> train = split.get("train");
            Map<Long, Map<Long, Double>> test = split.get("test");
            Set<Long> allItems = getAllItems(userItem);

            // 3. 创建算法实例
            RecommenderStrategy userBasedCF = new UserBasedCF();
            RecommenderStrategy itemBasedCF = new ItemBasedCF();
            RecommenderStrategy hybridCF = new HybridRecommender(userBasedCF, itemBasedCF);

            // 4. 评估各算法
            System.out.println("\n" + "=".repeat(75));
            System.out.println("开始评估算法性能");
            System.out.println("=" + "=".repeat(75));

            List<EvaluationResult> results = new ArrayList<>();
            results.add(evaluate(userBasedCF, "User-Based CF", train, test, allItems, TOP_N, RELEVANCE_THRESHOLD));
            results.add(evaluate(itemBasedCF, "Item-Based CF", train, test, allItems, TOP_N, RELEVANCE_THRESHOLD));
            results.add(evaluate(hybridCF, "Hybrid", train, test, allItems, TOP_N, RELEVANCE_THRESHOLD));

            // 5. 输出汇总结果
            System.out.println("\n" + "=".repeat(100));
            System.out.println("算法评估结果汇总");
            System.out.println("=" + "=".repeat(100));
            System.out.printf("%-15s | %-12s | %-10s | %-10s | %-10s | %-8s%n",
                    "算法", "Precision@" + TOP_N, "Recall@" + TOP_N, "NDCG@" + TOP_N, "Coverage", "Users");
            System.out.println("-".repeat(100));
            for (EvaluationResult result : results) {
                System.out.printf("%-15s | %-12.4f | %-10.4f | %-10.4f | %-10.4f | %-8d%n",
                        result.algorithm, result.precisionAtK, result.recallAtK,
                        result.ndcgAtK, result.coverage, result.evaluableUsers);
            }
            System.out.println("=" + "=".repeat(100));

        } catch (IOException e) {
            System.err.println("加载数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
