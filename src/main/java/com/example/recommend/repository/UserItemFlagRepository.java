package com.example.recommend.repository;

import com.example.recommend.model.UserItemFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserItemFlagRepository extends JpaRepository<UserItemFlag, Long> {
    Optional<UserItemFlag> findByUserIdAndItemId(Long userId, Long itemId);

    List<UserItemFlag> findByUserId(Long userId);

    List<UserItemFlag> findByUserIdAndFavoriteTrue(Long userId);

    List<UserItemFlag> findByUserIdAndInCartTrue(Long userId);
}
