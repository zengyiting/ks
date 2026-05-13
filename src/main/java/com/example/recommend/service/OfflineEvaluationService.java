package com.example.recommend.service;

import com.example.recommend.algo.ItemBasedCF;
import com.example.recommend.algo.Recommendation;
import com.example.recommend.algo.RecommenderStrategy;
import com.example.recommend.algo.UserBasedCF;
import com.example.recommend.model.Item;
import com.example.recommend.repository.ItemRepository;
import com.example.recommend.repository.RatingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.Instant;
import java.time.Duration;

/**
 * 离线评估服务类
 *
 * <p>提供推荐算法的离线评估功能，支持多种算法类型的性能对比分析：
 * <ul>
 *   <li>基于用户的协同过滤（User-Based CF）</li>
 *   <li>基于物品的协同过滤（Item-Based CF）</li>
 *   <li>混合推荐算法（Hybrid）</li>
 * </ul>
 * 评估指标包括精确率、召回率、NDCG和覆盖率。
 */
@Service
public class OfflineEvaluationService {
    /** 评分数据访问层 */
    private final RatingRepository ratingRepository;

    /** 商品数据访问层 */
    private final ItemRepository itemRepository;

    /** 基于用户的协同过滤策略 */
    private final RecommenderStrategy userBasedCF = new UserBasedCF();

    /** 基于物品的协同过滤策略 */
    private final RecommenderStrategy itemBasedCF = new ItemBasedCF();

    /**
     * 构造函数，注入依赖
     *
     * @param ratingRepository 评分仓库
     * @param itemRepository 商品仓库
     */
    public OfflineEvaluationService(RatingRepository ratingRepository, ItemRepository itemRepository) {
        this.ratingRepository = ratingRepository;
        this.itemRepository = itemRepository;
    }

    /**
     * 执行离线评估
     *
     * <p>对多种推荐算法进行离线评估，计算各项性能指标。
     * 评估过程包括数据集划分、算法执行和指标计算。
     *
     * @param topK Top-K推荐的K值，范围1-100
     * @param testRatio 测试集比例，范围0.0-1.0，默认0.2
     * @param relevanceThreshold 相关性阈值，用于判断测试集中的相关物品，范围0.0-5.0，默认4.0
     * @return 包含各算法评估指标的评估报告
     */
    @Transactional(readOnly = true)
    public EvaluationReport evaluate(int topK, double testRatio, double relevanceThreshold) {
        int k = Math.max(1, Math.min(topK, 100));
        double ratio = normalizeRatio(testRatio);
        double threshold = normalizeThreshold(relevanceThreshold);
        DatasetSplit split = splitDataset(ratio, threshold);

        System.out.println("\n开始评估 4 个算法 (USER_BASED, ITEM_BASED, HYBRID, BEHAVIOR_BASED)...");
        long evalStart = System.currentTimeMillis();

        AlgorithmMetrics userMetrics = evaluateAlgorithm(AlgorithmType.USER_BASED, split, k);
        System.out.println("  -> USER_BASED 完成, Precision: " + String.format("%.4f", userMetrics.precisionAtK()));

        AlgorithmMetrics itemMetrics = evaluateAlgorithm(AlgorithmType.ITEM_BASED, split, k);
        System.out.println("  -> ITEM_BASED 完成, Precision: " + String.format("%.4f", itemMetrics.precisionAtK()));

        AlgorithmMetrics hybridMetrics = evaluateAlgorithm(AlgorithmType.HYBRID, split, k);
        System.out.println("  -> HYBRID 完成, Precision: " + String.format("%.4f", hybridMetrics.precisionAtK()));

        long totalTime = System.currentTimeMillis() - evalStart;
        System.out.println("\n全部算法评估完成! 总耗时: " + (totalTime / 1000) + "秒");

        Map<String, AlgorithmMetrics> metrics = new LinkedHashMap<>();
        metrics.put("user", userMetrics);
        metrics.put("item", itemMetrics);
        metrics.put("hybrid", hybridMetrics);

        return new EvaluationReport(
                k,
                ratio,
                threshold,
                split.trainSize,
                split.testSize,
                split.evaluableUsers(),
                metrics
        );
    }

