package com.example.recommend.web;

import com.example.recommend.service.RankingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ranking")
public class RankingController {
    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    /**
     * 获取收藏排行榜 Top N
     */
    @GetMapping("/favorites")
    public List<RankingService.RankingItem> getFavoriteRanking(
            @RequestParam(name = "n", defaultValue = "50") int n
    ) {
        int topN = Math.max(1, Math.min(n, 200));
        return rankingService.getTopRanking(topN);
    }
}
