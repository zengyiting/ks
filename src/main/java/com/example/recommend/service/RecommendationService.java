package com.example.recommend.service;

import com.example.recommend.algo.ItemBasedCF;
import com.example.recommend.algo.Recommendation;
import com.example.recommend.algo.RecommenderStrategy;
import com.example.recommend.algo.UserBasedCF;
import com.example.recommend.model.Item;
import com.example.recommend.repository.ItemRepository;
import com.example.recommend.repository.RatingRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;


/**
 * 推荐服务类
 *
 * <p>提供个性化商品推荐功能，支持多种推荐算法：
 * <ul>
 *   <li>基于用户的协同过滤（User-Based CF）</li>
 *   <li>基于物品的协同过滤（Item-Based CF）</li>
 *   <li>基于行为的推荐（Behavior-Based）</li>
 *   <li>混合推荐算法（Hybrid）</li>
 * </ul>
 * 支持推荐结果缓存、推荐理由生成和多样性优化。
 */
@Service
public class RecommendationService {
    /** 推荐结果缓存名称 */
    static final String RESULT_CACHE = "recommendationResults";

    /** 评分数据访问层 */
    private final RatingRepository ratingRepository;

    /** 商品数据访问层 */
    private final ItemRepository itemRepository;

    /** 商品关联预计算服务 */
    private final ItemAssociationPrecomputeService itemAssociationPrecomputeService;

    /** 基于用户的协同过滤策略 */
    private final RecommenderStrategy userBasedCF = new UserBasedCF();

    /** 基于物品的协同过滤策略 */
    private final ItemBasedCF itemBasedCF = new ItemBasedCF();

    /**
     * 构造函数，注入依赖
     *
     * @param ratingRepository 评分仓库
     * @param itemRepository 商品仓库
     * @param itemAssociationPrecomputeService 商品关联预计算服务
     */
    public RecommendationService(
            RatingRepository ratingRepository,
            ItemRepository itemRepository,
            ItemAssociationPrecomputeService itemAssociationPrecomputeService
    ) {
        this.ratingRepository = ratingRepository;
        this.itemRepository = itemRepository;
        this.itemAssociationPrecomputeService = itemAssociationPrecomputeService;
    }

    /**
     * 生成缓存键
     *
     * @param userId 用户ID
     * @param topN 推荐数量
     * @param type 算法类型
     * @return 格式为"userId:ALGORITHM_TYPE:topN"的缓存键
     */
    public static String cacheKey(Long userId, int topN, AlgorithmType type) {
        AlgorithmType safeType = type == null ? AlgorithmType.USER_BASED : type;
        return userId + ":" + safeType.name() + ":" + topN;
    }

    /**
     * 为用户生成推荐列表
     *
     * @param userId 用户ID
     * @param topN 推荐数量
     * @param type 算法类型，如果为null则使用USER_BASED
     * @return 推荐列表，如果参数无效返回空列表
     */
    @Transactional(readOnly = true)
    public List<Recommendation> recommendForUser(Long userId, int topN, AlgorithmType type) {
        if (userId == null || topN <= 0) {
            return List.of();
        }
        RecommendationContext context = new RecommendationContext();
        return recommendForUser(context, userId, topN, type == null ? AlgorithmType.USER_BASED : type);
    }