    @Transactional(readOnly = true)
    public EvaluationReport evaluateWithSample(int topK, double testRatio, double relevanceThreshold, int sampleSize) {
        int k = Math.max(1, Math.min(topK, 100));
        double ratio = normalizeRatio(testRatio);
        double threshold = normalizeThreshold(relevanceThreshold);
        DatasetSplit split = splitDatasetWithSample(ratio, threshold, sampleSize);

        System.out.println("\n开始评估 3 个算法 (USER_BASED, ITEM_BASED, HYBRID)...");
        System.out.println("采样用户数: " + sampleSize + ", 实际评估: " + split.evaluableUsers());
        long evalStart = System.currentTimeMillis();

        AlgorithmMetrics userMetrics = evaluateAlgorithm(AlgorithmType.USER_BASED, split, k);
        System.out.println("  -> USER_BASED 完成, Precision: " + String.format("%.4f", userMetrics.precisionAtK()));

        AlgorithmMetrics itemMetrics = evaluateAlgorithm(AlgorithmType.ITEM_BASED, split, k);
        System.out.println("  -> ITEM_BASED 完成, Precision: " + String.format("%.4f", itemMetrics.precisionAtK()));

        AlgorithmMetrics hybridMetrics = evaluateAlgorithm(AlgorithmType.HYBRID, split, k);
        System.out.println("  -> HYBRID 完成, Precision: " + String.format("%.4f", hybridMetrics.precisionAtK()));

        long totalTime = System.currentTimeMillis() - evalStart;
        System.out.println("\n全部算法评估完成! 总耗时: " + (totalTime / 1000) + "秒");

        Map<String, AlgorithmMetrics> metrics = new LinkedHashMap<>();
        metrics.put("user", userMetrics);
        metrics.put("item", itemMetrics);
        metrics.put("hybrid", hybridMetrics);

        return new EvaluationReport(
                k,
                ratio,
                threshold,
                split.trainSize,
                split.testSize,
                split.evaluableUsers(),
                metrics
        );
    }

    /**
     * 评估单个算法的性能指标
     *
     * <p>对指定算法在测试集上进行评估，计算精确率、召回率、NDCG和覆盖率。
     *
     * @param type 算法类型
     * @param split 数据集划分结果
     * @param topK Top-K推荐的K值
     * @return 算法的各项性能指标
     */
    private AlgorithmMetrics evaluateAlgorithm(AlgorithmType type, DatasetSplit split, int topK) {
        if (split.evaluableUsers() == 0 || split.candidateItems.isEmpty()) {
            return new AlgorithmMetrics(0.0, 0.0, 0.0, 0.0, 0);
        }
        double precision = 0.0;
        double recall = 0.0;
        double ndcg = 0.0;
        Set<Long> coveredItems = new HashSet<>();
        int users = 0;
        int totalUsers = split.testRelevant.size();
        int logInterval = Math.max(100, totalUsers / 100);

        System.out.println("    [" + type + "] 开始评估 " + totalUsers + " 用户...");
        long startTime = System.currentTimeMillis();

        for (Map.Entry<Long, Set<Long>> entry : split.testRelevant.entrySet()) {
            Long userId = entry.getKey();
            Set<Long> rawRelevant = entry.getValue();
            if (rawRelevant == null || rawRelevant.isEmpty()) {
                continue;
            }
            // 跳过那些已在训练集中出现的测试项（避免重复交互对评估造成误导）
            Set<Long> relevant = new HashSet<>(rawRelevant);
            Set<Long> trainItems = split.trainMatrix.getOrDefault(userId, Collections.emptyMap()).keySet();
            relevant.removeAll(trainItems);
            if (relevant.isEmpty()) {
                // 如果去重后没有可评估的相关项，则跳过该用户
                continue;
            }
            List<Recommendation> recs = recommend(type, split.trainMatrix, userId, topK);
            List<Long> topItems = recs.stream().limit(topK).map(Recommendation::getItemId).toList();
            coveredItems.addAll(topItems);

            int hit = 0;
            for (Long itemId : topItems) {
                if (relevant.contains(itemId)) {
                    hit++;
                }
            }
            precision += ((double) hit) / topK;
            recall += ((double) hit) / relevant.size();
            ndcg += ndcgAtK(topItems, relevant, topK);
            users++;
            if (users % logInterval == 0) {
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.println("    [" + type + "] 进度: " + users + "/" + totalUsers +
                    " (" + (users * 100 / totalUsers) + "%) - 耗时: " + (elapsed / 1000) + "秒");
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("    [" + type + "] 完成! 评估用户: " + users + ", 耗时: " + (elapsed / 1000) + "秒");

        if (users == 0) {
            return new AlgorithmMetrics(0.0, 0.0, 0.0, 0.0, 0);
        }
        double coverage = ((double) coveredItems.size()) / split.candidateItems.size();
        return new AlgorithmMetrics(
                precision / users,
                recall / users,
                ndcg / users,
                coverage,
                users
        );
    }

    /**
     * 根据算法类型生成推荐
     *
     * @param type 算法类型
     * @param trainMatrix 训练集用户-物品评分矩阵
     * @param userId 目标用户ID
     * @param topK Top-K推荐的K值
     * @return 推荐列表
     */
    private List<Recommendation> recommend(
            AlgorithmType type,
            Map<Long, Map<Long, Double>> trainMatrix,
            Long userId,
            int topK
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
            case BEHAVIOR_BASED -> itemBasedCF.recommend(trainMatrix, userId, topK);
            case HYBRID -> blendHybridRecommendations(trainMatrix, userId, topK);
        };
    }

    /**
     * 构建用户-物品衰减权重矩阵（离线评估使用）
     */
    private Map<Long, Map<Long, Double>> buildUserItemDecayMap() {
        Map<Long, Map<Long, Double>> map = new HashMap<>();
        Instant now = Instant.now();
        for (RatingRepository.UserItemRatedAtView row : ratingRepository.findAllUserItemRatedAt()) {
            Long userId = row.getUserId();
            Long itemId = row.getItemId();
            Instant ratedAt = row.getRatedAt();
            if (userId == null || itemId == null) continue;
            map.computeIfAbsent(userId, ignored -> new HashMap<>()).put(itemId, decayWeight(now, ratedAt));
        }
        return map;
    }

    private Map<Long, Map<Long, Double>> applyDecayToMatrix(
            Map<Long, Map<Long, Double>> matrix,
            Map<Long, Map<Long, Double>> decayMap
    ) {
        if (matrix == null || matrix.isEmpty()) return Map.of();
        Map<Long, Map<Long, Double>> out = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Double>> e : matrix.entrySet()) {
            Long userId = e.getKey();
            Map<Long, Double> row = new HashMap<>();
            Map<Long, Double> decays = decayMap == null ? Map.of() : decayMap.getOrDefault(userId, Map.of());
            for (Map.Entry<Long, Double> r : e.getValue().entrySet()) {
                double decay = decays.getOrDefault(r.getKey(), 1.0);
                row.put(r.getKey(), r.getValue() * decay);
            }
            if (!row.isEmpty()) out.put(userId, row);
        }
        return out;
    }

