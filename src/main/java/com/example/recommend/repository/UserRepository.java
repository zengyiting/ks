package com.example.recommend.repository;

import com.example.recommend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓库
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    Page<User> findByUsernameContainingIgnoreCase(String keyword, Pageable pageable);

    List<User> findTop50ByOrderByIdAsc();

    List<User> findTop50ByUsernameContainingIgnoreCaseOrderByIdAsc(String keyword);

    List<User> findTop50ByDisabledFalseOrderByIdAsc();

    List<User> findTop50ByUsernameContainingIgnoreCaseAndDisabledFalseOrderByIdAsc(String keyword);
}
