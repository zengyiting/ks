package com.example.recommend.service;

import com.example.recommend.model.Item;
import com.example.recommend.model.Rating;
import com.example.recommend.model.User;
import com.example.recommend.model.UserItemFlag;
import com.example.recommend.repository.ItemRepository;
import com.example.recommend.repository.RatingRepository;
import com.example.recommend.repository.UserItemFlagRepository;
import com.example.recommend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户行为服务类
 *
 * <p>提供用户行为记录和查询功能，包括：
 * <ul>
 *   <li>用户和商品的搜索查询</li>
 *   <li>用户评分记录的查询和管理</li>
 *   <li>用户行为（点击、收藏、加购）的记录和映射</li>
 * </ul>
 * 支持将不同行为类型映射为相应的评分值，用于推荐算法计算。
 */
@Service
public class BehaviorService {
    /** 批量评分写入上限，避免单次请求过大导致事务压力过高 */
    private static final int BATCH_LIMIT = 1000;

    /** 用户数据访问层 */
    private final UserRepository userRepository;

    /** 商品数据访问层 */
    private final ItemRepository itemRepository;

    /** 评分数据访问层 */
    private final RatingRepository ratingRepository;

    /** 用户收藏/加购标记仓库 */
    private final UserItemFlagRepository userItemFlagRepository;

    /** 商品关联预计算服务，用于在评分更新时标记缓存失效 */
    private final ItemAssociationPrecomputeService itemAssociationPrecomputeService;

    /** 推荐缓存管理服务，用于行为更新后淘汰用户推荐缓存 */
    private final RecommendationCacheService recommendationCacheService;

    /** 收藏排行榜服务 */
    private final RankingService rankingService;

    /**
     * 构造函数，注入依赖
     *
     * @param userRepository 用户仓库
     * @param itemRepository 商品仓库
     * @param ratingRepository 评分仓库
     * @param itemAssociationPrecomputeService 商品关联预计算服务
     */
    public BehaviorService(
            UserRepository userRepository,
            ItemRepository itemRepository,
            RatingRepository ratingRepository,
            ItemAssociationPrecomputeService itemAssociationPrecomputeService,
            RecommendationCacheService recommendationCacheService,
            UserItemFlagRepository userItemFlagRepository,
            RankingService rankingService
    ) {
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.ratingRepository = ratingRepository;
        this.itemAssociationPrecomputeService = itemAssociationPrecomputeService;
        this.recommendationCacheService = recommendationCacheService;
        this.userItemFlagRepository = userItemFlagRepository;
        this.rankingService = rankingService;
    }

