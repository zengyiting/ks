package com.example.recommend.web;

import com.example.recommend.model.Item;
import com.example.recommend.repository.ItemRepository;
import com.example.recommend.service.AlgorithmType;
import com.example.recommend.service.RecommendationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

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