    /**
     * 为用户生成带推荐理由的推荐列表
     *
     * <p>该方法会缓存推荐结果，并生成每条推荐的解释说明。
     * 推荐理由包括算法类型、类别匹配、热门程度等信息。
     *
     * @param userId 用户ID
     * @param topN 推荐数量
     * @param type 算法类型，如果为null则使用USER_BASED
     * @return 包含推荐理由的推荐结果列表，如果参数无效或无推荐返回空列表
     */
    @Cacheable(
            cacheNames = RESULT_CACHE,
            keyGenerator = "recommendationCacheKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    @Transactional(readOnly = true)
    public List<RecommendationResult> recommendForUserWithReason(Long userId, int topN, AlgorithmType type) {
        if (userId == null || topN <= 0) {
            return List.of();
        }
        RecommendationContext context = new RecommendationContext();
        AlgorithmType safeType = type == null ? AlgorithmType.USER_BASED : type;
        List<Recommendation> recs = recommendForUser(context, userId, topN, safeType);
        if (recs.isEmpty()) {
            return List.of();
        }

        Map<Long, Map<Long, Double>> matrix = context.userItemMatrix();
        Map<Long, Map<Long, Double>> decayMap = context.userItemDecayMap();
        Map<Long, Double> userRatings = matrix.getOrDefault(userId, Collections.emptyMap());
        Set<Long> ids = new HashSet<>(userRatings.keySet());
        for (Recommendation rec : recs) {
            ids.add(rec.getItemId());
        }

        Map<Long, String> categoryMap = context.loadCategoryMap(ids);
        Map<String, Double> pref = categoryPreferenceMap(
                userRatings,
                decayMap.getOrDefault(userId, Collections.emptyMap()),
                categoryMap
        );
        String topCategory = pref.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        double topStrength = topCategory == null ? 0.0 : pref.getOrDefault(topCategory, 0.0);
        Set<Long> ratedItemIds = userRatings.keySet();

        List<RecommendationResult> results = new ArrayList<>();
        for (Recommendation rec : recs) {
            String reason = buildReason(safeType, rec.getItemId(), rec.getScore(), categoryMap, topCategory, topStrength, ratedItemIds);
            results.add(new RecommendationResult(rec.getItemId(), rec.getScore(), reason));
        }
        return results;
    }

    /**
     * 核心推荐逻辑
     *
     * <p>根据算法类型执行相应的推荐策略，并应用热门物品回退和多样性优化。
     *
     * @param context 推荐上下文，用于缓存中间计算结果
     * @param userId 用户ID
     * @param topN 推荐数量
     * @param type 算法类型
     * @return 推荐列表
     */
    private List<Recommendation> recommendForUser(
            RecommendationContext context,
            Long userId,
            int topN,
            AlgorithmType type
    ) {
        Map<Long, Map<Long, Double>> matrix = context.userItemMatrix();
        Map<Long, Map<Long, Double>> decayMap = context.userItemDecayMap();
        List<Recommendation> primary = switch (type) {
            case USER_BASED -> {
                Map<Long, Map<Long, Double>> decayed = applyDecayToMatrix(matrix, decayMap);
                yield userBasedCF.recommend(decayed, userId, topN);
            }
            case ITEM_BASED -> itemBasedCF.recommend(matrix, context.itemUserRatingMatrix(), userId, topN);
            case BEHAVIOR_BASED -> behaviorBasedRecommendations(context, matrix, decayMap, userId, topN);
            case HYBRID -> blendHybridRecommendations(context, matrix, decayMap, userId, topN);
        };
        List<Recommendation> merged = mergeWithPopularFallback(context, primary, matrix, userId, topN);
        if (type != AlgorithmType.HYBRID || merged.isEmpty()) {
            return merged;
        }

        Set<Long> itemIds = new HashSet<>();
        for (Recommendation rec : merged) {
            itemIds.add(rec.getItemId());
        }
        return diversifyRecommendations(merged, topN, context.loadCategoryMap(itemIds));
    }

    /**
     * 构建用户-物品评分矩阵
     *
     * @return 用户ID到物品评分映射的矩阵
     */
    private Map<Long, Map<Long, Double>> buildUserItemMatrix() {
        Map<Long, Map<Long, Double>> matrix = new HashMap<>();
        for (RatingRepository.UserItemScoreView row : ratingRepository.findAllUserItemScores()) {
            Long userId = row.getUserId();
            Long itemId = row.getItemId();
            Double score = row.getScore();
            if (userId == null || itemId == null || score == null) {
                continue;
            }
            matrix.computeIfAbsent(userId, ignored -> new HashMap<>()).put(itemId, score);
        }
        return matrix;
    }

    /**
     * 对用户-物品矩阵应用时效衰减权重，返回新的矩阵（不修改原矩阵）
     *
     * @param matrix 原始用户-物品评分矩阵
     * @param decayMap 用户-物品衰减权重矩阵
     * @return 应用衰减后的评分矩阵
     */
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

    /**
     * 构建用户-物品衰减权重矩阵
     *
     * <p>基于评分时间计算衰减权重，越近的评分权重越高。
     * 衰减公式：0.5^(天数/30)
     *
     * @return 用户ID到物品衰减权重的映射
     */
    private Map<Long, Map<Long, Double>> buildUserItemDecayMap() {
        Map<Long, Map<Long, Double>> map = new HashMap<>();
        Instant now = Instant.now();
        for (RatingRepository.UserItemRatedAtView row : ratingRepository.findAllUserItemRatedAt()) {
            Long userId = row.getUserId();
            Long itemId = row.getItemId();
            Instant ratedAt = row.getRatedAt();
            if (userId == null || itemId == null) {
                continue;
            }
            map.computeIfAbsent(userId, ignored -> new HashMap<>()).put(itemId, decayWeight(now, ratedAt));
        }
        return map;
    }

    /**
     * 合并推荐结果并应用热门物品回退
     *
        * <p>将主要推荐结果与热门物品结合；若仍不足，则从未评分商品中按偏好规则补齐，
        * 使返回数量尽可能接近 topN（不超过未评分商品总数）。
     *
     * @param context 推荐上下文
     * @param primary 主要推荐列表
     * @param matrix 用户-物品评分矩阵
     * @param userId 用户ID
     * @param topN 需要的推荐数量
     * @return 合并后的推荐列表
     */
    private List<Recommendation> mergeWithPopularFallback(
            RecommendationContext context,
            List<Recommendation> primary,
            Map<Long, Map<Long, Double>> matrix,
            Long userId,
            int topN
    ) {
        Map<Long, Recommendation> merged = new LinkedHashMap<>();
        for (Recommendation rec : primary) {
            merged.putIfAbsent(rec.getItemId(), rec);
        }
        if (merged.size() >= topN) {
            return merged.values().stream().sorted().limit(topN).toList();
        }

        Set<Long> ratedItems = matrix.getOrDefault(userId, Collections.emptyMap()).keySet();
        Set<Long> excluded = new HashSet<>(merged.keySet());
        excluded.addAll(ratedItems);
        for (Recommendation rec : popularFallback(context, topN - merged.size(), excluded)) {
            merged.putIfAbsent(rec.getItemId(), rec);
            if (merged.size() >= topN) {
                break;
            }
        }

        if (merged.size() < topN) {
            Set<Long> coldStartExcluded = new HashSet<>(merged.keySet());
            coldStartExcluded.addAll(ratedItems);
            for (Recommendation rec : catalogFallback(context, matrix, userId, topN - merged.size(), coldStartExcluded)) {
                merged.putIfAbsent(rec.getItemId(), rec);
                if (merged.size() >= topN) {
                    break;
                }
            }
        }
        return merged.values().stream().sorted().limit(topN).toList();
    }

    /**
     * 热门物品回退策略（改进版）
     *
     * <p>改进点：
     * <ul>
     *   <li>引入随机扰动因子（±10%），避免热门物品过于固化</li>
     *   <li>添加类别多样性约束，限制同类别物品数量不超过40%</li>
     *   <li>引入时间衰减权重，使热门度有一定波动空间</li>
     * </ul>
     *
     * @param context 推荐上下文
     * @param need 需要的推荐数量
     * @param excluded 需要排除的物品集合
     * @return 热门物品推荐列表
     */
    private List<Recommendation> popularFallback(RecommendationContext context, int need, Set<Long> excluded) {
        if (need <= 0) {
            return List.of();
        }

        java.util.Random random = new java.util.Random();
        Map<Long, String> categoryMap = context.loadCategoryMap(
            context.popularityRanking().stream()
                .map(Recommendation::getItemId)
                .collect(java.util.stream.Collectors.toSet())
        );

        List<Recommendation> result = new ArrayList<>(need);
        Map<String, Integer> categoryCount = new HashMap<>();
        int maxSameCategory = (int) Math.ceil(need * 0.4);

        for (Recommendation rec : context.popularityRanking()) {
            if (excluded.contains(rec.getItemId())) {
                continue;
            }

            String category = categoryMap.getOrDefault(rec.getItemId(), "");
            int currentCount = categoryCount.getOrDefault(category, 0);

            if (!category.isEmpty() && currentCount >= maxSameCategory) {
                continue;
            }

            double randomFactor = 0.9 + random.nextDouble() * 0.2;
            double adjustedScore = rec.getScore() * randomFactor;

            result.add(new Recommendation(rec.getItemId(), adjustedScore));
            categoryCount.put(category, currentCount + 1);

            if (result.size() >= need) {
                break;
            }
        }

        if (result.size() < need) {
            for (Recommendation rec : context.popularityRanking()) {
                if (excluded.contains(rec.getItemId())) {
                    continue;
                }
                boolean alreadyAdded = false;
                for (Recommendation r : result) {
                    if (r.getItemId().equals(rec.getItemId())) {
                        alreadyAdded = true;
                        break;
                    }
                }
                if (!alreadyAdded) {
                    double randomFactor = 0.9 + random.nextDouble() * 0.2;
                    result.add(new Recommendation(rec.getItemId(), rec.getScore() * randomFactor));
                    if (result.size() >= need) {
                        break;
                    }
                }
            }
        }

        return result;
    }

    /**
     * 目录级冷启动补齐策略（改进版）
     *
     * <p>改进点：
     * <ul>
     *   <li>动态调整偏好类别权重，根据用户活跃度自适应</li>
     *   <li>提高偏好分数权重（从0.20提升到0.35），增强个性化</li>
     *   <li>引入用户活跃度因子，活跃用户偏好权重更高</li>
     *   <li>添加类别多样性约束，避免同类物品过度集中</li>
     * </ul>
     */
    private List<Recommendation> catalogFallback(
            RecommendationContext context,
            Map<Long, Map<Long, Double>> matrix,
            Long userId,
            int need,
            Set<Long> excluded
    ) {
        if (need <= 0) {
            return List.of();
        }

        List<Item> allItems = context.allItems();
        if (allItems.isEmpty()) {
            return List.of();
        }

        Map<Long, Double> userRatings = matrix.getOrDefault(userId, Collections.emptyMap());
        int userActivity = userRatings.size();
        double activityFactor = Math.min(1.0, userActivity / 10.0);

        Map<String, Double> categoryPref = Map.of();
        double maxPref = 0.0;
        if (!userRatings.isEmpty()) {
            Set<Long> lookupIds = new HashSet<>(userRatings.keySet());
            for (Item item : allItems) {
                if (item.getId() != null) {
                    lookupIds.add(item.getId());
                }
            }
            Map<Long, String> categoryMap = context.loadCategoryMap(lookupIds);
            categoryPref = categoryPreferenceMap(userRatings, Collections.emptyMap(), categoryMap);
            maxPref = categoryPref.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        }

        double baseWeight = 0.35 + 0.15 * activityFactor;
        double activityBonus = userActivity > 5 ? 0.10 : 0.0;

        List<Recommendation> candidates = new ArrayList<>();
        Map<String, Integer> categoryCount = new HashMap<>();
        int maxSameCategory = (int) Math.ceil(need * 0.5);

        for (Item item : allItems) {
            if (item == null || item.getId() == null || excluded.contains(item.getId())) {
                continue;
            }

            String category = item.getCategory();
            if (category != null && !category.isEmpty()) {
                int currentCount = categoryCount.getOrDefault(category, 0);
                if (currentCount >= maxSameCategory) {
                    continue;
                }
                categoryCount.put(category, currentCount + 1);
            }

            double prefScore = 0.0;
            if (maxPref > 1e-12) {
                prefScore = categoryPref.getOrDefault(item.getCategory(), 0.0) / maxPref;
            }

            double score = 0.05 + (baseWeight * prefScore) + activityBonus + (1.0 / (1_000_000_000d + item.getId()));
            candidates.add(new Recommendation(item.getId(), score));
        }

        if (candidates.size() < need) {
            categoryCount.clear();
            for (Item item : allItems) {
                if (item == null || item.getId() == null || excluded.contains(item.getId())) {
                    continue;
                }
                boolean alreadyAdded = false;
                for (Recommendation r : candidates) {
                    if (r.getItemId().equals(item.getId())) {
                        alreadyAdded = true;
                        break;
                    }
                }
                if (!alreadyAdded) {
                    double prefScore = 0.0;
                    if (maxPref > 1e-12) {
                        prefScore = categoryPref.getOrDefault(item.getCategory(), 0.0) / maxPref;
                    }
                    double score = 0.05 + (baseWeight * prefScore) + activityBonus + (1.0 / (1_000_000_000d + item.getId()));
                    candidates.add(new Recommendation(item.getId(), score));
                    if (candidates.size() >= need) {
                        break;
                    }
                }
            }
        }

        return candidates.stream().sorted().limit(need).toList();
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
     * 根据用户历史评分数量动态调整各策略权重，并应用偏好类别提升。
     *
     * @param context 推荐上下文
     * @param matrix 用户-物品评分矩阵
     * @param decayMap 衰减权重矩阵
     * @param userId 用户ID
     * @param topN 推荐数量
     * @return 混合推荐列表
     */
    private List<Recommendation> blendHybridRecommendations(
            RecommendationContext context,
            Map<Long, Map<Long, Double>> matrix,
            Map<Long, Map<Long, Double>> decayMap,
            Long userId,
            int topN
    ) {
        Set<Long> rated = matrix.getOrDefault(userId, Collections.emptyMap()).keySet();
        int poolSize = Math.max(topN * 5, 20);
        List<Recommendation> itemRecs = itemBasedCF.recommend(matrix, context.itemUserRatingMatrix(), userId, poolSize);
        Map<Long, Map<Long, Double>> decayed = applyDecayToMatrix(matrix, decayMap);
        List<Recommendation> userRecs = userBasedCF.recommend(decayed, userId, poolSize);

        Map<Long, Double> itemRankScore = rankScoreMap(itemRecs);
        Map<Long, Double> userRankScore = rankScoreMap(userRecs);
        Map<Long, Double> popScore = popularityScoreMap(context, poolSize, new HashSet<>(rated));
        Map<Long, Double> associationScore = associationScoreMap(matrix, decayMap, userId, poolSize);

        Set<Long> candidates = new HashSet<>();
        candidates.addAll(itemRankScore.keySet());
        candidates.addAll(userRankScore.keySet());
        candidates.addAll(popScore.keySet());
        candidates.addAll(associationScore.keySet());
        candidates.removeAll(rated);

        Set<Long> categoryLookupIds = new HashSet<>(candidates);
        categoryLookupIds.addAll(rated);
        Map<Long, String> categoryMap = context.loadCategoryMap(categoryLookupIds);
        Map<Long, Double> contentSimilarityScore = contentSimilarityScoreMap(matrix, decayMap, userId, candidates, categoryMap);
        Map<Long, Double> preferredCategoryBoost = preferredCategoryBoostMap(matrix, decayMap, userId, candidates, categoryMap);
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
        return merged.stream().sorted().limit(topN).toList();
    }

    /**
     * 基于行为的推荐
     *
     * <p>将显式评分转换为隐式行为强度，考虑评分值和时效性。
     * 行为强度公式：(0.4 + 0.6 × 评分/5.0) × (0.85 + 0.15 × 衰减权重)
     *
     * @param context 推荐上下文
     * @param matrix 用户-物品评分矩阵
     * @param decayMap 衰减权重矩阵
     * @param userId 用户ID
     * @param topN 推荐数量
     * @return 基于行为的推荐列表
     */
    private List<Recommendation> behaviorBasedRecommendations(
            RecommendationContext context,
            Map<Long, Map<Long, Double>> matrix,
            Map<Long, Map<Long, Double>> decayMap,
            Long userId,
            int topN
    ) {
        Map<Long, Map<Long, Double>> implicit = buildImplicitBehaviorMatrix(matrix, decayMap);
        int poolSize = Math.max(20, topN * 4);
        return itemBasedCF.recommend(implicit, context.buildItemUserRatingMatrix(implicit), userId, poolSize)
                .stream().sorted().limit(topN).toList();
    }

    /**
     * 构建隐式行为矩阵
     *
     * <p>将显式评分转换为隐式行为强度，综合考虑评分值和时效性。
     *
     * @param matrix 用户-物品评分矩阵
     * @param decayMap 衰减权重矩阵
     * @return 隐式行为强度矩阵
     */
    private Map<Long, Map<Long, Double>> buildImplicitBehaviorMatrix(
            Map<Long, Map<Long, Double>> matrix,
            Map<Long, Map<Long, Double>> decayMap
    ) {
        Map<Long, Map<Long, Double>> implicit = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Double>> userEntry : matrix.entrySet()) {
            Long userId = userEntry.getKey();
            Map<Long, Double> decays = decayMap.getOrDefault(userId, Collections.emptyMap());
            Map<Long, Double> row = new HashMap<>();
            for (Map.Entry<Long, Double> itemEntry : userEntry.getValue().entrySet()) {
                Long itemId = itemEntry.getKey();
                double score = Math.max(0.0, Math.min(5.0, itemEntry.getValue()));
                double decay = decays.getOrDefault(itemId, 1.0);
                // 提高显式评分对隐式强度的影响（从 0.4/0.6 -> 0.2/0.8），
                // 增强时效性影响（从 0.85/0.15 -> 0.4/0.6），并对近期交互做小幅额外提升。
                double base = 0.2 + 0.8 * (score / 5.0);
                double recencyFactor = 0.4 + 0.6 * decay;
                if (decay >= 0.8) { // 最近交互小幅放大
                    base *= 1.15;
                }
                double strength = base * recencyFactor;
                row.put(itemId, strength);
            }
            if (!row.isEmpty()) {
                implicit.put(userId, row);
            }
        }
        return implicit;
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
            Recommendation rec = recs.get(i);
            map.put(rec.getItemId(), Math.max(map.getOrDefault(rec.getItemId(), 0.0), 1.0 / (1.0 + i)));
        }
        return map;
    }

