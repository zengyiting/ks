package com.example.recommend.repository;

import com.example.recommend.model.Rating;
import com.example.recommend.model.User;
import com.example.recommend.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 评分仓库
 */
public interface RatingRepository extends JpaRepository<Rating, Long> {
    interface UserItemScoreView {
        Long getUserId();

        Long getItemId();

        Double getScore();
    }

    interface UserItemRatedAtView {
        Long getUserId();

        Long getItemId();

        Instant getRatedAt();
    }

    interface UserItemScoreRatedAtView {
        Long getUserId();

        Long getItemId();

        Double getScore();

        Instant getRatedAt();
    }

    interface UserRatingWithItemView {
        Long getRatingId();

        Long getItemId();

        String getItemName();

        String getCategory();

        Double getScore();

        Instant getRatedAt();
    }

    interface ItemPopularityStatView {
        Long getItemId();

        Double getAvgScore();

        Long getRatingCount();
    }

    Optional<Rating> findByUserAndItem(User user, Item item);

    List<Rating> findByUserId(Long userId);

    @Query("select r from Rating r where r.item.id = :itemId")
    List<Rating> findByItemId(@Param("itemId") Long itemId);

    @Query("select r.user.id as userId, r.item.id as itemId, r.score as score from Rating r")
    List<UserItemScoreView> findAllUserItemScores();

    @Query("select r.user.id as userId, r.item.id as itemId, r.score as score, r.ratedAt as ratedAt from Rating r")
    List<UserItemScoreRatedAtView> findAllUserItemScoresWithRatedAt();

    @Query("select r.user.id as userId, r.item.id as itemId, r.ratedAt as ratedAt from Rating r")
    List<UserItemRatedAtView> findAllUserItemRatedAt();

    @Query("""
            select r.id as ratingId,
                   r.item.id as itemId,
                   r.item.name as itemName,
                   r.item.category as category,
                   r.score as score,
                   r.ratedAt as ratedAt
            from Rating r
            where r.user.id = :userId
            order by r.ratedAt desc
            """)
    List<UserRatingWithItemView> findUserRatingsWithItem(@Param("userId") Long userId);

    @Query("select r.item.id as itemId, avg(r.score) as avgScore, count(r.id) as ratingCount from Rating r group by r.item.id")
    List<ItemPopularityStatView> findItemPopularityStats();
}
