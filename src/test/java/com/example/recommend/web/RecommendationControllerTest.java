package com.example.recommend.web;

import com.example.recommend.model.Item;
import com.example.recommend.model.Rating;
import com.example.recommend.repository.ItemRepository;
import com.example.recommend.repository.RatingRepository;
import com.example.recommend.repository.UserItemFlagRepository;
import com.example.recommend.repository.UserRepository;
import com.example.recommend.service.RecommendationCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecommendationCacheService recommendationCacheService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired(required = false)
    @Qualifier("recommendationCacheKeyGenerator")
    private KeyGenerator recommendationCacheKeyGenerator;

    @MockBean
    private RatingRepository ratingRepository;

    @MockBean
    private ItemRepository itemRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserItemFlagRepository userItemFlagRepository;

    private Map<Long, Item> itemIndex;

    @BeforeEach
    void setUp() {
        assertThat(recommendationCacheKeyGenerator).as("custom recommendation cache key generator should be loaded").isNotNull();
        recommendationCacheService.invalidateAll();
        Cache recommendationCache = cacheManager.getCache("recommendationResults");
        if (recommendationCache != null) {
            recommendationCache.clear();
        }

        List<RatingRepository.UserItemScoreView> sampleRows = List.of(
                row(1L, 101L, 5.0), row(1L, 102L, 4.0), row(1L, 201L, 3.0),
                row(2L, 101L, 4.0), row(2L, 103L, 5.0), row(2L, 202L, 4.0),
                row(3L, 102L, 5.0), row(3L, 201L, 4.0), row(3L, 203L, 4.0),
                row(4L, 101L, 2.0), row(4L, 202L, 5.0), row(4L, 203L, 3.0)
        );

        itemIndex = new HashMap<>();
        itemIndex.put(101L, item(101L, "algo-book", "books"));
        itemIndex.put(102L, item(102L, "java-book", "books"));
        itemIndex.put(103L, item(103L, "ml-book", "books"));
        itemIndex.put(201L, item(201L, "mechanical-keyboard", "electronics"));
        itemIndex.put(202L, item(202L, "noise-canceling-headphones", "electronics"));
        itemIndex.put(203L, item(203L, "ergonomic-chair", "furniture"));

        when(ratingRepository.findAllUserItemScores()).thenReturn(sampleRows);
        when(ratingRepository.findAllUserItemRatedAt()).thenReturn(toRatedAtRows(sampleRows, java.time.Instant.now()));
        when(ratingRepository.findItemPopularityStats()).thenReturn(popularity(sampleRows));
        when(ratingRepository.findUserRatingsWithItem(1L)).thenReturn(Collections.singletonList(
                userRatingRow(1L, 101L, "sample-book", "books", 5.0, java.time.Instant.now())
        ));
        when(itemRepository.findAllById(anyIterable())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            return StreamSupport.stream(ids.spliterator(), false)
                    .map(itemIndex::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        });
        when(itemRepository.findTop100ByOrderByIdAsc()).thenReturn(new ArrayList<>(itemIndex.values()));
        when(itemRepository.findTop100ByNameContainingIgnoreCaseOrderByIdAsc(anyString())).thenReturn(new ArrayList<>(itemIndex.values()));
        when(itemRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(new ArrayList<>(itemIndex.values())));
        when(itemRepository.findByNameContainingIgnoreCase(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(new ArrayList<>(itemIndex.values())));
        when(userRepository.findTop50ByOrderByIdAsc()).thenReturn(List.of(user(1L, "alice"), user(2L, "bob")));
        when(userRepository.findTop50ByUsernameContainingIgnoreCaseOrderByIdAsc(anyString())).thenReturn(List.of(user(1L, "alice")));
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user(1L, "alice"), user(2L, "bob"))));
        when(userRepository.findByUsernameContainingIgnoreCase(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user(1L, "alice"))));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "alice")));
        when(userRepository.findAllById(anyIterable())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            Map<Long, com.example.recommend.model.User> users = Map.of(
                1L, user(1L, "alice"),
                2L, user(2L, "bob"),
                3L, user(3L, "carol"),
                4L, user(4L, "dave")
            );
            return StreamSupport.stream(ids.spliterator(), false)
                .map(users::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        });
        when(userRepository.findAll()).thenReturn(List.of(user(1L, "alice"), user(2L, "bob")));
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsById(1L)).thenReturn(true);
        when(userRepository.save(any(com.example.recommend.model.User.class))).thenAnswer(invocation -> {
            com.example.recommend.model.User u = invocation.getArgument(0);
            if (u.getId() == null) {
                ReflectionTestUtils.setField(u, "id", 999L);
            }
            return u;
        });
        when(itemRepository.findById(101L)).thenReturn(Optional.of(item(101L, "algo-book", "books")));
        when(itemRepository.findById(202L)).thenReturn(Optional.of(item(202L, "noise-canceling-headphones", "electronics")));
        when(itemRepository.findAll()).thenReturn(new ArrayList<>(itemIndex.values()));
        when(itemRepository.existsById(101L)).thenReturn(true);
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item i = invocation.getArgument(0);
            if (i.getId() == null) {
                ReflectionTestUtils.setField(i, "id", 888L);
            }
            return i;
        });
        when(ratingRepository.findByUserAndItem(any(com.example.recommend.model.User.class), any(Item.class))).thenReturn(Optional.empty());
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> {
            Rating r = invocation.getArgument(0);
            if (r.getId() == null) {
                ReflectionTestUtils.setField(r, "id", 777L);
            }
            if (r.getRatedAt() == null) {
                r.setRatedAt(java.time.Instant.now());
            }
            return r;
        });
        when(ratingRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
            Iterable<Rating> in = invocation.getArgument(0);
            List<Rating> out = new ArrayList<>();
            long seed = 5000L;
            for (Rating r : in) {
                if (r.getId() == null) {
                    ReflectionTestUtils.setField(r, "id", seed++);
                }
                if (r.getRatedAt() == null) {
                    r.setRatedAt(java.time.Instant.now());
                }
                out.add(r);
            }
            return out;
        });
    }

    @Test
    void shouldReturnBadRequestWhenAlgoInvalid() throws Exception {
        mockMvc.perform(get("/api/recommendations/1")
                        .param("n", "5")
                        .param("algo", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnSortedScoresForItemBased() throws Exception {
        String json = mockMvc.perform(get("/api/recommendations/1")
                        .param("n", "3")
                        .param("algo", "item"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(list).hasSize(3);

        List<Double> scores = list.stream()
                .map(m -> ((Number) m.get("score")).doubleValue())
                .toList();
        assertThat(scores.get(0)).isGreaterThanOrEqualTo(scores.get(1));
        assertThat(scores.get(1)).isGreaterThanOrEqualTo(scores.get(2));
    }

    @Test
    void shouldReturnReasonForHybridRecommendation() throws Exception {
        String json = mockMvc.perform(get("/api/recommendations/1")
                        .param("n", "3")
                        .param("algo", "hybrid"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(list).isNotEmpty();
        assertThat(list.get(0)).containsKey("reason");
        assertThat(String.valueOf(list.get(0).get("reason"))).isNotBlank();
    }

    @Test
    void shouldClampTopNToOneWhenNIsZero() throws Exception {
        String json = mockMvc.perform(get("/api/recommendations/1")
                        .param("n", "0")
                        .param("algo", "item"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(list).hasSize(1);
    }

    @Test
    void shouldClampTopNToHundredWhenNTooLarge() throws Exception {
        List<RatingRepository.UserItemScoreView> largeRows = new ArrayList<>();
        largeRows.add(row(1L, 1L, 5.0));
        itemIndex = new HashMap<>();
        itemIndex.put(1L, item(1L, "target-item", "seed"));
        for (long u = 2L; u <= 220L; u++) {
            long candidateItemId = 1000L + u;
            largeRows.add(row(u, 1L, 5.0));
            largeRows.add(row(u, candidateItemId, 4.0));
            itemIndex.put(candidateItemId, item(candidateItemId, "item-" + candidateItemId, "bulk"));
        }

        when(ratingRepository.findAllUserItemScores()).thenReturn(largeRows);
        when(ratingRepository.findAllUserItemRatedAt()).thenReturn(toRatedAtRows(largeRows, java.time.Instant.now()));
        when(ratingRepository.findItemPopularityStats()).thenReturn(popularity(largeRows));

        String json = mockMvc.perform(get("/api/recommendations/1")
                        .param("n", "999")
                        .param("algo", "item"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(list).hasSize(100);
    }

    @Test
    void shouldBackfillFromCatalogWhenCfAndPopularityInsufficient() throws Exception {
        for (long id = 300L; id < 312L; id++) {
            itemIndex.put(id, item(id, "cold-item-" + id, (id % 2 == 0) ? "electronics" : "books"));
        }
        refreshItemListStubs();
        assertThat(itemRepository.findAll()).hasSize(18);

        String json = mockMvc.perform(get("/api/recommendations/1")
                        .param("n", "13")
                        .param("algo", "user"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(list).hasSize(13);
        assertThat(list)
                .extracting(m -> ((Number) m.get("itemId")).longValue())
                .contains(300L, 301L, 302L);
    }

        @Test
        void shouldUseDifferentCacheEntriesForDifferentTopN() throws Exception {
        String top1Json = mockMvc.perform(get("/api/recommendations/1")
                .param("n", "1")
                .param("algo", "user"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        List<Map<String, Object>> top1 = objectMapper.readValue(top1Json, new TypeReference<>() {});
        assertThat(top1).hasSize(1);

        String top3Json = mockMvc.perform(get("/api/recommendations/1")
                .param("n", "3")
                .param("algo", "user"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        List<Map<String, Object>> top3 = objectMapper.readValue(top3Json, new TypeReference<>() {});
        assertThat(top3.size()).isGreaterThan(top1.size());
        }

        @Test
        void shouldEvictUserRecommendationCacheAfterBehaviorUpdate() throws Exception {
        List<RatingRepository.UserItemScoreView> beforeRows = List.of(
            row(1L, 101L, 5.0), row(1L, 102L, 4.0), row(1L, 201L, 3.0),
            row(2L, 101L, 4.0), row(2L, 103L, 5.0), row(2L, 202L, 4.0),
            row(3L, 102L, 5.0), row(3L, 201L, 4.0), row(3L, 203L, 4.0),
            row(4L, 101L, 2.0), row(4L, 202L, 5.0), row(4L, 203L, 3.0)
        );
        List<RatingRepository.UserItemScoreView> afterRows = new ArrayList<>(beforeRows);
        afterRows.add(row(1L, 202L, 5.0));
        java.time.Instant now = java.time.Instant.now();
        when(ratingRepository.findAllUserItemScores()).thenReturn(beforeRows, afterRows);
        when(ratingRepository.findAllUserItemRatedAt()).thenReturn(
            toRatedAtRows(beforeRows, now),
            toRatedAtRows(afterRows, now)
        );
        when(ratingRepository.findItemPopularityStats()).thenReturn(popularity(beforeRows), popularity(afterRows));

        String beforeJson = mockMvc.perform(get("/api/recommendations/1")
                .param("n", "3")
                .param("algo", "user"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        objectMapper.readValue(beforeJson, new TypeReference<List<Map<String, Object>>>() {});

        mockMvc.perform(post("/api/behaviors/ratings")
                .contentType(APPLICATION_JSON)
                .content("{\"userId\":1,\"itemId\":202,\"score\":5.0}"))
            .andExpect(status().isOk());

        String afterJson = mockMvc.perform(get("/api/recommendations/1")
                .param("n", "3")
                .param("algo", "user"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        objectMapper.readValue(afterJson, new TypeReference<List<Map<String, Object>>>() {});
        verify(ratingRepository, atLeast(2)).findAllUserItemScores();
        }

        @Test
        void shouldInvalidateRecommendationCacheAfterAdminItemUpdate() throws Exception {
        List<RatingRepository.UserItemScoreView> beforeRows = List.of(
            row(1L, 101L, 5.0), row(1L, 102L, 4.0), row(1L, 201L, 3.0),
            row(2L, 101L, 4.0), row(2L, 103L, 5.0), row(2L, 202L, 4.0),
            row(3L, 102L, 5.0), row(3L, 201L, 4.0), row(3L, 203L, 4.0),
            row(4L, 101L, 2.0), row(4L, 202L, 5.0), row(4L, 203L, 3.0)
        );
        List<RatingRepository.UserItemScoreView> afterRows = new ArrayList<>(beforeRows);
        afterRows.add(row(1L, 202L, 5.0));
        java.time.Instant now = java.time.Instant.now();
        when(ratingRepository.findAllUserItemScores()).thenReturn(beforeRows, afterRows);
        when(ratingRepository.findAllUserItemRatedAt()).thenReturn(
            toRatedAtRows(beforeRows, now),
            toRatedAtRows(afterRows, now)
        );
        when(ratingRepository.findItemPopularityStats()).thenReturn(popularity(beforeRows), popularity(afterRows));

        String beforeJson = mockMvc.perform(get("/api/recommendations/1")
                .param("n", "3")
                .param("algo", "user"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        objectMapper.readValue(beforeJson, new TypeReference<List<Map<String, Object>>>() {});

        mockMvc.perform(put("/api/admin/items/101")
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"algo-book-v2\",\"category\":\"books\"}"))
            .andExpect(status().isOk());

        String afterJson = mockMvc.perform(get("/api/recommendations/1")
                .param("n", "3")
                .param("algo", "user"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        objectMapper.readValue(afterJson, new TypeReference<List<Map<String, Object>>>() {});
        verify(ratingRepository, atLeast(2)).findAllUserItemScores();
        }

        @Test
        void shouldInvalidateRecommendationCacheAfterBatchRatingsImport() throws Exception {
        List<RatingRepository.UserItemScoreView> beforeRows = List.of(
            row(1L, 101L, 5.0), row(1L, 102L, 4.0), row(1L, 201L, 3.0),
            row(2L, 101L, 4.0), row(2L, 103L, 5.0), row(2L, 202L, 4.0),
            row(3L, 102L, 5.0), row(3L, 201L, 4.0), row(3L, 203L, 4.0),
            row(4L, 101L, 2.0), row(4L, 202L, 5.0), row(4L, 203L, 3.0)
        );
        List<RatingRepository.UserItemScoreView> afterRows = new ArrayList<>(beforeRows);
        afterRows.add(row(1L, 202L, 5.0));
        java.time.Instant now = java.time.Instant.now();
        when(ratingRepository.findAllUserItemScores()).thenReturn(beforeRows, afterRows);
        when(ratingRepository.findAllUserItemRatedAt()).thenReturn(
            toRatedAtRows(beforeRows, now),
            toRatedAtRows(afterRows, now)
        );
        when(ratingRepository.findItemPopularityStats()).thenReturn(popularity(beforeRows), popularity(afterRows));

        String beforeJson = mockMvc.perform(get("/api/recommendations/1")
                .param("n", "3")
                .param("algo", "user"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        objectMapper.readValue(beforeJson, new TypeReference<List<Map<String, Object>>>() {});

        mockMvc.perform(post("/api/behaviors/ratings/batch")
                .contentType(APPLICATION_JSON)
                .content("{\"rows\":[{\"userId\":1,\"itemId\":202,\"score\":5.0},{\"userId\":1,\"itemId\":103,\"score\":4.7}]}"))
            .andExpect(status().isOk());

        String afterJson = mockMvc.perform(get("/api/recommendations/1")
                .param("n", "3")
                .param("algo", "user"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        objectMapper.readValue(afterJson, new TypeReference<List<Map<String, Object>>>() {});
        verify(ratingRepository, times(2)).findAllUserItemScores();
        }

    @Test
    void shouldUseFallbackForColdStartUser() throws Exception {
        String json = mockMvc.perform(get("/api/recommendations/999")
                        .param("n", "3")
                        .param("algo", "hybrid"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {});
        assertThat(list).hasSize(3);
        assertThat(list).extracting(m -> ((Number) m.get("itemId")).longValue())
                .contains(101L);
    }

    @Test
    void shouldReturnOfflineEvaluationReport() throws Exception {
        String json = mockMvc.perform(get("/api/evaluations/offline")
                        .param("k", "10")
                        .param("testRatio", "0.2")
                        .param("relevance", "4.0"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> report = objectMapper.readValue(json, new TypeReference<>() {});
        Map<String, Object> metrics = (Map<String, Object>) report.get("metrics");
        assertThat(metrics).containsKeys("user", "item", "hybrid");
    }

    @Test
    void shouldExportOfflineEvaluationCsv() throws Exception {
        String csv = mockMvc.perform(get("/api/evaluations/offline/csv")
                        .param("k", "10")
                        .param("testRatio", "0.2")
                        .param("relevance", "4.0"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(csv).contains("algorithm,topK,testRatio,relevance");
        assertThat(csv).contains("hybrid");
    }

    @Test
    void shouldCollectUserBehaviorByApis() throws Exception {
        String users = mockMvc.perform(get("/api/behaviors/users"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(users).contains("alice");

        String items = mockMvc.perform(get("/api/behaviors/items"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(items).contains("\"id\":101");

        String ratings = mockMvc.perform(get("/api/behaviors/users/1/ratings"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(ratings).contains("itemId");

        String event = mockMvc.perform(post("/api/behaviors/events")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":1,\"itemId\":101,\"action\":\"favorite\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(event).contains("\"action\":\"favorite\"");
    }

    @Test
    void shouldCrudUsersByAdminApis() throws Exception {
        String users = mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(users).contains("alice");

        String created = mockMvc.perform(post("/api/admin/users")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"charlie\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(created).contains("charlie");

        String updated = mockMvc.perform(put("/api/admin/users/1")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice_new\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(updated).contains("alice_new");

        mockMvc.perform(delete("/api/admin/users/1"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldCrudItemsByAdminApis() throws Exception {
        String items = mockMvc.perform(get("/api/admin/items"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(items).contains("\"id\":101");

        String created = mockMvc.perform(post("/api/admin/items")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"new-item\",\"category\":\"toy\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(created).contains("new-item");

        String updated = mockMvc.perform(put("/api/admin/items/101")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"updated-item\",\"category\":\"books\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(updated).contains("updated-item");

        mockMvc.perform(delete("/api/admin/items/101"))
                .andExpect(status().isOk());
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

    private static List<RatingRepository.ItemPopularityStatView> popularity(List<RatingRepository.UserItemScoreView> rows) {
        Map<Long, double[]> stat = new HashMap<>();
        for (RatingRepository.UserItemScoreView row : rows) {
            Long itemId = row.getItemId();
            Double score = row.getScore();
            double[] acc = stat.computeIfAbsent(itemId, k -> new double[2]);
            acc[0] += score;
            acc[1] += 1;
        }
        List<RatingRepository.ItemPopularityStatView> result = new ArrayList<>();
        for (Map.Entry<Long, double[]> e : stat.entrySet()) {
            double sum = e.getValue()[0];
            long cnt = (long) e.getValue()[1];
            double avg = sum / cnt;
            result.add(popularityRow(e.getKey(), avg, cnt));
        }
        return result;
    }

    private static List<RatingRepository.UserItemRatedAtView> toRatedAtRows(
            List<RatingRepository.UserItemScoreView> rows,
            java.time.Instant ratedAt
    ) {
        return rows.stream().map(row -> ratedAtRow(row.getUserId(), row.getItemId(), ratedAt)).toList();
    }

    private static RatingRepository.UserItemRatedAtView ratedAtRow(Long userId, Long itemId, java.time.Instant ratedAt) {
        return new RatingRepository.UserItemRatedAtView() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public Long getItemId() {
                return itemId;
            }

            @Override
            public java.time.Instant getRatedAt() {
                return ratedAt;
            }
        };
    }

    private static RatingRepository.UserRatingWithItemView userRatingRow(
            Long ratingId,
            Long itemId,
            String itemName,
            String category,
            Double score,
            java.time.Instant ratedAt
    ) {
        return new RatingRepository.UserRatingWithItemView() {
            @Override
            public Long getRatingId() {
                return ratingId;
            }

            @Override
            public Long getItemId() {
                return itemId;
            }

            @Override
            public String getItemName() {
                return itemName;
            }

            @Override
            public String getCategory() {
                return category;
            }

            @Override
            public Double getScore() {
                return score;
            }

            @Override
            public java.time.Instant getRatedAt() {
                return ratedAt;
            }
        };
    }

    private static RatingRepository.ItemPopularityStatView popularityRow(Long itemId, Double avgScore, Long ratingCount) {
        return new RatingRepository.ItemPopularityStatView() {
            @Override
            public Long getItemId() {
                return itemId;
            }

            @Override
            public Double getAvgScore() {
                return avgScore;
            }

            @Override
            public Long getRatingCount() {
                return ratingCount;
            }
        };
    }

    private static Item item(Long id, String name, String category) {
        Item item = new Item(name, category);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static com.example.recommend.model.User user(Long id, String username) {
        com.example.recommend.model.User user = new com.example.recommend.model.User(username);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private void refreshItemListStubs() {
        List<Item> snapshot = new ArrayList<>(itemIndex.values());
        when(itemRepository.findAll()).thenReturn(snapshot);
        when(itemRepository.findTop100ByOrderByIdAsc()).thenReturn(snapshot);
        when(itemRepository.findTop100ByNameContainingIgnoreCaseOrderByIdAsc(anyString())).thenReturn(snapshot);
        when(itemRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(snapshot));
        when(itemRepository.findByNameContainingIgnoreCase(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(snapshot));
    }
}