    /**
     * 计算热门物品得分映射
     *
     * @param context 推荐上下文
     * @param limit 限制数量
     * @param excluded 排除的物品集合
     * @return 物品ID到归一化热门得分的映射
     */
    private Map<Long, Double> popularityScoreMap(RecommendationContext context, int limit, Set<Long> excluded) {
        List<Recommendation> popular = popularFallback(context, limit, excluded);
        if (popular.isEmpty()) {
            return Map.of();
        }
        double max = popular.stream().mapToDouble(Recommendation::getScore).max().orElse(1.0);
        double normalizedMax = max <= 1e-12 ? 1.0 : max;
        Map<Long, Double> map = new HashMap<>();
        for (Recommendation rec : popular) {
            map.put(rec.getItemId(), rec.getScore() / normalizedMax);
        }
        return map;
    }

    /**
     * 计算物品关联得分映射
     *
     * <p>基于预计算的物品关联关系和用户评分，计算候选物品的关联得分。
     *
     * @param matrix 用户-物品评分矩阵
     * @param decayMap 衰减权重矩阵
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 物品ID到关联得分的映射
     */
    private Map<Long, Double> associationScoreMap(
            Map<Long, Map<Long, Double>> matrix,
            Map<Long, Map<Long, Double>> decayMap,
            Long userId,
            int limit
    ) {
        Map<Long, Double> userRatings = matrix.getOrDefault(userId, Collections.emptyMap());
        if (userRatings.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> userDecays = decayMap.getOrDefault(userId, Collections.emptyMap());
        Map<Long, Double> raw = new HashMap<>();
        for (Long ratedItem : userRatings.keySet()) {
            Map<Long, Double> neighbors = itemAssociationPrecomputeService.neighbors(ratedItem);
            if (neighbors.isEmpty()) {
                continue;
            }
            double userWeight = Math.max(0.0, userRatings.getOrDefault(ratedItem, 0.0) / 5.0)
                    * userDecays.getOrDefault(ratedItem, 1.0);
            for (Map.Entry<Long, Double> entry : neighbors.entrySet()) {
                Long candidate = entry.getKey();
                if (userRatings.containsKey(candidate)) {
                    continue;
                }
                raw.merge(candidate, entry.getValue() * userWeight, Double::sum);
            }
        }
        return normalizeAndLimit(raw, limit);
    }

    /**
     * 计算内容相似度得分映射
     *
     * <p>基于用户偏好类别和衰减权重计算候选物品的内容相似度得分。
     *
     * @param matrix 用户-物品评分矩阵
     * @param decayMap 衰减权重矩阵
     * @param userId 用户ID
     * @param candidates 候选物品集合
     * @param categoryMap 物品类别映射
     * @return 物品ID到内容相似度得分的映射
     */
    private Map<Long, Double> contentSimilarityScoreMap(
            Map<Long, Map<Long, Double>> matrix,
            Map<Long, Map<Long, Double>> decayMap,
            Long userId,
            Set<Long> candidates,
            Map<Long, String> categoryMap
    ) {
        Map<Long, Double> userRatings = matrix.getOrDefault(userId, Collections.emptyMap());
        Map<Long, Double> userDecays = decayMap.getOrDefault(userId, Collections.emptyMap());
        if (userRatings.isEmpty() || candidates.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> categoryPref = new HashMap<>();
        for (Map.Entry<Long, Double> entry : userRatings.entrySet()) {
            String category = categoryMap.get(entry.getKey());
            if (category == null || category.isBlank()) {
                continue;
            }
            categoryPref.merge(category, entry.getValue() * userDecays.getOrDefault(entry.getKey(), 1.0), Double::sum);
        }
        if (categoryPref.isEmpty()) {
            return Map.of();
        }
        double maxPref = categoryPref.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double normalizedMax = maxPref <= 1e-12 ? 1.0 : maxPref;
        Map<Long, Double> raw = new HashMap<>();
        for (Long itemId : candidates) {
            String category = categoryMap.get(itemId);
            if (category == null || category.isBlank()) {
                continue;
            }
            raw.put(itemId, categoryPref.getOrDefault(category, 0.0) / normalizedMax);
        }
        return raw;
    }

    /**
     * 计算偏好类别提升映射
     *
     * <p>识别用户偏好的类别（平均分≥4.0且加权评分数≥2），计算类别强度，
     * 用于提升该类别物品的推荐得分。考虑了评分的时效性。
     *
     * @param matrix 用户-物品评分矩阵
     * @param decayMap 衰减权重矩阵
     * @param userId 用户ID
     * @param candidates 候选物品集合
     * @param categoryMap 物品类别映射
     * @return 物品ID到类别提升系数的映射
     */
    private Map<Long, Double> preferredCategoryBoostMap(
            Map<Long, Map<Long, Double>> matrix,
            Map<Long, Map<Long, Double>> decayMap,
            Long userId,
            Set<Long> candidates,
            Map<Long, String> categoryMap
    ) {
        Map<Long, Double> userRatings = matrix.getOrDefault(userId, Collections.emptyMap());
        Map<Long, Double> userDecays = decayMap.getOrDefault(userId, Collections.emptyMap());
        if (userRatings.isEmpty() || candidates.isEmpty()) {
            return Map.of();
        }
        Map<String, double[]> stat = new HashMap<>();
        for (Map.Entry<Long, Double> entry : userRatings.entrySet()) {
            String category = categoryMap.get(entry.getKey());
            if (category == null || category.isBlank()) {
                continue;
            }
            double[] acc = stat.computeIfAbsent(category, ignored -> new double[2]);
            double recency = userDecays.getOrDefault(entry.getKey(), 1.0);
            acc[0] += entry.getValue() * recency;
            acc[1] += recency;
        }
        if (stat.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> categoryStrength = new HashMap<>();
        for (Map.Entry<String, double[]> entry : stat.entrySet()) {
            double avg = entry.getValue()[0] / entry.getValue()[1];
            double count = entry.getValue()[1];
            if (avg < 4.0 || count < 2.0) {
                continue;
            }
            double strength = Math.min(1.0, ((avg - 4.0) * 0.7) + Math.min(0.3, (count - 2.0) * 0.08));
            if (strength > 1e-12) {
                categoryStrength.put(entry.getKey(), strength);
            }
        }
        if (categoryStrength.isEmpty()) {
            return Map.of();
        }
        Map<Long, Double> boost = new HashMap<>();
        for (Long itemId : candidates) {
            String category = categoryMap.get(itemId);
            if (category == null || category.isBlank()) {
                continue;
            }
            boost.put(itemId, categoryStrength.getOrDefault(category, 0.0));
        }
        return boost;
    }

    /**
     * 动态计算混合权重（改进版 - 连续函数）
     *
     * <p>改进点：
     * <ul>
     *   <li>使用连续函数替代分段函数，实现平滑过渡</li>
     *   <li>基于sigmoid函数计算权重，避免边界跳变</li>
     *   <li>根据用户活跃度自适应调整各策略权重</li>
     *   <li>权重比例基于实验数据优化</li>
     * </ul>
     *
     * @param ratedCount 用户历史评分数量
     * @return 包含各策略权重的HybridWeights对象
     */
    private HybridWeights dynamicWeights(int ratedCount) {
        double normalizedActivity = Math.min(1.0, ratedCount / 30.0);

        double sigmoid = 1.0 / (1.0 + Math.exp(-10.0 * (normalizedActivity - 0.4)));

        double itemCfWeight = 0.30 + 0.15 * sigmoid;
        double userCfWeight = 0.15 + 0.15 * sigmoid;
        double popularityWeight = 0.25 - 0.15 * sigmoid;
        double associationWeight = 0.10 + 0.02 * sigmoid;
        double contentWeight = 0.20 - 0.15 * sigmoid;

        double total = itemCfWeight + userCfWeight + popularityWeight + associationWeight + contentWeight;
        itemCfWeight /= total;
        userCfWeight /= total;
        popularityWeight /= total;
        associationWeight /= total;
        contentWeight /= total;

        return new HybridWeights(itemCfWeight, userCfWeight, popularityWeight, associationWeight, contentWeight);
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
        double normalizedMax = max <= 1e-12 ? 1.0 : max;
        return raw.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(Math.max(1, limit))
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue() / normalizedMax), Map::putAll);
    }

    /**
     * 多样化推荐结果（改进版 - MMR算法）
     *
     * <p>改进点：
     * <ul>
     *   <li>实现MMR（Maximal Marginal Relevance）算法</li>
     *   <li>平衡相关性与多样性，使用可调节的λ参数</li>
     *   <li>基于类别相似度计算物品间冗余度</li>
     *   <li>动态调整多样性权重，根据推荐位置自适应</li>
     * </ul>
     *
     * <p>MMR公式：MMR = λ × relevance - (1-λ) × max(similarity to selected)
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

        double lambda = 0.7;

        List<Recommendation> pool = new ArrayList<>(ranked);
        List<Recommendation> selected = new ArrayList<>();

        if (!pool.isEmpty()) {
            Recommendation first = pool.stream()
                    .max(Comparator.comparingDouble(Recommendation::getScore))
                    .orElse(null);
            if (first != null) {
                selected.add(first);
                pool.remove(first);
            }
        }

        while (!pool.isEmpty() && selected.size() < topN) {
            Recommendation best = null;
            double bestMmr = Double.NEGATIVE_INFINITY;

            for (Recommendation candidate : pool) {
                double relevance = candidate.getScore();

                double maxSimilarity = 0.0;
                String candidateCategory = categoryMap.getOrDefault(candidate.getItemId(), "");

                for (Recommendation selectedRec : selected) {
                    String selectedCategory = categoryMap.getOrDefault(selectedRec.getItemId(), "");
                    double similarity = candidateCategory.equals(selectedCategory) ? 0.8 : 0.1;
                    maxSimilarity = Math.max(maxSimilarity, similarity);
                }

                double positionWeight = 1.0 - 0.1 * Math.min(selected.size(), 5);
                double mmr = lambda * relevance * positionWeight - (1.0 - lambda) * maxSimilarity;

                if (mmr > bestMmr) {
                    bestMmr = mmr;
                    best = candidate;
                }
            }

            if (best == null) {
                break;
            }

            selected.add(best);
            pool.remove(best);
        }

        return selected;
    }

    /**
     * 计算衰减权重
     *
     * <p>基于时间差计算衰减权重，使用半衰期30天的指数衰减。
     * 公式：0.5^(天数/30)
     *
     * @param now 当前时间
     * @param ratedAt 评分时间
     * @return 衰减权重，范围0.0-1.0
     */
    private double decayWeight(Instant now, Instant ratedAt) {
        if (ratedAt == null) {
            return 1.0;
        }
        long days = Math.max(0L, Duration.between(ratedAt, now).toDays());
        return Math.pow(0.5, ((double) days) / 30.0);
    }

    /**
     * 计算类别偏好映射
     *
     * <p>基于用户评分和衰减权重计算各类别的偏好强度。
     *
     * @param userRatings 用户评分映射
     * @param userDecays 用户衰减权重映射
     * @param categoryMap 物品类别映射
     * @return 类别到偏好强度的映射
     */
    private Map<String, Double> categoryPreferenceMap(
            Map<Long, Double> userRatings,
            Map<Long, Double> userDecays,
            Map<Long, String> categoryMap
    ) {
        if (userRatings.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> pref = new HashMap<>();
        for (Map.Entry<Long, Double> entry : userRatings.entrySet()) {
            String category = categoryMap.get(entry.getKey());
            if (category == null || category.isBlank()) {
                continue;
            }
            double score = Math.max(0.0, Math.min(5.0, entry.getValue())) / 5.0;
            pref.merge(category, score * userDecays.getOrDefault(entry.getKey(), 1.0), Double::sum);
        }
        return pref;
    }

    /**
     * 获取热门商品推荐列表
     *
     * <p>基于全站评分数量和评分均值计算热门商品，考虑时间衰减。
     *
     * @param topN 需要的推荐数量
     * @return 热门商品推荐列表
     */
    @Transactional(readOnly = true)
    public List<Recommendation> getPopularItems(int topN) {
        if (topN <= 0) {
            return List.of();
        }
        RecommendationContext context = new RecommendationContext();
        return popularFallback(context, topN, new HashSet<>());
    }

    /**
     * 获取指定分类的热门商品推荐列表
     *
     * @param category 分类名称
     * @param topN 需要的推荐数量
     * @return 指定分类的热门商品推荐列表
     */
    @Transactional(readOnly = true)
    public List<Recommendation> getPopularItemsByCategory(String category, int topN) {
        if (topN <= 0 || category == null || category.trim().isBlank()) {
            return List.of();
        }
        RecommendationContext context = new RecommendationContext();
        List<Recommendation> popular = popularFallback(context, topN * 2, new HashSet<>());
        
        Map<Long, String> categoryMap = context.loadCategoryMap(
            popular.stream()
                .map(Recommendation::getItemId)
                .collect(java.util.stream.Collectors.toSet())
        );
        
        return popular.stream()
                .filter(rec -> category.equals(categoryMap.get(rec.getItemId())))
                .limit(topN)
                .toList();
    }

    /**
     * 获取多样性优化的推荐列表
     *
     * <p>使用MMR算法平衡相关性与多样性，返回多样化的推荐结果。
     *
     * @param userId 用户ID
     * @param topN 需要的推荐数量
     * @param type 算法类型
     * @param diversityLevel 多样性级别（0.0-1.0，越高越多样）
     * @return 多样性优化后的推荐列表
     */
    @Transactional(readOnly = true)
    public List<RecommendationResult> recommendWithDiversity(Long userId, int topN, AlgorithmType type, double diversityLevel) {
        if (userId == null || topN <= 0) {
            return List.of();
        }
        
        RecommendationContext context = new RecommendationContext();
        AlgorithmType safeType = type == null ? AlgorithmType.USER_BASED : type;
        
        // 获取初始推荐列表（取更多候选）
        int poolSize = Math.max(topN * 3, 50);
        List<Recommendation> initialRecs = recommendForUser(context, userId, poolSize, safeType);
        
        if (initialRecs.isEmpty()) {
            return List.of();
        }
        
        // 应用多样性优化
        Set<Long> itemIds = initialRecs.stream()
                .map(Recommendation::getItemId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, String> categoryMap = context.loadCategoryMap(itemIds);
        
        // 根据多样性级别调整lambda参数
        double lambda = 1.0 - diversityLevel * 0.4; // lambda: 0.6-1.0
        List<Recommendation> diversified = diversifyRecommendationsWithLambda(initialRecs, topN, categoryMap, lambda);
        
        // 生成推荐理由
        Map<Long, Map<Long, Double>> matrix = context.userItemMatrix();
        Map<Long, Double> userRatings = matrix.getOrDefault(userId, Collections.emptyMap());
        Map<String, Double> pref = categoryPreferenceMap(userRatings, Collections.emptyMap(), categoryMap);
        String topCategory = pref.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        
        return diversified.stream()
                .map(rec -> {
                    String reason = buildReason(safeType, rec.getItemId(), rec.getScore(), categoryMap, topCategory, 0.0, userRatings.keySet());
                    return new RecommendationResult(rec.getItemId(), rec.getScore(), reason);
                })
                .toList();
    }

    /**
     * 使用指定lambda参数的MMR多样化算法
     */
    private List<Recommendation> diversifyRecommendationsWithLambda(
            List<Recommendation> ranked,
            int topN,
            Map<Long, String> categoryMap,
            double lambda
    ) {
        if (ranked.isEmpty()) {
            return ranked;
        }

        List<Recommendation> pool = new ArrayList<>(ranked);
        List<Recommendation> selected = new ArrayList<>();

        // 选择得分最高的第一个
        if (!pool.isEmpty()) {
            Recommendation first = pool.stream()
                    .max(Comparator.comparingDouble(Recommendation::getScore))
                    .orElse(null);
            if (first != null) {
                selected.add(first);
                pool.remove(first);
            }
        }

        while (!pool.isEmpty() && selected.size() < topN) {
            Recommendation best = null;
            double bestMmr = Double.NEGATIVE_INFINITY;

            for (Recommendation candidate : pool) {
                double relevance = candidate.getScore();

                double maxSimilarity = 0.0;
                String candidateCategory = categoryMap.getOrDefault(candidate.getItemId(), "");

                for (Recommendation selectedRec : selected) {
                    String selectedCategory = categoryMap.getOrDefault(selectedRec.getItemId(), "");
                    double similarity = candidateCategory.equals(selectedCategory) ? 0.8 : 0.1;
                    maxSimilarity = Math.max(maxSimilarity, similarity);
                }

                double positionWeight = 1.0 - 0.1 * Math.min(selected.size(), 5);
                double mmr = lambda * relevance * positionWeight - (1.0 - lambda) * maxSimilarity;

                if (mmr > bestMmr) {
                    bestMmr = mmr;
                    best = candidate;
                }
            }

            if (best == null) {
                break;
            }

            selected.add(best);
            pool.remove(best);
        }

        return selected;
    }

    /**
     * 构建推荐理由
     *
     * <p>根据算法类型、类别匹配情况和得分生成人性化的推荐理由。
     *
     * @param type 算法类型
     * @param itemId 物品ID
     * @param score 推荐得分
     * @param categoryMap 物品类别映射
     * @param topCategory 用户最偏好的类别
     * @param topStrength 偏好强度
     * @param ratedItemIds 用户已评分物品集合
     * @return 推荐理由文本
     */
    private String buildReason(
            AlgorithmType type,
            Long itemId,
            double score,
            Map<Long, String> categoryMap,
            String topCategory,
            double topStrength,
            Set<Long> ratedItemIds
    ) {
        String candidateCategory = categoryMap.get(itemId);
        boolean categoryMatch = topCategory != null && topCategory.equalsIgnoreCase(candidateCategory);
        if (type == AlgorithmType.HYBRID) {
            if (categoryMatch && topStrength >= 1.2) {
                return "混合推荐：你最近对 " + topCategory + " 类目偏好明显，这个商品在相似用户中也很受欢迎";
            }
            if (score >= 0.7) {
                return "混合推荐：结合了相似用户、相似商品和全站热度，综合得分靠前";
            }
            return "混合推荐：综合多种信号后，将它作为当前值得探索的候选";
        }
        if (type == AlgorithmType.BEHAVIOR_BASED) {
            if (categoryMatch) {
                return "行为推荐：基于你的近期行为轨迹，优先补充你常互动的 " + topCategory + " 类目";
            }
            return "行为推荐：依据你的历史互动强度和时效性排序得到";
        }
        if (type == AlgorithmType.ITEM_BASED) {
            return "评分推荐：它和你高分评价过的商品相似度较高";
        }
        if (ratedItemIds.isEmpty()) {
            return "评分推荐：基于相似用户偏好给出的冷启动候选";
        }
        return "评分推荐：与你评分模式接近的用户也偏好这个商品";
    }

    /**
     * 推荐上下文类
     *
     * <p>用于缓存推荐过程中的中间计算结果，避免重复计算。
     * 采用懒加载策略，只在首次访问时计算。
     */
    private final class RecommendationContext {
        /** 用户-物品评分矩阵 */
        private Map<Long, Map<Long, Double>> userItemMatrix;

        /** 用户-物品衰减权重矩阵 */
        private Map<Long, Map<Long, Double>> userItemDecayMap;

        /** 物品-用户评分矩阵 */
        private Map<Long, Map<Long, Double>> itemUserRatingMatrix;

        /** 热门物品排名列表 */
        private List<Recommendation> popularityRanking;

        /** 全量商品缓存（仅在冷启动补齐路径加载） */
        private List<Item> allItems;

        /** 物品类别缓存 */
        private final Map<Long, String> categoryCache = new HashMap<>();

        /**
         * 获取用户-物品评分矩阵（懒加载）
         *
         * @return 用户-物品评分矩阵
         */
        Map<Long, Map<Long, Double>> userItemMatrix() {
            if (userItemMatrix == null) {
                userItemMatrix = buildUserItemMatrix();
            }
            return userItemMatrix;
        }

        /**
         * 获取用户-物品衰减权重矩阵（懒加载）
         *
         * @return 用户-物品衰减权重矩阵
         */
        Map<Long, Map<Long, Double>> userItemDecayMap() {
            if (userItemDecayMap == null) {
                userItemDecayMap = buildUserItemDecayMap();
            }
            return userItemDecayMap;
        }

        /**
         * 获取物品-用户评分矩阵（懒加载）
         *
         * @return 物品-用户评分矩阵
         */
        Map<Long, Map<Long, Double>> itemUserRatingMatrix() {
            if (itemUserRatingMatrix == null) {
                itemUserRatingMatrix = buildItemUserRatingMatrix(userItemMatrix());
            }
            return itemUserRatingMatrix;
        }

        /**
         * 获取热门物品排名（懒加载）
         *
         * <p>基于物品平均评分和评分数量计算热门度。
         * 热门度公式：平均分 × log(1 + 评分次数)
         *
         * @return 按热门度排序的推荐列表
         */
        List<Recommendation> popularityRanking() {
            if (popularityRanking == null) {
                popularityRanking = new ArrayList<>();
                for (RatingRepository.ItemPopularityStatView row : ratingRepository.findItemPopularityStats()) {
                    Long itemId = row.getItemId();
                    Double avg = row.getAvgScore();
                    Long count = row.getRatingCount();
                    if (itemId == null || avg == null || count == null) {
                        continue;
                    }
                    popularityRanking.add(new Recommendation(itemId, avg * Math.log1p(count)));
                }
                popularityRanking = popularityRanking.stream().sorted().toList();
            }
            return popularityRanking;
        }

        List<Item> allItems() {
            if (allItems == null) {
                List<Item> buffer = new ArrayList<>();
                for (Item item : itemRepository.findAll()) {
                    if (item != null && item.getId() != null) {
                        buffer.add(item);
                    }
                }
                buffer.sort(Comparator.comparing(Item::getId));
                allItems = List.copyOf(buffer);
            }
            return allItems;
        }

        /**
         * 加载物品类别映射（带缓存）
         *
         * @param itemIds 物品ID集合
         * @return 物品ID到类别的映射
         */
        Map<Long, String> loadCategoryMap(Set<Long> itemIds) {
            if (itemIds.isEmpty()) {
                return Map.of();
            }
            Set<Long> missing = new HashSet<>();
            for (Long itemId : itemIds) {
                if (!categoryCache.containsKey(itemId)) {
                    missing.add(itemId);
                }
            }
            if (!missing.isEmpty()) {
                Iterable<Item> items = itemRepository.findAllById(missing);
                for (Item item : items) {
                    categoryCache.put(item.getId(), item.getCategory());
                }
            }
            Map<Long, String> result = new HashMap<>();
            for (Long itemId : itemIds) {
                if (categoryCache.containsKey(itemId)) {
                    result.put(itemId, categoryCache.get(itemId));
                }
            }
            return result;
        }

        /**
         * 构建物品-用户评分矩阵
         *
         * @param source 源矩阵（用户-物品或隐式行为矩阵）
         * @return 物品-用户评分矩阵
         */
        Map<Long, Map<Long, Double>> buildItemUserRatingMatrix(Map<Long, Map<Long, Double>> source) {
            Map<Long, Map<Long, Double>> itemUsers = new HashMap<>();
            for (Map.Entry<Long, Map<Long, Double>> userEntry : source.entrySet()) {
                Long userId = userEntry.getKey();
                for (Map.Entry<Long, Double> itemEntry : userEntry.getValue().entrySet()) {
                    itemUsers.computeIfAbsent(itemEntry.getKey(), ignored -> new HashMap<>()).put(userId, itemEntry.getValue());
                }
            }
            return itemUsers;
        }
    }

    /**
     * 混合推荐权重记录
     *
     * @param itemCf 基于物品的协同过滤权重
     * @param userCf 基于用户的协同过滤权重
     * @param popularity 热门物品权重
     * @param association 物品关联权重
     * @param content 内容相似度权重
     */
    private record HybridWeights(double itemCf, double userCf, double popularity, double association, double content) {
    }

    /**
     * 推荐结果记录（带推荐理由）
     *
     * @param itemId 物品ID
     * @param score 推荐得分
     * @param reason 推荐理由文本
     */
    public record RecommendationResult(Long itemId, double score, String reason) implements java.io.Serializable {
    }
}
