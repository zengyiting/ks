package com.example.recommend.repository;

import com.example.recommend.model.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 商品仓库
 */
public interface ItemRepository extends JpaRepository<Item, Long> {
    Page<Item> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    List<Item> findTop100ByOrderByIdAsc();

    List<Item> findTop100ByNameContainingIgnoreCaseOrderByIdAsc(String keyword);
}
