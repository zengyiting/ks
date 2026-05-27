package com.example.recommend.web;

import com.example.recommend.model.Item;
import com.example.recommend.model.UserItemFlag;
import com.example.recommend.repository.ItemRepository;
import com.example.recommend.repository.RatingRepository;
import com.example.recommend.repository.UserItemFlagRepository;
import com.example.recommend.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final RatingRepository ratingRepository;
    private final UserItemFlagRepository userItemFlagRepository;
    private final com.example.recommend.service.RankingService rankingService;

    public CatalogController(
            UserRepository userRepository,
            ItemRepository itemRepository,
            RatingRepository ratingRepository,
            UserItemFlagRepository userItemFlagRepository,
            com.example.recommend.service.RankingService rankingService
    ) {
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.ratingRepository = ratingRepository;
        this.userItemFlagRepository = userItemFlagRepository;
        this.rankingService = rankingService;
    }

    @GetMapping("/users")
    public List<UserDto> users(@RequestParam(name = "keyword", required = false) String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        if (k.isBlank()) {
            return userRepository.findTop50ByDisabledFalseOrderByIdAsc().stream()
                    .map(u -> new UserDto(u.getId(), u.getUsername()))
                    .toList();
        }
        return userRepository.findTop50ByUsernameContainingIgnoreCaseAndDisabledFalseOrderByIdAsc(k).stream()
                .map(u -> new UserDto(u.getId(), u.getUsername()))
                .toList();
    }

    @GetMapping("/items")
    public List<ItemDto> items(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "limit", defaultValue = "24") int limit
    ) {
        int size = Math.max(1, Math.min(limit, 100));
        Pageable pageable = PageRequest.of(0, size, Sort.by(Sort.Direction.ASC, "id"));
        List<Item> items;
        if (keyword == null || keyword.isBlank()) {
            items = itemRepository.findAll(pageable).getContent();
        } else {
            items = itemRepository.findByNameContainingIgnoreCase(keyword.trim(), pageable).getContent();
        }
        Map<Long, RatingStat> stats = ratingStats();
        return items.stream()
                .map(item -> toItemDto(item, stats.get(item.getId())))
                .toList();
    }

    @GetMapping("/items/{id}")
    public ItemDetailDto itemDetail(@PathVariable Long id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found"));
        RatingStat stat = ratingStats().get(item.getId());
        double avg = stat == null ? 0.0 : stat.avgScore();
        long count = stat == null ? 0L : stat.ratingCount();
        double price = item.getPrice() == null ? 0.0 : item.getPrice().doubleValue();
        String description = item.getDescription() == null ? "" : item.getDescription();
        return new ItemDetailDto(
                item.getId(),
                item.getName(),
                item.getCategory(),
                item.getImageUrl(),
                avg,
                count,
                item.getCreatedAt() == null ? null : item.getCreatedAt().toString(),
                price,
                description
        );
    }

    @GetMapping("/users/{userId}/favorites")
    public List<ItemDto> favorites(@PathVariable Long userId) {
        List<UserItemFlag> flags = userItemFlagRepository.findByUserIdAndFavoriteTrue(userId);
        flags.sort(Comparator.comparing(UserItemFlag::getUpdatedAt).reversed());
        return mapFlagsToItems(flags);
    }

    @GetMapping("/users/{userId}/cart")
    public List<ItemDto> cart(@PathVariable Long userId) {
        List<UserItemFlag> flags = userItemFlagRepository.findByUserIdAndInCartTrue(userId);
        flags.sort(Comparator.comparing(UserItemFlag::getUpdatedAt).reversed());
        return mapFlagsToItems(flags);
    }

    @GetMapping("/users/{userId}/flags")
    public List<UserItemFlagDto> flags(@PathVariable Long userId) {
        return userItemFlagRepository.findByUserId(userId).stream()
            .map(flag -> new UserItemFlagDto(flag.getItemId(), flag.isFavorite(), flag.isInCart()))
            .toList();
    }

    @DeleteMapping("/users/{userId}/favorites/{itemId}")
    public Map<String, Object> removeFavorite(@PathVariable Long userId, @PathVariable Long itemId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        UserItemFlag flag = userItemFlagRepository.findByUserIdAndItemId(userId, itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "收藏不存在"));
        flag.setFavorite(false);
        userItemFlagRepository.save(flag);
        rankingService.decrementFavorite(itemId);
        return Map.of("success", true, "message", "已取消收藏");
    }

    @DeleteMapping("/users/{userId}/cart/{itemId}")
    public Map<String, Object> removeCart(@PathVariable Long userId, @PathVariable Long itemId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        UserItemFlag flag = userItemFlagRepository.findByUserIdAndItemId(userId, itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "购物袋记录不存在"));
        flag.setInCart(false);
        userItemFlagRepository.save(flag);
        return Map.of("success", true, "message", "已从购物袋移除");
    }

    private List<ItemDto> mapFlagsToItems(List<UserItemFlag> flags) {
        if (flags.isEmpty()) {
            return List.of();
        }
        Set<Long> itemIds = flags.stream().map(UserItemFlag::getItemId).collect(Collectors.toSet());
        Map<Long, Item> itemMap = toItemMap(itemRepository.findAllById(itemIds));
        Map<Long, RatingStat> stats = ratingStats();
        return flags.stream()
                .map(flag -> {
                    Item item = itemMap.get(flag.getItemId());
                    return item == null ? null : toItemDto(item, stats.get(item.getId()));
                })
                .filter(item -> item != null)
                .toList();
    }

    private Map<Long, RatingStat> ratingStats() {
        Map<Long, RatingStat> stats = new HashMap<>();
        for (RatingRepository.ItemPopularityStatView row : ratingRepository.findItemPopularityStats()) {
            Long itemId = row.getItemId();
            if (itemId == null) {
                continue;
            }
            double avg = row.getAvgScore() == null ? 0.0 : row.getAvgScore();
            long count = row.getRatingCount() == null ? 0L : row.getRatingCount();
            stats.put(itemId, new RatingStat(avg, count));
        }
        return stats;
    }

    private Map<Long, Item> toItemMap(Iterable<Item> items) {
        return StreamSupport.stream(items.spliterator(), false)
                .collect(Collectors.toMap(Item::getId, Function.identity(), (a, b) -> a));
    }

    private ItemDto toItemDto(Item item, RatingStat stat) {
        double avg = stat == null ? 0.0 : stat.avgScore();
        long count = stat == null ? 0L : stat.ratingCount();
        double price = item.getPrice() == null ? 0.0 : item.getPrice().doubleValue();
        return new ItemDto(item.getId(), item.getName(), item.getCategory(), item.getImageUrl(), avg, count, price);
    }

    public record UserDto(Long id, String username) {
    }

    public record ItemDto(Long id, String name, String category, String imageUrl, double avgScore, long ratingCount, double price) {
    }

    public record ItemDetailDto(
            Long id,
            String name,
            String category,
            String imageUrl,
            double avgScore,
            long ratingCount,
            String createdAt,
            double price,
            String description
    ) {
    }

    public record UserItemFlagDto(Long itemId, boolean favorite, boolean inCart) {
    }

    private record RatingStat(double avgScore, long ratingCount) {
    }
}
