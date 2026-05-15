package com.example.recommend.algo;

import com.example.recommend.service.AlgorithmType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class MovieLensEvaluator {

    public static void main(String[] args) throws IOException {
        String dataPath = "docs/ml-100k/u.data";
        double sampleRatio = 0.3;

        System.out.println("=== MovieLens Evaluator ===");
        System.out.println("Loading data from: " + dataPath);
        System.out.println("Sample ratio: " + (sampleRatio * 100) + "%");

        Map<Long, Map<Long, Double>> matrix = loadRatings(dataPath, sampleRatio);
        Map<Long, String> categoryMap = new HashMap<>();

        System.out.println("Loaded: " + matrix.size() + " users");
        System.out.println("Total items: " + countItems(matrix));

        DatasetSplit split = splitDataset(matrix, 0.2, 4.0);

        System.out.println("\n=== Starting Evaluation ===");
        System.out.println("Train users: " + split.trainSize());
        System.out.println("Test users: " + split.testSize());

        UserBasedCF userBasedCF = new UserBasedCF();
        ItemBasedCF itemBasedCF = new ItemBasedCF();

        List<EvaluationResult> results = new ArrayList<>();
        results.add(evaluateAlgorithm("User-Based CF", AlgorithmType.USER_BASED, split, categoryMap, 10, userBasedCF, itemBasedCF));
        results.add(evaluateAlgorithm("Item-Based CF", AlgorithmType.ITEM_BASED, split, categoryMap, 10, userBasedCF, itemBasedCF));
        results.add(evaluateAlgorithm("Behavior-Based", AlgorithmType.BEHAVIOR_BASED, split, categoryMap, 10, userBasedCF, itemBasedCF));
        results.add(evaluateAlgorithm("Hybrid", AlgorithmType.HYBRID, split, categoryMap, 10, userBasedCF, itemBasedCF));

        generateHtmlReport(results, "reports/offline-eval/movielens-evaluation-report.html");
        System.out.println("\nReport saved to: reports/offline-eval/movielens-evaluation-report.html");
    }

    private static EvaluationResult evaluateAlgorithm(
            String name,
            AlgorithmType type,
            DatasetSplit split,
            Map<Long, String> categoryMap,
            int topK,
            UserBasedCF userBasedCF,
            ItemBasedCF itemBasedCF
    ) {
        System.out.println("\nEvaluating: " + name);
        long start = System.currentTimeMillis();

        double precision = 0.0;
        double recall = 0.0;
        double ndcg = 0.0;
        Set<Long> coveredItems = new HashSet<>();
        int users = 0;

        for (Map.Entry<Long, Set<Long>> entry : split.testRelevant().entrySet()) {
            Long userId = entry.getKey();
            Set<Long> relevant = new HashSet<>(entry.getValue());
            if (relevant.isEmpty()) continue;

            Set<Long> trainItems = split.trainMatrix().getOrDefault(userId, Collections.emptyMap()).keySet();
            relevant.removeAll(trainItems);
            if (relevant.isEmpty()) continue;

            List<Recommendation> recs = recommend(type, split.trainMatrix(), userId, topK, categoryMap, userBasedCF, itemBasedCF);
            List<Long> topItems = recs.stream().limit(topK).map(Recommendation::getItemId).collect(Collectors.toList());
            coveredItems.addAll(topItems);

            int hit = 0;
            for (Long itemId : topItems) {
                if (relevant.contains(itemId)) hit++;
            }
            precision += ((double) hit) / topK;
            recall += ((double) hit) / relevant.size();
            ndcg += ndcgAtK(topItems, relevant, topK);
            users++;
        }

        double coverage = ((double) coveredItems.size()) / split.candidateItems().size();
        long time = System.currentTimeMillis() - start;

        double avgPrecision = users == 0 ? 0.0 : precision / users;
        double avgRecall = users == 0 ? 0.0 : recall / users;
        double avgNdcg = users == 0 ? 0.0 : ndcg / users;

        System.out.printf("  Precision@%d: %.4f\n", topK, avgPrecision);
        System.out.printf("  Recall@%d: %.4f\n", topK, avgRecall);
        System.out.printf("  NDCG@%d: %.4f\n", topK, avgNdcg);
        System.out.printf("  Coverage: %.4f\n", coverage);
        System.out.printf("  Evaluated users: %d\n", users);
        System.out.printf("  Time: %d ms\n", time);

        return new EvaluationResult(name, avgPrecision, avgRecall, avgNdcg, coverage, users, time);
    }

    private static void generateHtmlReport(List<EvaluationResult> results, String filePath) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("""
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>推荐系统算法评估报告</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { font-family: 'Microsoft YaHei', sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; padding: 40px 20px; }
                    .container { max-width: 1200px; margin: 0 auto; }
                    .header { text-align: center; color: white; margin-bottom: 40px; }
                    .header h1 { font-size: 36px; margin-bottom: 10px; text-shadow: 2px 2px 4px rgba(0,0,0,0.3); }
                    .card { background: white; border-radius: 16px; box-shadow: 0 10px 40px rgba(0,0,0,0.15); padding: 30px; margin-bottom: 30px; }
                    .card-title { font-size: 24px; color: #333; margin-bottom: 20px; padding-bottom: 15px; border-bottom: 2px solid #eee; }
                    .table-container { overflow-x: auto; }
                    table { width: 100%; border-collapse: collapse; margin-top: 20px; }
                    th, td { padding: 14px; text-align: center; border-bottom: 1px solid #eee; }
                    th { background: #f8f9fa; font-weight: 600; color: #333; }
                    tr:hover { background: #f8f9fa; }
                    .highlight { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }
                    .chart-container { height: 400px; margin: 20px 0; }
                    .bar-chart { display: flex; align-items: flex-end; justify-content: space-around; height: 100%; padding: 20px; }
                    .bar-group { display: flex; gap: 8px; align-items: flex-end; }
                    .bar { width: 35px; border-radius: 6px 6px 0 0; transition: height 0.5s ease; position: relative; }
                    .bar.precision { background: #667eea; }
                    .bar.recall { background: #f093fb; }
                    .bar.ndcg { background: #4facfe; }
                    .bar.coverage { background: #43e97b; }
                    .bar-label { position: absolute; top: -25px; left: 50%; transform: translateX(-50%); font-size: 10px; white-space: nowrap; color: #333; }
                    .algo-label { text-align: center; margin-top: 10px; font-size: 13px; color: #666; font-weight: 500; }
                    .legend { display: flex; justify-content: center; gap: 30px; margin-top: 20px; }
                    .legend-item { display: flex; align-items: center; gap: 8px; }
                    .legend-color { width: 20px; height: 20px; border-radius: 4px; }
                    .footer { text-align: center; color: white; margin-top: 40px; opacity: 0.8; font-size: 14px; }
                    .metrics-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; margin-bottom: 30px; }
                    .metric-card { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 12px; padding: 20px; color: white; text-align: center; }
                    .metric-value { font-size: 32px; font-weight: bold; margin-bottom: 5px; }
                    .metric-label { font-size: 14px; opacity: 0.9; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>📊 推荐系统算法评估报告</h1>
                        <p>基于MovieLens 100K数据集</p>
                    </div>
            """);

        html.append("<div class=\"card\"><h2 class=\"card-title\">📈 评估结果对比</h2><div class=\"table-container\"><table><thead><tr><th>算法名称</th><th>精确率@10</th><th>召回率@10</th><th>NDCG@10</th><th>覆盖率</th><th>评估用户数</th><th>耗时(ms)</th></tr></thead><tbody>");

        double maxPrecision = results.stream().mapToDouble(r -> r.precision).max().orElse(0);
        double maxRecall = results.stream().mapToDouble(r -> r.recall).max().orElse(0);
        double maxNdcg = results.stream().mapToDouble(r -> r.ndcg).max().orElse(0);
        double maxCoverage = results.stream().mapToDouble(r -> r.coverage).max().orElse(0);

        for (EvaluationResult r : results) {
            boolean isBest = r.precision == maxPrecision;
            html.append("<tr").append(isBest ? " class=\"highlight\"" : "").append(">");
            html.append("<td><strong>").append(r.name).append("</strong></td>");
            html.append(String.format("<td>%.4f</td>", r.precision));
            html.append(String.format("<td>%.4f</td>", r.recall));
            html.append(String.format("<td>%.4f</td>", r.ndcg));
            html.append(String.format("<td>%.4f</td>", r.coverage));
            html.append("<td>").append(r.users).append("</td>");
            html.append("<td>").append(r.time).append("</td>");
            html.append("</tr>");
        }

        html.append("</tbody></table></div></div>");

        html.append("<div class=\"card\"><h2 class=\"card-title\">📊 可视化对比</h2><div class=\"chart-container\"><div class=\"bar-chart\">");

        for (EvaluationResult r : results) {
            html.append("<div style=\"display: flex; flex-direction: column; align-items: center; flex: 1;\"><div class=\"bar-group\">");

            double pHeight = (r.precision / maxPrecision) * 300;
            double rHeight = (r.recall / maxRecall) * 300;
            double nHeight = (r.ndcg / maxNdcg) * 300;
            double cHeight = (r.coverage / maxCoverage) * 300;

            html.append(String.format("<div class=\"bar precision\" style=\"height: %.1fpx\" title=\"精确率: %.4f\"><span class=\"bar-label\">%.3f</span></div>", pHeight, r.precision, r.precision));
            html.append(String.format("<div class=\"bar recall\" style=\"height: %.1fpx\" title=\"召回率: %.4f\"><span class=\"bar-label\">%.3f</span></div>", rHeight, r.recall, r.recall));
            html.append(String.format("<div class=\"bar ndcg\" style=\"height: %.1fpx\" title=\"NDCG: %.4f\"><span class=\"bar-label\">%.3f</span></div>", nHeight, r.ndcg, r.ndcg));
            html.append(String.format("<div class=\"bar coverage\" style=\"height: %.1fpx\" title=\"覆盖率: %.4f\"><span class=\"bar-label\">%.3f</span></div>", cHeight, r.coverage, r.coverage));

            html.append("</div><div class=\"algo-label\">").append(r.name).append("</div></div>");
        }

        html.append("</div></div><div class=\"legend\"><div class=\"legend-item\"><div class=\"legend-color\" style=\"background: #667eea;\"></div><span>精确率</span></div><div class=\"legend-item\"><div class=\"legend-color\" style=\"background: #f093fb;\"></div><span>召回率</span></div><div class=\"legend-item\"><div class=\"legend-color\" style=\"background: #4facfe;\"></div><span>NDCG</span></div><div class=\"legend-item\"><div class=\"legend-color\" style=\"background: #43e97b;\"></div><span>覆盖率</span></div></div></div>");

        html.append("""
                <div class="footer">
                    <p>📅 生成时间：""").append(java.time.LocalDateTime.now()).append("""
                     | 📁 项目：基于协同过滤的推荐系统</p>
                </div>
            </div>
            </body>
            </html>
            """);

        Files.createDirectories(Paths.get(filePath).getParent());
        Files.writeString(Paths.get(filePath), html.toString());
    }

    private record EvaluationResult(
        String name,
        double precision,
        double recall,
        double ndcg,
        double coverage,
        int users,
        long time
    ) {}


    private static List<Recommendation> recommend(
            AlgorithmType type,
            Map<Long, Map<Long, Double>> trainMatrix,
            Long userId,
            int topK,
            Map<Long, String> categoryMap,
            UserBasedCF userBasedCF,
            ItemBasedCF itemBasedCF
    ) {
        return switch (type) {
            case USER_BASED -> {
                List<Recommendation> recs = userBasedCF.recommend(trainMatrix, userId, topK);
                yield mergeWithPopularFallback(recs, trainMatrix, userId, topK);
            }
            case ITEM_BASED -> {
                List<Recommendation> recs = itemBasedCF.recommend(trainMatrix, userId, topK);
                yield mergeWithPopularFallback(recs, trainMatrix, userId, topK);
            }
            case BEHAVIOR_BASED -> {
                List<Recommendation> recs = behaviorBasedRecommend(trainMatrix, userId, topK);
                yield mergeWithPopularFallback(recs, trainMatrix, userId, topK);
            }
            case HYBRID -> blendHybrid(trainMatrix, userId, topK, categoryMap, userBasedCF, itemBasedCF);
            default -> Collections.emptyList();
        };
    }

    private static Map<Long, Map<Long, Double>> loadRatings(String path, double sampleRatio) throws IOException {
        Map<Long, Map<Long, Double>> matrix = new HashMap<>();
        Random random = new Random(42);
        Files.lines(Paths.get(path))
                .forEach(line -> {
                    if (random.nextDouble() >= sampleRatio) {
                        return;
                    }
                    String[] parts = line.split("\t");
                    long userId = Long.parseLong(parts[0]);
                    long itemId = Long.parseLong(parts[1]);
                    double rating = Double.parseDouble(parts[2]);
                    matrix.computeIfAbsent(userId, k -> new HashMap<>()).put(itemId, rating);
                });
        return matrix;
    }

    private static int countItems(Map<Long, Map<Long, Double>> matrix) {
        Set<Long> items = new HashSet<>();
        for (Map<Long, Double> userRatings : matrix.values()) {
            items.addAll(userRatings.keySet());
        }
        return items.size();
    }

    private static DatasetSplit splitDataset(
            Map<Long, Map<Long, Double>> matrix,
            double testRatio,
            double relevanceThreshold
    ) {
        Map<Long, Map<Long, Double>> trainMatrix = new HashMap<>();
        Map<Long, Set<Long>> testRelevant = new HashMap<>();
        Set<Long> candidateItems = new HashSet<>();
        Random random = new Random(42);

        for (Map.Entry<Long, Map<Long, Double>> entry : matrix.entrySet()) {
            Long userId = entry.getKey();
            Map<Long, Double> ratings = entry.getValue();
            Map<Long, Double> trainRatings = new HashMap<>();
            Set<Long> testItems = new HashSet<>();

            for (Map.Entry<Long, Double> rating : ratings.entrySet()) {
                long itemId = rating.getKey();
                double score = rating.getValue();
                candidateItems.add(itemId);

                if (random.nextDouble() < testRatio) {
                    if (score >= relevanceThreshold) {
                        testItems.add(itemId);
                    }
                } else {
                    trainRatings.put(itemId, score);
                }
            }

            if (!trainRatings.isEmpty()) {
                trainMatrix.put(userId, trainRatings);
            }
            if (!testItems.isEmpty()) {
                testRelevant.put(userId, testItems);
            }
        }

        return new DatasetSplit(trainMatrix, testRelevant, candidateItems, trainMatrix.size(), testRelevant.size());
    }

    private static double ndcgAtK(List<Long> topItems, Set<Long> relevant, int k) {
        double dcg = 0.0;
        double idcg = 0.0;

        for (int i = 0; i < Math.min(topItems.size(), k); i++) {
            if (relevant.contains(topItems.get(i))) {
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
            }
        }

        int idealHits = Math.min(relevant.size(), k);
        for (int i = 0; i < idealHits; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }

        return idcg == 0 ? 0 : dcg / idcg;
    }

    private static List<Recommendation> mergeWithPopularFallback(
            List<Recommendation> primary,
            Map<Long, Map<Long, Double>> matrix,
            Long userId,
            int topN
    ) {
        Map<Long, Recommendation> merged = new LinkedHashMap<>();
        for (Recommendation r : primary) {
            merged.putIfAbsent(r.getItemId(), r);
        }
        if (merged.size() >= topN) {
            return merged.values().stream().sorted().limit(topN).collect(Collectors.toList());
        }
        Set<Long> excluded = new HashSet<>(merged.keySet());
        excluded.addAll(matrix.getOrDefault(userId, Collections.emptyMap()).keySet());
        for (Recommendation r : popularFallback(matrix, topN - merged.size(), excluded)) {
            merged.putIfAbsent(r.getItemId(), r);
            if (merged.size() >= topN) break;
        }
        return merged.values().stream().sorted().limit(topN).collect(Collectors.toList());
    }

    private static List<Recommendation> popularFallback(
            Map<Long, Map<Long, Double>> matrix,
            int need,
            Set<Long> excluded
    ) {
        if (need <= 0) return Collections.emptyList();
        Map<Long, double[]> stats = new HashMap<>();
        for (Map<Long, Double> userRatings : matrix.values()) {
            for (Map.Entry<Long, Double> e : userRatings.entrySet()) {
                double[] acc = stats.computeIfAbsent(e.getKey(), k -> new double[2]);
                acc[0] += e.getValue();
                acc[1] += 1;
            }
        }
        List<Recommendation> ranked = new ArrayList<>();
        for (Map.Entry<Long, double[]> e : stats.entrySet()) {
            if (excluded.contains(e.getKey())) continue;
            double avg = e.getValue()[0] / e.getValue()[1];
            double score = avg * Math.log(1 + e.getValue()[1]);
            ranked.add(new Recommendation(e.getKey(), score));
        }
        return ranked.stream().sorted().limit(need).collect(Collectors.toList());
    }

    private static List<Recommendation> behaviorBasedRecommend(
            Map<Long, Map<Long, Double>> trainMatrix,
            Long userId,
            int topK
    ) {
        Map<Long, Double> userRatings = trainMatrix.getOrDefault(userId, Collections.emptyMap());
        if (userRatings.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Double> itemScores = new HashMap<>();
        Map<Long, Set<Long>> itemUsers = buildItemUserSet(trainMatrix);
        Map<Long, Double> itemPopularity = popularityScoreMap(trainMatrix, topK * 3, userRatings.keySet());

        for (Map.Entry<Long, Double> ratedEntry : userRatings.entrySet()) {
            Long ratedItem = ratedEntry.getKey();
            double rating = ratedEntry.getValue();

            Set<Long> coUsers = itemUsers.getOrDefault(ratedItem, Collections.emptySet());
            if (coUsers.isEmpty()) continue;

            double behaviorIntensity = 0.2 + 0.8 * (rating / 5.0);

            for (Map.Entry<Long, Set<Long>> entry : itemUsers.entrySet()) {
                Long candidate = entry.getKey();
                if (userRatings.containsKey(candidate)) continue;

                Set<Long> candidateUsers = entry.getValue();
                int coCount = 0;
                for (Long u : coUsers) {
                    if (candidateUsers.contains(u)) coCount++;
                }

                if (coCount > 0) {
                    double similarity = (double) coCount / Math.sqrt(coUsers.size() * candidateUsers.size());
                    double popScore = itemPopularity.getOrDefault(candidate, 0.0);
                    double finalScore = behaviorIntensity * similarity * (0.6 + 0.4 * popScore);
                    itemScores.merge(candidate, finalScore, Double::sum);
                }
            }
        }

        return itemScores.entrySet().stream()
                .map(e -> new Recommendation(e.getKey(), e.getValue()))
                .sorted()
                .limit(topK)
                .collect(Collectors.toList());
    }

    private static List<Recommendation> blendHybrid(
            Map<Long, Map<Long, Double>> trainMatrix,
            Long userId,
            int topN,
            Map<Long, String> categoryMap,
            UserBasedCF userBasedCF,
            ItemBasedCF itemBasedCF
    ) {
        Set<Long> rated = trainMatrix.getOrDefault(userId, Collections.emptyMap()).keySet();
        int poolSize = Math.max(topN * 5, 20);

        List<Recommendation> itemRecs = itemBasedCF.recommend(trainMatrix, userId, poolSize);
        List<Recommendation> userRecs = userBasedCF.recommend(trainMatrix, userId, poolSize);

        Map<Long, Double> itemRankScore = rankScoreMap(itemRecs);
        Map<Long, Double> userRankScore = rankScoreMap(userRecs);
        Map<Long, Double> popScore = popularityScoreMap(trainMatrix, poolSize, rated);
        Map<Long, Double> associationScore = associationScoreMap(trainMatrix, userId, poolSize);

        Set<Long> candidates = new HashSet<>();
        candidates.addAll(itemRankScore.keySet());
        candidates.addAll(userRankScore.keySet());
        candidates.addAll(popScore.keySet());
        candidates.addAll(associationScore.keySet());
        candidates.removeAll(rated);

        List<Recommendation> merged = new ArrayList<>();
        for (Long itemId : candidates) {
            double score = 0.35 * itemRankScore.getOrDefault(itemId, 0.0)
                    + 0.25 * userRankScore.getOrDefault(itemId, 0.0)
                    + 0.20 * popScore.getOrDefault(itemId, 0.0)
                    + 0.20 * associationScore.getOrDefault(itemId, 0.0);
            if (score > 1e-12) {
                merged.add(new Recommendation(itemId, score));
            }
        }

        return mergeWithPopularFallback(merged.stream().sorted().collect(Collectors.toList()), trainMatrix, userId, topN);
    }

    private static Map<Long, Set<Long>> buildItemUserSet(Map<Long, Map<Long, Double>> matrix) {
        Map<Long, Set<Long>> itemUserMatrix = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Double>> userEntry : matrix.entrySet()) {
            Long userId = userEntry.getKey();
            for (Long itemId : userEntry.getValue().keySet()) {
                itemUserMatrix.computeIfAbsent(itemId, k -> new HashSet<>()).add(userId);
            }
        }
        return itemUserMatrix;
    }

    private static Map<Long, Double> rankScoreMap(List<Recommendation> recs) {
        Map<Long, Double> map = new HashMap<>();
        for (int i = 0; i < recs.size(); i++) {
            Recommendation r = recs.get(i);
            map.put(r.getItemId(), Math.max(map.getOrDefault(r.getItemId(), 0.0), 1.0 / (1.0 + i)));
        }
        return map;
    }

    private static Map<Long, Double> popularityScoreMap(
            Map<Long, Map<Long, Double>> matrix,
            int limit,
            Set<Long> excluded
    ) {
        List<Recommendation> popular = popularFallback(matrix, limit, excluded);
        if (popular.isEmpty()) return Map.of();
        double max = popular.stream().mapToDouble(Recommendation::getScore).max().orElse(1.0);
        Map<Long, Double> map = new HashMap<>();
        for (Recommendation r : popular) {
            map.put(r.getItemId(), r.getScore() / max);
        }
        return map;
    }

    private static Map<Long, Double> associationScoreMap(
            Map<Long, Map<Long, Double>> trainMatrix,
            Long userId,
            int limit
    ) {
        Map<Long, Double> userRatings = trainMatrix.getOrDefault(userId, Collections.emptyMap());
        if (userRatings.isEmpty()) return Map.of();
        Map<Long, Set<Long>> itemUsers = buildItemUserSet(trainMatrix);
        Map<Long, Double> raw = new HashMap<>();
        for (Long ratedItem : userRatings.keySet()) {
            Set<Long> usersI = itemUsers.getOrDefault(ratedItem, Collections.emptySet());
            if (usersI.isEmpty()) continue;
            double userWeight = Math.max(0.0, userRatings.getOrDefault(ratedItem, 0.0) / 5.0);
            for (Map.Entry<Long, Set<Long>> entry : itemUsers.entrySet()) {
                Long candidate = entry.getKey();
                if (userRatings.containsKey(candidate)) continue;
                Set<Long> usersJ = entry.getValue();
                int co = overlapCount(usersI, usersJ);
                if (co == 0) continue;
                double sim = co / Math.sqrt((double) usersI.size() * usersJ.size());
                raw.merge(candidate, sim * userWeight, Double::sum);
            }
        }
        return normalizeAndLimit(raw, limit);
    }

    private static int overlapCount(Set<Long> a, Set<Long> b) {
        int count = 0;
        for (Long id : a) {
            if (b.contains(id)) count++;
        }
        return count;
    }

    private static Map<Long, Double> normalizeAndLimit(Map<Long, Double> raw, int limit) {
        if (raw.isEmpty()) return Map.of();
        double max = raw.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        return raw.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue() / max), Map::putAll);
    }

    private record DatasetSplit(
            Map<Long, Map<Long, Double>> trainMatrix,
            Map<Long, Set<Long>> testRelevant,
            Set<Long> candidateItems,
            int trainSize,
            int testSize
    ) {
        public int evaluableUsers() {
            return testRelevant.size();
        }
    }
}
