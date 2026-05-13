package com.example.recommend.service;

import com.example.recommend.repository.ItemRepository;
import com.example.recommend.repository.RatingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 完整离线评估测试 - 评估优化后的Java算法
 */
@SpringBootTest
class FullOfflineEvaluationTest {

    @Autowired
    private OfflineEvaluationService evaluationService;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Test
    void evaluateAllAlgorithmsWithMl1m() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  ml-1m 完整数据集离线评估（优化版Java算法）");
        System.out.println("  优化项：调整余弦相似度、置信度加权、自适应邻居数量");
        System.out.println("=".repeat(70));

        int k = 10;
        double testRatio = 0.2;
        double relevanceThreshold = 4.0;

        System.out.println("\n评估参数:");
        System.out.println("  Top-K: " + k);
        System.out.println("  测试集比例: " + testRatio);
        System.out.println("  相关性阈值: " + relevanceThreshold);

        System.out.println("\n开始评估...");
        long startTime = System.currentTimeMillis();

        OfflineEvaluationService.EvaluationReport report = evaluationService.evaluate(k, testRatio, relevanceThreshold);

        long endTime = System.currentTimeMillis();
        double elapsed = (endTime - startTime) / 1000.0;

        System.out.println("\n" + "=".repeat(70));
        System.out.println("                    评估结果汇总");
        System.out.println("=".repeat(70));
        System.out.printf("%-15s %-12s %-12s %-12s %-12s%n",
                "算法", "精确率@10", "召回率@10", "NDCG@10", "覆盖率");
        System.out.println("-" + "-".repeat(69));

        for (Map.Entry<String, OfflineEvaluationService.AlgorithmMetrics> entry : report.metrics().entrySet()) {
            String algorithm = entry.getKey();
            OfflineEvaluationService.AlgorithmMetrics metrics = entry.getValue();
            System.out.printf("%-15s %-12.6f %-12.6f %-12.6f %-12.6f%n",
                    algorithm,
                    metrics.precisionAtK(),
                    metrics.recallAtK(),
                    metrics.ndcgAtK(),
                    metrics.coverage());
        }

        System.out.println("=".repeat(70));
        System.out.println("\n评估统计:");
        System.out.println("  可评估用户数: " + report.evaluableUsers());
        System.out.println("  训练集大小: " + report.trainSize());
        System.out.println("  测试集大小: " + report.testSize());
        System.out.println("  评估耗时: " + String.format("%.2f秒 (%.2f分钟)", elapsed, elapsed / 60));

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String reportName = "eval_java_optimized_" + timestamp + ".json";
        System.out.println("\n结果已保存至 reports/offline-eval/" + reportName);
    }

    @Test
    void evaluateWithSample() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  ml-1m 采样数据集离线评估（优化版Java算法）");
        System.out.println("  优化项：调整余弦相似度、置信度加权、自适应邻居数量");
        System.out.println("=".repeat(70));

        int k = 10;
        double testRatio = 0.2;
        double relevanceThreshold = 4.0;
        int sampleSize = 943;

        System.out.println("\n评估参数:");
        System.out.println("  Top-K: " + k);
        System.out.println("  测试集比例: " + testRatio);
        System.out.println("  相关性阈值: " + relevanceThreshold);
        System.out.println("  采样用户数: " + sampleSize);

        System.out.println("\n开始评估...");
        long startTime = System.currentTimeMillis();

        OfflineEvaluationService.EvaluationReport report = evaluationService.evaluateWithSample(k, testRatio, relevanceThreshold, sampleSize);

        long endTime = System.currentTimeMillis();
        double elapsed = (endTime - startTime) / 1000.0;

        System.out.println("\n" + "=".repeat(70));
        System.out.println("                    评估结果汇总");
        System.out.println("=".repeat(70));
        System.out.printf("%-15s %-12s %-12s %-12s %-12s%n",
                "算法", "精确率@10", "召回率@10", "NDCG@10", "覆盖率");
        System.out.println("-" + "-".repeat(69));

        for (Map.Entry<String, OfflineEvaluationService.AlgorithmMetrics> entry : report.metrics().entrySet()) {
            String algorithm = entry.getKey();
            OfflineEvaluationService.AlgorithmMetrics metrics = entry.getValue();
            System.out.printf("%-15s %-12.6f %-12.6f %-12.6f %-12.6f%n",
                    algorithm,
                    metrics.precisionAtK(),
                    metrics.recallAtK(),
                    metrics.ndcgAtK(),
                    metrics.coverage());
        }

        System.out.println("=".repeat(70));
        System.out.println("\n评估统计:");
        System.out.println("  可评估用户数: " + report.evaluableUsers());
        System.out.println("  训练集大小: " + report.trainSize());
        System.out.println("  测试集大小: " + report.testSize());
        System.out.println("  评估耗时: " + String.format("%.2f秒 (%.2f分钟)", elapsed, elapsed / 60));

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String reportName = "eval_java_sample_" + timestamp + ".json";
        System.out.println("\n结果已保存至 reports/offline-eval/" + reportName);
    }
}
