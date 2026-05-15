package com.example.recommend.web;

import com.example.recommend.algo.Recommendation;
import com.example.recommend.model.Item;
import com.example.recommend.repository.ItemRepository;
import com.example.recommend.service.AlgorithmType;
import com.example.recommend.service.RecommendationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 推荐接口：GET /api/recommendations/{userId}?n=5&algo=user|item|behavior|hybrid
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;
    private final ItemRepository itemRepository;

    public RecommendationController(RecommendationService recommendationService, ItemRepository itemRepository) {
        this.recommendationService = recommendationService;
        this.itemRepository = itemRepository;
    }

    @GetMapping("/{userId}")
    public List<RecommendationDto> recommend(
            @PathVariable Long userId,
            @RequestParam(name = "n", defaultValue = "5") int n,
            @RequestParam(name = "algo", defaultValue = "user") String algo
    ) {
        int safeTopN = Math.max(1, Math.min(n, 100));
        AlgorithmType type = parseAlgo(algo);
        List<RecommendationService.RecommendationResult> recs = recommendationService.recommendForUserWithReason(userId, safeTopN, type);
        Set<Long> itemIds = recs.stream().map(RecommendationService.RecommendationResult::itemId).collect(Collectors.toSet());
        Map<Long, Item> itemMap = toItemMap(itemRepository.findAllById(itemIds));
        return recs.stream().map(r -> {
            Item item = itemMap.get(r.itemId());
            String name = item == null ? "Item#" + r.itemId() : item.getName();
            String category = item == null ? null : item.getCategory();
            String imageUrl = item == null ? null : item.getImageUrl();
            return new RecommendationDto(r.itemId(), name, category, imageUrl, r.score(), r.reason());
        }).collect(Collectors.toList());
    }

    /**
     * 获取热门商品推荐
     *
     * @param n 返回数量，默认10
     * @param category 分类筛选（可选）
     * @return 热门商品推荐列表
     */
    @GetMapping("/popular")
    public List<RecommendationDto> getPopularItems(
            @RequestParam(name = "n", defaultValue = "10") int n,
            @RequestParam(name = "category", required = false) String category) {
        int safeTopN = Math.max(1, Math.min(n, 100));
        
        List<Recommendation> recs;
        if (category != null && !category.trim().isBlank()) {
            recs = recommendationService.getPopularItemsByCategory(category.trim(), safeTopN);
        } else {
            recs = recommendationService.getPopularItems(safeTopN);
        }
        
        Set<Long> itemIds = recs.stream().map(Recommendation::getItemId).collect(Collectors.toSet());
        Map<Long, Item> itemMap = toItemMap(itemRepository.findAllById(itemIds));
        
        return recs.stream().map(r -> {
            Item item = itemMap.get(r.getItemId());
            String name = item == null ? "Item#" + r.getItemId() : item.getName();
            String itemCategory = item == null ? null : item.getCategory();
            String imageUrl = item == null ? null : item.getImageUrl();
            return new RecommendationDto(r.getItemId(), name, itemCategory, imageUrl, r.getScore(), "热门推荐");
        }).collect(Collectors.toList());
    }

    /**
     * 获取多样性优化的推荐列表
     *
     * @param userId 用户ID
     * @param n 返回数量，默认5
     * @param algo 算法类型，默认user
     * @param diversity 多样性级别（0.0-1.0），默认0.5
     * @return 多样性优化的推荐列表
     */
    @GetMapping("/{userId}/diverse")
    public List<RecommendationDto> recommendWithDiversity(
            @PathVariable Long userId,
            @RequestParam(name = "n", defaultValue = "5") int n,
            @RequestParam(name = "algo", defaultValue = "user") String algo,
            @RequestParam(name = "diversity", defaultValue = "0.5") double diversity) {
        int safeTopN = Math.max(1, Math.min(n, 100));
        double safeDiversity = Math.max(0.0, Math.min(1.0, diversity));
        AlgorithmType type = parseAlgo(algo);
        
        List<RecommendationService.RecommendationResult> recs = recommendationService.recommendWithDiversity(userId, safeTopN, type, safeDiversity);
        
        Set<Long> itemIds = recs.stream().map(RecommendationService.RecommendationResult::itemId).collect(Collectors.toSet());
        Map<Long, Item> itemMap = toItemMap(itemRepository.findAllById(itemIds));
        
        return recs.stream().map(r -> {
            Item item = itemMap.get(r.itemId());
            String name = item == null ? "Item#" + r.itemId() : item.getName();
            String category = item == null ? null : item.getCategory();
            String imageUrl = item == null ? null : item.getImageUrl();
            return new RecommendationDto(r.itemId(), name, category, imageUrl, r.score(), r.reason());
        }).collect(Collectors.toList());
    }

    private AlgorithmType parseAlgo(String algo) {
        if (algo == null || algo.isBlank() || "user".equalsIgnoreCase(algo)) {
            return AlgorithmType.USER_BASED;
        }
        if ("item".equalsIgnoreCase(algo)) {
            return AlgorithmType.ITEM_BASED;
        }
        if ("behavior".equalsIgnoreCase(algo)) {
            return AlgorithmType.BEHAVIOR_BASED;
        }
        if ("hybrid".equalsIgnoreCase(algo)) {
            return AlgorithmType.HYBRID;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "algo 参数只支持 user、item、behavior 或 hybrid");
    }

    private Map<Long, Item> toItemMap(Iterable<Item> items) {
        return StreamSupport.stream(items.spliterator(), false)
                .collect(Collectors.toMap(Item::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * 推荐返回 DTO
     */
    public record RecommendationDto(Long itemId, String name, String category, String imageUrl, double score, String reason) {}
}
