package com.example.recommend.web;

import com.example.recommend.service.BehaviorService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/behaviors")
public class BehaviorController {
    private final BehaviorService behaviorService;

    public BehaviorController(BehaviorService behaviorService) {
        this.behaviorService = behaviorService;
    }

    @GetMapping("/users")
    public List<UserDto> users(@RequestParam(name = "keyword", required = false) String keyword) {
        return behaviorService.findUsers(keyword).stream()
                .map(u -> new UserDto(u.getId(), u.getUsername()))
                .toList();
    }

    @GetMapping("/items")
    public List<ItemDto> items(@RequestParam(name = "keyword", required = false) String keyword) {
        return behaviorService.findItems(keyword).stream()
                .map(i -> new ItemDto(i.getId(), i.getName(), i.getCategory()))
                .toList();
    }

    @GetMapping("/users/{userId}/ratings")
    public List<BehaviorService.RatingRow> userRatings(@PathVariable Long userId) {
        return behaviorService.findUserRatings(userId);
    }

    @PostMapping("/ratings")
    public BehaviorService.RatingRow upsertRating(@RequestBody UpsertRatingRequest request) {
        return behaviorService.upsertRating(request.userId(), request.itemId(), request.score());
    }

    @PostMapping("/ratings/batch")
    public BehaviorService.BatchUpsertResult upsertRatingsBatch(@RequestBody BatchUpsertRatingRequest request) {
        List<BehaviorService.UpsertRatingCommand> commands = request == null || request.rows() == null
                ? List.of()
                : request.rows().stream()
                .map(row -> new BehaviorService.UpsertRatingCommand(row.userId(), row.itemId(), row.score()))
                .toList();
        return behaviorService.upsertRatingsBatch(commands);
    }

    @PostMapping("/events")
    public BehaviorService.BehaviorWriteResult recordEvent(@RequestBody BehaviorEventRequest request) {
        return behaviorService.recordBehavior(request.userId(), request.itemId(), request.action());
    }

    public record UpsertRatingRequest(Long userId, Long itemId, double score) {
    }

    public record BatchUpsertRatingRequest(List<UpsertRatingRequest> rows) {
    }

    public record BehaviorEventRequest(Long userId, Long itemId, String action) {
    }

    public record UserDto(Long id, String username) {
    }

    public record ItemDto(Long id, String name, String category) {
    }
}
