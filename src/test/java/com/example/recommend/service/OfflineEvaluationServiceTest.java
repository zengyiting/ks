package com.example.recommend.service;

import com.example.recommend.repository.ItemRepository;
import com.example.recommend.repository.RatingRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfflineEvaluationServiceTest {

    @Test
    void shouldEvaluateThreeAlgorithms() {
        RatingRepository ratingRepository = mock(RatingRepository.class);
        ItemRepository itemRepository = mock(ItemRepository.class);
        when(ratingRepository.findAllUserItemScores()).thenReturn(List.of(
                row(1L, 101L, 5.0), row(1L, 102L, 4.0), row(1L, 201L, 3.0),
                row(2L, 101L, 4.0), row(2L, 103L, 5.0), row(2L, 202L, 4.0),
                row(3L, 102L, 5.0), row(3L, 201L, 4.0), row(3L, 203L, 4.0),
                row(4L, 101L, 2.0), row(4L, 202L, 5.0), row(4L, 203L, 3.0)
        ));
        when(itemRepository.findAllById(anyIterable())).thenReturn(List.of());
        OfflineEvaluationService service = new OfflineEvaluationService(ratingRepository, itemRepository);

        OfflineEvaluationService.EvaluationReport report = service.evaluate(10, 0.2, 4.0);

        assertThat(report.evaluableUsers()).isGreaterThan(0);
        assertThat(report.metrics().keySet()).containsExactly("user", "item", "hybrid");
        for (Map.Entry<String, OfflineEvaluationService.AlgorithmMetrics> e : report.metrics().entrySet()) {
            assertThat(e.getValue().precisionAtK()).isBetween(0.0, 1.0);
            assertThat(e.getValue().recallAtK()).isBetween(0.0, 1.0);
            assertThat(e.getValue().ndcgAtK()).isBetween(0.0, 1.0);
            assertThat(e.getValue().coverage()).isBetween(0.0, 1.0);
        }
    }

    @Test
    void shouldNormalizeInvalidParameters() {
        RatingRepository ratingRepository = mock(RatingRepository.class);
        ItemRepository itemRepository = mock(ItemRepository.class);
        when(ratingRepository.findAllUserItemScores()).thenReturn(List.of(
                row(1L, 101L, 5.0), row(1L, 102L, 4.0),
                row(2L, 101L, 4.0), row(2L, 103L, 5.0)
        ));
        when(itemRepository.findAllById(anyIterable())).thenReturn(List.of());
        OfflineEvaluationService service = new OfflineEvaluationService(ratingRepository, itemRepository);

        OfflineEvaluationService.EvaluationReport report = service.evaluate(-1, 2.0, 100.0);

        assertThat(report.topK()).isEqualTo(1);
        assertThat(report.testRatio()).isEqualTo(0.2);
        assertThat(report.relevanceThreshold()).isEqualTo(5.0);
    }

    private static RatingRepository.UserItemScoreView row(Long userId, Long itemId, Double score) {
        return new RatingRepository.UserItemScoreView() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public Long getItemId() {
                return itemId;
            }

            @Override
            public Double getScore() {
                return score;
            }
        };
    }
}