    private double decayWeight(Instant now, Instant ratedAt) {
        if (ratedAt == null) return 1.0;
        long days = Math.max(0L, Duration.between(ratedAt, now).toDays());
        return Math.pow(0.5, ((double) days) / 30.0);
    }

    /**
     * 合并推荐结果并应用热门物品回退策略
     *
     * <p>将主要推荐结果与热门物品结合，确保推荐数量达到要求。
     *
     * @param primary 主要推荐列表
     * @param matrix 用户-物品评分矩阵
     * @param userId 目标用户ID
     * @param topN 需要的推荐数量
     * @return 合并后的推荐列表
     */
    private List<Recommendation> mergeWithPopularFallback(
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
            return merged.values().stream().sorted().limit(topN).toList();
        }
        Set<Long> excluded = new HashSet<>(merged.keySet());
        excluded.addAll(matrix.getOrDefault(userId, Collections.emptyMap()).keySet());
        for (Recommendation r : popularFallback(matrix, topN - merged.size(), excluded)) {
            merged.putIfAbsent(r.getItemId(), r);
            if (merged.size() >= topN) {
                break;
            }
        }
        return merged.values().stream().sorted().limit(topN).toList();
    }

    /**
     * 热门物品回退策略
     *
     * <p>基于物品的平均评分和流行度计算得分，返回热门物品推荐。
     * 得分计算公式：平均分 × log(1 + 评分次数)
     *
     * @param matrix 用户-物品评分矩阵
     * @param need 需要的推荐数量
     * @param excluded 需要排除的物品ID集合
     * @return 热门物品推荐列表
     */
    private List<Recommendation> popularFallback(
            Map<Long, Map<Long, Double>> matrix,
            int need,
            Set<Long> excluded
    ) {
        if (need <= 0) {
            return List.of();
        }
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
            Long itemId = e.getKey();
            if (excluded.contains(itemId)) {
                continue;
            }
            double cnt = e.getValue()[1];
            if (cnt <= 0) {
                continue;
            }
            double avg = e.getValue()[0] / cnt;
            double score = avg * Math.log1p(cnt);
            ranked.add(new Recommendation(itemId, score));
        }
        return ranked.stream().sorted().limit(need).toList();
    }

    /**
     * 混合推荐算法
     *
     * <p>融合多种推荐策略的结果，包括：
     * <ul>
     *   <li>基于物品的协同过滤</li>
     *   <li>基于用户的协同过滤</li>
     *   <li>热门物品推荐</li>
     *   <li>物品关联推荐</li>
     *   <li>内容相似度推荐</li>
     * </ul>
     * 根据用户历史评分数量动态调整各策略权重，并应用多样性优化。
     *
     * @param matrix 用户-物品评分矩阵
     * @param userId 目标用户ID
     * @param topN 需要的推荐数量
     * @return 混合推荐列表
     */
    private List<Recommendation> blendHybridRecommendations(
            Map<Long, Map<Long, Double>> matrix,
            Long userId,
            int topN
    ) {
        Set<Long> rated = matrix.getOrDefault(userId, Collections.emptyMap()).keySet();
        int poolSize = Math.max(topN * 5, 20);
        List<Recommendation> itemRecs = itemBasedCF.recommend(matrix, userId, poolSize);
        List<Recommendation> userRecs = userBasedCF.recommend(matrix, userId, poolSize);

        Map<Long, Double> itemRankScore = rankScoreMap(itemRecs);
        Map<Long, Double> userRankScore = rankScoreMap(userRecs);
        Set<Long> excluded = new HashSet<>(rated);
        Map<Long, Double> popScore = popularityScoreMap(matrix, poolSize, excluded);
        Map<Long, Double> associationScore = associationScoreMap(matrix, userId, poolSize);

        Set<Long> candidates = new HashSet<>();
        candidates.addAll(itemRankScore.keySet());
        candidates.addAll(userRankScore.keySet());
        candidates.addAll(popScore.keySet());
        candidates.addAll(associationScore.keySet());
        candidates.removeAll(rated);
        Set<Long> categoryLookupIds = new HashSet<>(candidates);
        categoryLookupIds.addAll(rated);
        Map<Long, String> categoryMap = loadCategoryMap(categoryLookupIds);
        Map<Long, Double> contentSimilarityScore = contentSimilarityScoreMap(matrix, userId, candidates, categoryMap);
        Map<Long, Double> preferredCategoryBoost = preferredCategoryBoostMap(matrix, userId, candidates, categoryMap);
        HybridWeights weights = dynamicWeights(rated.size());

        List<Recommendation> merged = new ArrayList<>();
        for (Long itemId : candidates) {
            double score = weights.itemCf() * itemRankScore.getOrDefault(itemId, 0.0)
                    + weights.userCf() * userRankScore.getOrDefault(itemId, 0.0)
                    + weights.popularity() * popScore.getOrDefault(itemId, 0.0)
                    + weights.association() * associationScore.getOrDefault(itemId, 0.0)
                    + weights.content() * contentSimilarityScore.getOrDefault(itemId, 0.0);
            score *= (1.0 + 0.25 * preferredCategoryBoost.getOrDefault(itemId, 0.0));
            if (score > 1e-12) {
                merged.add(new Recommendation(itemId, score));
            }
        }
        List<Recommendation> fallbackMerged = mergeWithPopularFallback(merged.stream().sorted().toList(), matrix, userId, topN);
        Set<Long> chosenIds = new HashSet<>();
        for (Recommendation r : fallbackMerged) {
            chosenIds.add(r.getItemId());
        }
        Map<Long, String> chosenCategoryMap = loadCategoryMap(chosenIds);
        return diversifyRecommendations(fallbackMerged, topN, chosenCategoryMap);
    }

    /**
     * 计算排名得分映射
     *
     * <p>基于推荐位置计算排名得分，公式：1/(1+位置索引)
     *
     * @param recs 推荐列表
     * @return 物品ID到排名得分的映射
     */
    private Map<Long, Double> rankScoreMap(List<Recommendation> recs) {
        Map<Long, Double> map = new HashMap<>();
        for (int i = 0; i < recs.size(); i++) {
            Recommendation r = recs.get(i);
            double rankScore = 1.0 / (1.0 + i);
            map.put(r.getItemId(), Math.max(map.getOrDefault(r.getItemId(), 0.0), rankScore));
        }
        return map;
    }

    /**
     * 计算热门物品得分映射
     *
     * <p>获取热门物品并进行归一化处理。
     *
     * @param matrix 用户-物品评分矩阵
     * @param limit 限制数量
     * @param excluded 排除的物品集合
     * @return 物品ID到归一化热门得分的映射
     */
    private Map<Long, Double> popularityScoreMap(
            Map<Long, Map<Long, Double>> matrix,
            int limit,
            Set<Long> excluded
    ) {
        List<Recommendation> popular = popularFallback(matrix, limit, excluded);
        if (popular.isEmpty()) {
            return Map.of();
        }
        double max = popular.stream().mapToDouble(Recommendation::getScore).max().orElse(1.0);
        if (max <= 1e-12) {
            max = 1.0;
        }
        Map<Long, Double> map = new HashMap<>();
        for (Recommendation r : popular) {
            map.put(r.getItemId(), r.getScore() / max);
        }
        return map;
    }

    /**
     * 计算物品关联得分映射
     *
     * <p>基于共同用户计算物品间的关联强度，使用余弦相似度。
     *
     * @param matrix 用户-物品评分矩阵
     * @param userId 目标用户ID
     * @param limit 限制数量
     * @return 物品ID到关联得分的映射
     */
    private Map<Long, Double> associationScoreMap(
            Map<Long, Map<Long, Double>> matrix,
            Long userId,
            int limit
    ) {
        Map<Long, Double> userRatings = matrix.getOrDefault(userId, Collections.emptyMap());
        if (userRatings.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<Long>> itemUsers = buildItemUsers(matrix);
        Map<Long, Double> raw = new HashMap<>();
        for (Long ratedItem : userRatings.keySet()) {
            Set<Long> usersI = itemUsers.getOrDefault(ratedItem, Collections.emptySet());
            if (usersI.isEmpty()) {
                continue;
            }
            double userWeight = Math.max(0.0, userRatings.getOrDefault(ratedItem, 0.0) / 5.0);
            for (Map.Entry<Long, Set<Long>> entry : itemUsers.entrySet()) {
                Long candidate = entry.getKey();
                if (userRatings.containsKey(candidate)) {
                    continue;
                }
                Set<Long> usersJ = entry.getValue();
                int co = overlapCount(usersI, usersJ);
                if (co == 0) {
                    continue;
                }
                double sim = co / Math.sqrt((double) usersI.size() * usersJ.size());
                raw.merge(candidate, sim * userWeight, Double::sum);
            }
        }
        return normalizeAndLimit(raw, limit);
    }

    /**
     * 构建物品-用户映射
     *
     * <p>从用户-物品矩阵反向构建物品到用户集合的映射。
     *
     * @param matrix 用户-物品评分矩阵
     * @return 物品ID到用户ID集合的映射
     */
    private Map<Long, Set<Long>> buildItemUsers(Map<Long, Map<Long, Double>> matrix) {
        Map<Long, Set<Long>> itemUsers = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Double>> userEntry : matrix.entrySet()) {
            Long uid = userEntry.getKey();
            for (Long itemId : userEntry.getValue().keySet()) {
                itemUsers.computeIfAbsent(itemId, k -> new HashSet<>()).add(uid);
            }
        }
        return itemUsers;
    }

    /**
     * 计算两个集合的重叠数量
     *
     * @param a 集合A
     * @param b 集合B
     * @return 重叠元素的数量
     */
    private int overlapCount(Set<Long> a, Set<Long> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<Long> smaller = a.size() <= b.size() ? a : b;
        Set<Long> larger = a.size() <= b.size() ? b : a;
        int c = 0;
        for (Long id : smaller) {
            if (larger.contains(id)) {
                c++;
            }
        }
        return c;
    }

    /**
     * 归一化并限制结果数量
     *
     * <p>对原始得分进行归一化处理，并按得分降序限制返回数量。
     *
     * @param raw 原始得分映射
     * @param limit 限制数量
     * @return 归一化后的得分映射
     */
    private Map<Long, Double> normalizeAndLimit(Map<Long, Double> raw, int limit) {
        if (raw.isEmpty()) {
            return Map.of();
        }
        double max = raw.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        final double normalizedMax = max <= 1e-12 ? 1.0 : max;
        return raw.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(Math.max(1, limit))
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue() / normalizedMax), Map::putAll);
    }

    /**
     * 多样化推荐结果
     *
     * <p>通过类别惩罚机制提高推荐结果的多样性，避免同类别物品过度集中。
     * 调整公式：调整后得分 = 原始得分 / (1 + 同类别已选数量 × 0.35)
     *
     * @param ranked 已排序的推荐列表
     * @param topN 需要的推荐数量
     * @param categoryMap 物品ID到类别的映射
     * @return 多样化后的推荐列表
     */
    private List<Recommendation> diversifyRecommendations(
            List<Recommendation> ranked,
            int topN,
            Map<Long, String> categoryMap
    ) {
        if (ranked.isEmpty()) {
            return ranked;
        }
        List<Recommendation> pool = new ArrayList<>(ranked);
        List<Recommendation> selected = new ArrayList<>();
        Map<String, Integer> categoryCount = new HashMap<>();
        while (!pool.isEmpty() && selected.size() < topN) {
            Recommendation best = null;
            double bestScore = -1.0;
            for (Recommendation r : pool) {
                String cat = categoryMap.getOrDefault(r.getItemId(), "");
                int seen = categoryCount.getOrDefault(cat, 0);
                double adjusted = r.getScore() / (1.0 + seen * 0.35);
                if (adjusted > bestScore) {
                    bestScore = adjusted;
                    best = r;
                }
            }
            if (best == null) {
                break;
            }
            selected.add(best);
            String cat = categoryMap.getOrDefault(best.getItemId(), "");
            categoryCount.put(cat, categoryCount.getOrDefault(cat, 0) + 1);
            pool.remove(best);
        }
        return selected;
    }

    /**
     * 加载物品类别映射
     *
     * @param itemIds 物品ID集合
     * @return 物品ID到类别的映射
     */
    private Map<Long, String> loadCategoryMap(Set<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        Iterable<Item> items = itemRepository.findAllById(itemIds);
        for (Item item : items) {
            map.put(item.getId(), item.getCategory());
        }
        return map;
    }

    /**
     * 计算内容相似度得分映射
     *
     * <p>基于用户偏好类别计算候选物品的内容相似度得分。
     *
     * @param matrix 用户-物品评分矩阵
     * @param userId 目标用户ID
     * @param candidates 候选物品集合
     * @param categoryMap 物品类别映射
     * @return 物品ID到内容相似度得分的映射
     */
    private Map<Long, Double> contentSimilarityScoreMap(
            Map<Long, Map<Long, Double>> matrix,
            Long userId,
            Set<Long> candidates,
            Map<Long, String> categoryMap
    ) {
        Map<Long, Double> userRatings = matrix.getOrDefault(userId, Collections.emptyMap());
        if (userRatings.isEmpty() || candidates.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> categoryPref = new HashMap<>();
        for (Map.Entry<Long, Double> e : userRatings.entrySet()) {
            String cat = categoryMap.get(e.getKey());
            if (cat == null || cat.isBlank()) {
                continue;
            }
            categoryPref.merge(cat, e.getValue(), Double::sum);
        }
        if (categoryPref.isEmpty()) {
            return Map.of();
        }
        double maxPref = categoryPref.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        if (maxPref <= 1e-12) {
            maxPref = 1.0;
        }
        Map<Long, Double> raw = new HashMap<>();
        for (Long itemId : candidates) {
            String cat = categoryMap.get(itemId);
            if (cat == null || cat.isBlank()) {
                continue;
            }
            raw.put(itemId, categoryPref.getOrDefault(cat, 0.0) / maxPref);
        }
        return raw;
    }

    /**
     * 计算偏好类别提升映射
     *
     * <p>识别用户偏好的类别（平均分≥4.0且评分数≥2），计算类别强度，
     * 用于提升该类别物品的推荐得分。
     *
     * @param matrix 用户-物品评分矩阵
     * @param userId 目标用户ID
     * @param candidates 候选物品集合
     * @param categoryMap 物品类别映射
     * @return 物品ID到类别提升系数的映射
     */
    private Map<Long, Double> preferredCategoryBoostMap(
            Map<Long, Map<Long, Double>> matrix,
            Long userId,
            Set<Long> candidates,
            Map<Long, String> categoryMap
    ) {
        Map<Long, Double> userRatings = matrix.getOrDefault(userId, Collections.emptyMap());
        if (userRatings.isEmpty() || candidates.isEmpty()) {
            return Map.of();
        }
        Map<String, double[]> stat = new HashMap<>();
        for (Map.Entry<Long, Double> e : userRatings.entrySet()) {
            String cat = categoryMap.get(e.getKey());
            if (cat == null || cat.isBlank()) {
                continue;
            }
            double[] acc = stat.computeIfAbsent(cat, k -> new double[2]);
            acc[0] += e.getValue();
            acc[1] += 1.0;
        }
        if (stat.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> catStrength = new HashMap<>();
        for (Map.Entry<String, double[]> e : stat.entrySet()) {
            double avg = e.getValue()[0] / e.getValue()[1];
            double cnt = e.getValue()[1];
            if (avg < 4.0 || cnt < 2.0) {
                continue;
            }
            double strength = Math.min(1.0, ((avg - 4.0) / 1.0) * 0.7 + Math.min(0.3, (cnt - 2.0) * 0.08));
            if (strength > 1e-12) {
                catStrength.put(e.getKey(), strength);
            }
        }
        if (catStrength.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> boost = new HashMap<>();
        for (Long itemId : candidates) {
            String cat = categoryMap.get(itemId);
            if (cat == null || cat.isBlank()) {
                continue;
            }
            boost.put(itemId, catStrength.getOrDefault(cat, 0.0));
        }
        return boost;
    }

    /**
     * 动态计算混合权重
     *
     * <p>根据用户历史评分数量动态调整各推荐策略的权重：
     * <ul>
     *   <li>评分数&lt;6：侧重内容和热门物品</li>
     *   <li>评分数6-18：均衡各策略</li>
     *   <li>评分数≥18：侧重协同过滤</li>
     * </ul>
     *
     * @param ratedCount 用户历史评分数量
     * @return 包含各策略权重的HybridWeights对象
     */
    private HybridWeights dynamicWeights(int ratedCount) {
        if (ratedCount < 6) {
            return new HybridWeights(0.25, 0.10, 0.20, 0.10, 0.35);
        }
        if (ratedCount < 18) {
            return new HybridWeights(0.35, 0.20, 0.15, 0.15, 0.15);
        }
        return new HybridWeights(0.42, 0.23, 0.15, 0.12, 0.08);
    }

    /**
     * 划分训练集和测试集
     *
     * <p>基于哈希函数将用户评分数据划分为训练集和测试集。
     * 保证每个用户至少有1条训练数据和1条测试数据。
     *
     * @param testRatio 测试集比例
     * @param relevanceThreshold 相关性阈值
     * @return 包含训练集、测试集和候选物品集合的数据集划分结果
     */
    private DatasetSplit splitDataset(double testRatio, double relevanceThreshold) {
        // read both score and timestamp for time-based split
        Map<Long, Map<Long, Double>> all = new HashMap<>();
        Map<Long, Map<Long, Instant>> timeMap = new HashMap<>();
        for (RatingRepository.UserItemScoreRatedAtView row : ratingRepository.findAllUserItemScoresWithRatedAt()) {
            Long userId = row.getUserId();
            Long itemId = row.getItemId();
            Double score = row.getScore();
            Instant ratedAt = row.getRatedAt();
            if (userId == null || itemId == null || score == null || ratedAt == null) {
                continue;
            }
            all.computeIfAbsent(userId, k -> new HashMap<>()).put(itemId, score);
            timeMap.computeIfAbsent(userId, k -> new HashMap<>()).put(itemId, ratedAt);
        }

        Map<Long, Map<Long, Double>> train = new HashMap<>();
        Map<Long, Set<Long>> testRelevant = new HashMap<>();
        Set<Long> candidateItems = new HashSet<>();
        int trainSize = 0;
        int testSize = 0;

        for (Map.Entry<Long, Map<Long, Double>> userEntry : all.entrySet()) {
            Long userId = userEntry.getKey();
            Map<Long, Double> userRatings = userEntry.getValue();
            List<Long> itemIds = new ArrayList<>(userRatings.keySet());
            itemIds.sort(Comparator.comparing((Long id) -> timeMap.getOrDefault(userId, Collections.emptyMap()).getOrDefault(id, Instant.EPOCH)));

            if (itemIds.size() < 2) {
                for (Long itemId : itemIds) {
                    Double s = userRatings.get(itemId);
                    train.computeIfAbsent(userId, k -> new HashMap<>()).put(itemId, s);
                    candidateItems.add(itemId);
                    trainSize++;
                }
                continue;
            }

            int n = itemIds.size();
            int testCount = (int) Math.ceil(n * testRatio);
            if (testCount < 1) testCount = 1;
            if (testCount >= n) testCount = n - 1;
            int trainCount = n - testCount;

            for (int i = 0; i < trainCount; i++) {
                Long itemId = itemIds.get(i);
                Double s = userRatings.get(itemId);
                train.computeIfAbsent(userId, k -> new HashMap<>()).put(itemId, s);
                candidateItems.add(itemId);
                trainSize++;
            }

            Set<Long> relevant = new HashSet<>();
            for (int i = trainCount; i < n; i++) {
                Long itemId = itemIds.get(i);
                Double s = userRatings.get(itemId);
                testSize++;
                candidateItems.add(itemId);
                if (s >= relevanceThreshold) {
                    relevant.add(itemId);
                }
            }
            if (!relevant.isEmpty()) {
                testRelevant.put(userId, relevant);
            }
        }
        return new DatasetSplit(train, testRelevant, candidateItems, trainSize, testSize);
    }

    private DatasetSplit splitDatasetWithSample(double testRatio, double relevanceThreshold, int sampleSize) {
        Map<Long, Map<Long, Double>> allUsers = new HashMap<>();
        Map<Long, Map<Long, Instant>> timeMap = new HashMap<>();
        for (RatingRepository.UserItemScoreRatedAtView row : ratingRepository.findAllUserItemScoresWithRatedAt()) {
            Long userId = row.getUserId();
            Long itemId = row.getItemId();
            Double score = row.getScore();
            Instant ratedAt = row.getRatedAt();
            if (userId == null || itemId == null || score == null || ratedAt == null) {
                continue;
            }
            allUsers.computeIfAbsent(userId, k -> new HashMap<>()).put(itemId, score);
            timeMap.computeIfAbsent(userId, k -> new HashMap<>()).put(itemId, ratedAt);
        }

        List<Long> allUserIds = new ArrayList<>(allUsers.keySet());
        Collections.shuffle(allUserIds, new Random(42));
        int actualSampleSize = Math.min(sampleSize, allUserIds.size());
        List<Long> sampledUserIds = allUserIds.subList(0, actualSampleSize);
        Set<Long> sampledUserSet = new HashSet<>(sampledUserIds);

        Map<Long, Map<Long, Double>> sampled = new HashMap<>();
        for (Long userId : sampledUserIds) {
            sampled.put(userId, allUsers.get(userId));
        }

        Map<Long, Map<Long, Double>> train = new HashMap<>();
        Map<Long, Set<Long>> testRelevant = new HashMap<>();
        Set<Long> candidateItems = new HashSet<>();
        int trainSize = 0;
        int testSize = 0;

        for (Map.Entry<Long, Map<Long, Double>> userEntry : sampled.entrySet()) {
            Long userId = userEntry.getKey();
            Map<Long, Double> userRatings = userEntry.getValue();
            List<Long> itemIds = new ArrayList<>(userRatings.keySet());
            itemIds.sort(Comparator.comparing((Long id) -> timeMap.getOrDefault(userId, Collections.emptyMap()).getOrDefault(id, Instant.EPOCH)));

            if (itemIds.size() < 2) {
                for (Long itemId : itemIds) {
                    Double s = userRatings.get(itemId);
                    train.computeIfAbsent(userId, k -> new HashMap<>()).put(itemId, s);
                    candidateItems.add(itemId);
                    trainSize++;
                }
                continue;
            }

            int n = itemIds.size();
            int testCount = (int) Math.ceil(n * testRatio);
            if (testCount < 1) testCount = 1;
            if (testCount >= n) testCount = n - 1;
            int trainCount = n - testCount;

            for (int i = 0; i < trainCount; i++) {
                Long itemId = itemIds.get(i);
                Double s = userRatings.get(itemId);
                train.computeIfAbsent(userId, k -> new HashMap<>()).put(itemId, s);
                candidateItems.add(itemId);
                trainSize++;
            }

            Set<Long> relevant = new HashSet<>();
            for (int i = trainCount; i < n; i++) {
                Long itemId = itemIds.get(i);
                Double s = userRatings.get(itemId);
                testSize++;
                candidateItems.add(itemId);
                if (s >= relevanceThreshold) {
                    relevant.add(itemId);
                }
            }
            if (!relevant.isEmpty()) {
                testRelevant.put(userId, relevant);
            }
        }
        return new DatasetSplit(train, testRelevant, candidateItems, trainSize, testSize);
    }

    /**
     * 判断物品是否在测试集中
     *
     * <p>基于用户ID和物品ID的哈希值确定是否分配到测试集。
     *
     * @param userId 用户ID
     * @param itemId 物品ID
     * @param ratio 测试集比例
     * @return 如果应该在测试集中返回true
     */
    private boolean isInTest(Long userId, Long itemId, double ratio) {
        long hash = Objects.hash(userId, itemId) & Long.MAX_VALUE;
        long bucket = hash % 10000;
        return bucket < (long)(ratio * 10000);
    }
    /**
     * 规范化测试集比例
     *
     * @param testRatio 原始测试集比例
     * @return 规范化后的比例，范围0.0-1.0，无效值返回0.2
     */
    private double normalizeRatio(double testRatio) {
        if (Double.isNaN(testRatio) || testRatio <= 0.0 || testRatio >= 1.0) {
            return 0.2;
        }
        return testRatio;
    }

    /**
     * 规范化相关性阈值
     *
     * @param threshold 原始阈值
     * @return 规范化后的阈值，范围0.0-5.0，NaN返回4.0
     */
    private double normalizeThreshold(double threshold) {
        if (Double.isNaN(threshold)) {
            return 1.5;
        }
        return Math.max(0.0, Math.min(5.0, threshold));
    }

    /**
     * 计算NDCG@K指标
     *
     * <p>计算归一化折损累计增益，衡量推荐列表中相关物品的排名质量。
     *
     * @param recommended 推荐列表
     * @param relevant 相关物品集合
     * @param topK Top-K值
     * @return NDCG值，范围0.0-1.0
     */
    private double ndcgAtK(List<Long> recommended, Set<Long> relevant, int topK) {
        if (relevant.isEmpty()) {
            return 0.0;
        }
        double dcg = 0.0;
        for (int i = 0; i < Math.min(topK, recommended.size()); i++) {
            if (relevant.contains(recommended.get(i))) {
                dcg += 1.0 / log2(i + 2);
            }
        }

        int idealHits = Math.min(topK, relevant.size());
        double idcg = 0.0;
        for (int i = 0; i < idealHits; i++) {
            idcg += 1.0 / log2(i + 2);
        }
        if (idcg <= 1e-12) {
            return 0.0;
        }
        return dcg / idcg;
    }

    /**
     * 计算以2为底的对数
     *
     * @param v 输入值
     * @return log2(v)
     */
    private double log2(int v) {
        return Math.log(v) / Math.log(2.0);
    }

    /**
     * 数据集划分结果记录
     *
     * @param trainMatrix 训练集用户-物品评分矩阵
     * @param testRelevant 测试集中相关物品映射（用户ID→相关物品ID集合）
     * @param candidateItems 候选物品集合
     * @param trainSize 训练集大小
     * @param testSize 测试集大小
     */
    private record DatasetSplit(
            Map<Long, Map<Long, Double>> trainMatrix,
            Map<Long, Set<Long>> testRelevant,
            Set<Long> candidateItems,
            int trainSize,
            int testSize
    ) {
        /**
         * 获取可评估的用户数量
         *
         * @return 测试集中有相关物品的用户数量
         */
        int evaluableUsers() {
            return testRelevant.size();
        }
    }

    /**
     * 评估报告记录
     *
     * @param topK Top-K推荐的K值
     * @param testRatio 测试集比例
     * @param relevanceThreshold 相关性阈值
     * @param trainSize 训练集大小
     * @param testSize 测试集大小
     * @param evaluableUsers 可评估的用户数量
     * @param metrics 各算法的评估指标映射
     */
    public record EvaluationReport(
            int topK,
            double testRatio,
            double relevanceThreshold,
            int trainSize,
            int testSize,
            int evaluableUsers,
            Map<String, AlgorithmMetrics> metrics
    ) {}

    /**
     * 算法评估指标记录
     *
     * @param precisionAtK 精确率@K
     * @param recallAtK 召回率@K
     * @param ndcgAtK NDCG@K
     * @param coverage 覆盖率
     * @param users 参与评估的用户数量
     */
    public record AlgorithmMetrics(
            double precisionAtK,
            double recallAtK,
            double ndcgAtK,
            double coverage,
            int users
    ) {}

    /**
     * 混合推荐权重记录
     *
     * @param itemCf 基于物品的协同过滤权重
     * @param userCf 基于用户的协同过滤权重
     * @param popularity 热门物品权重
     * @param association 物品关联权重
     * @param content 内容相似度权重
     */
    private record HybridWeights(
            double itemCf,
            double userCf,
            double popularity,
            double association,
            double content
    ) {
    }
}