    /**
     * 搜索用户列表
     *
     * <p>根据关键词搜索用户，最多返回50条结果，按ID升序排列。
     * 如果不提供关键词则返回前50个用户。
     *
     * @param keyword 搜索关键词，可为null或空字符串
     * @return 用户列表，最多50条记录
     */
    @Transactional(readOnly = true)
    public List<User> findUsers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return userRepository.findTop50ByDisabledFalseOrderByIdAsc();
        }
        return userRepository.findTop50ByUsernameContainingIgnoreCaseAndDisabledFalseOrderByIdAsc(keyword.trim());
    }

    /**
     * 搜索商品列表
     *
     * <p>根据关键词搜索商品，最多返回100条结果，按ID升序排列。
     * 如果不提供关键词则返回前100个商品。
     *
     * @param keyword 搜索关键词，可为null或空字符串
     * @return 商品列表，最多100条记录
     */
    @Transactional(readOnly = true)
    public List<Item> findItems(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return itemRepository.findTop100ByOrderByIdAsc();
        }
        return itemRepository.findTop100ByNameContainingIgnoreCaseOrderByIdAsc(keyword.trim());
    }

    /**
     * 查询用户的评分记录
     *
     * <p>获取指定用户的所有评分记录，包含商品详细信息。
     * 如果用户ID无效则返回空列表。
     *
     * @param userId 用户ID，必须大于0
     * @return 评分记录列表，包含评分ID、商品信息、评分分数和时间
     */
    @Transactional(readOnly = true)
    public List<RatingRow> findUserRatings(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        return ratingRepository.findUserRatingsWithItem(userId).stream()
                .map(row -> new RatingRow(
                        row.getRatingId(),
                        row.getItemId(),
                        row.getItemName(),
                        row.getCategory(),
                        row.getScore() == null ? 0.0 : row.getScore(),
                        row.getRatedAt() == null ? null : row.getRatedAt().toString()
                ))
                .toList();
    }

    /**
     * 创建或更新用户评分
     *
     * <p>为指定用户对商品的评分进行新增或更新操作。
     * 评分范围限制在0.0-5.0之间。更新后会标记商品关联预计算缓存失效。
     *
     * @param userId 用户ID，必须大于0
     * @param itemId 商品ID，必须大于0
     * @param score 评分值，会自动限制在0.0-5.0范围内
     * @return 包含评分详情和商品信息的RatingRow对象
     * @throws ResponseStatusException 当用户ID、商品ID无效或用户/商品不存在时抛出异常
     */
    @Transactional
    public RatingRow upsertRating(Long userId, Long itemId, double score) {
        if (userId == null || userId <= 0 || itemId == null || itemId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId 和 itemId 必须为正整数");
        }
        double safeScore = Math.max(0.0, Math.min(5.0, score));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (user.isDisabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已禁用");
        }
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        Rating rating = ratingRepository.findByUserAndItem(user, item).orElseGet(() -> new Rating(user, item, safeScore));
        rating.setScore(safeScore);
        rating.setRatedAt(Instant.now());
        Rating saved = ratingRepository.save(rating);
        itemAssociationPrecomputeService.markDirty();
        recommendationCacheService.invalidateUser(userId);
        return new RatingRow(
                saved.getId(),
                item.getId(),
                item.getName(),
                item.getCategory(),
                saved.getScore(),
                saved.getRatedAt() == null ? null : saved.getRatedAt().toString()
        );
    }

    /**
     * 批量导入评分
     *
     * <p>单次事务内写入多条评分，并对受影响用户做一次版本失效。</p>
     */
    @Transactional
    public BatchUpsertResult upsertRatingsBatch(List<UpsertRatingCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批量评分不能为空");
        }
        if (commands.size() > BATCH_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单次批量评分不能超过 " + BATCH_LIMIT + " 条");
        }

        Set<Long> userIds = new HashSet<>();
        Set<Long> itemIds = new HashSet<>();
        for (int i = 0; i < commands.size(); i++) {
            UpsertRatingCommand command = commands.get(i);
            if (command == null || command.userId() == null || command.userId() <= 0
                    || command.itemId() == null || command.itemId() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "第 " + (i + 1) + " 条记录的 userId/itemId 非法");
            }
            userIds.add(command.userId());
            itemIds.add(command.itemId());
        }

        Map<Long, User> userMap = new HashMap<>();
        for (User user : userRepository.findAllById(userIds)) {
            userMap.put(user.getId(), user);
        }
        Map<Long, Item> itemMap = new HashMap<>();
        for (Item item : itemRepository.findAllById(itemIds)) {
            itemMap.put(item.getId(), item);
        }

        List<Rating> toSave = new ArrayList<>(commands.size());
        Instant now = Instant.now();
        Set<Long> affectedUsers = new HashSet<>();
        for (int i = 0; i < commands.size(); i++) {
            UpsertRatingCommand command = commands.get(i);
            User user = userMap.get(command.userId());
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "第 " + (i + 1) + " 条记录用户不存在");
            }
            if (user.isDisabled()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "第 " + (i + 1) + " 条记录用户已禁用");
            }
            Item item = itemMap.get(command.itemId());
            if (item == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "第 " + (i + 1) + " 条记录商品不存在");
            }
            double safeScore = Math.max(0.0, Math.min(5.0, command.score()));
            Rating rating = ratingRepository.findByUserAndItem(user, item)
                    .orElseGet(() -> new Rating(user, item, safeScore));
            rating.setScore(safeScore);
            rating.setRatedAt(now);
            toSave.add(rating);
            affectedUsers.add(user.getId());
        }

        List<Rating> saved = ratingRepository.saveAll(toSave);
        itemAssociationPrecomputeService.markDirty();
        recommendationCacheService.invalidateUsers(affectedUsers);
        return new BatchUpsertResult(saved.size(), affectedUsers.size());
    }

    /**
     * 记录用户行为并映射为评分
     *
     * <p>将用户的不同行为类型（点击、加购、收藏）映射为相应的评分值：
     * <ul>
     *   <li>click（点击）→ 2.2分（弱偏好）</li>
     *   <li>cart（加购）→ 3.8分（中强偏好）</li>
     *   <li>favorite（收藏）→ 4.5分（强偏好）</li>
     * </ul>
     * 内部调用upsertRating方法保存评分记录。
     *
     * @param userId 用户ID，必须大于0
     * @param itemId 商品ID，必须大于0
     * @param action 行为类型，仅支持click/favorite/cart
     * @return 包含行为记录详情的BehaviorWriteResult对象
     * @throws ResponseStatusException 当行为类型为空或不支持时抛出异常
     */
    @Transactional
    public BehaviorWriteResult recordBehavior(Long userId, Long itemId, String action) {
        if (action == null || action.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action 不能为空，支持 click/favorite/cart");
        }
        String normalized = action.trim().toLowerCase();
        double mappedScore = switch (normalized) {
            case "view" -> 1.6;
            case "click" -> 2.2;
            case "cart" -> 3.8;
            case "favorite" -> 4.5;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action 仅支持 view/click/favorite/cart");
        };
        RatingRow row = upsertRating(userId, itemId, mappedScore);
        if ("favorite".equals(normalized) || "cart".equals(normalized)) {
            touchFlag(userId, itemId, normalized);
        }
        String message = switch (normalized) {
            case "view" -> "已记录浏览行为";
            case "click" -> "已记录点击行为，映射为弱偏好";
            case "cart" -> "已记录加购行为，映射为中强偏好";
            default -> "已记录收藏行为，映射为强偏好";
        };
        return new BehaviorWriteResult(row.ratingId(), row.itemId(), row.itemName(), row.category(), row.score(), row.ratedAt(), normalized, message);
    }

    private void touchFlag(Long userId, Long itemId, String action) {
        UserItemFlag flag = userItemFlagRepository
                .findByUserIdAndItemId(userId, itemId)
                .orElseGet(() -> new UserItemFlag(userId, itemId));
        if ("favorite".equals(action)) {
            boolean wasFavorite = flag.isFavorite();
            flag.setFavorite(true);
            if (!wasFavorite) {
                rankingService.incrementFavorite(itemId);
            }
        }
        if ("cart".equals(action)) {
            flag.setInCart(true);
        }
        flag.setUpdatedAt(Instant.now());
        userItemFlagRepository.save(flag);
    }

    /**
     * 评分记录数据传输对象
     *
     * @param ratingId 评分记录ID
     * @param itemId 商品ID
     * @param itemName 商品名称
     * @param category 商品分类
     * @param score 评分分数
     * @param ratedAt 评分时间字符串
     */
    public record RatingRow(
            Long ratingId,
            Long itemId,
            String itemName,
            String category,
            double score,
            String ratedAt
    ) {
    }

    /**
     * 行为记录写入结果数据传输对象
     *
     * @param ratingId 评分记录ID
     * @param itemId 商品ID
     * @param itemName 商品名称
     * @param category 商品分类
     * @param score 映射后的评分分数
     * @param ratedAt 评分时间字符串
     * @param action 行为类型（click/favorite/cart）
     * @param message 行为记录的描述信息
     */
    public record BehaviorWriteResult(
            Long ratingId,
            Long itemId,
            String itemName,
            String category,
            double score,
            String ratedAt,
            String action,
            String message
    ) {
    }

    public record UpsertRatingCommand(Long userId, Long itemId, double score) {
    }

    public record BatchUpsertResult(int updatedCount, int affectedUserCount) {
    }
}
