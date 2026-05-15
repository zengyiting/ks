package com.example.recommend.repository;

import com.example.recommend.model.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 商品仓库
 */
public interface ItemRepository extends JpaRepository<Item, Long> {
    Page<Item> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    List<Item> findTop100ByOrderByIdAsc();

    List<Item> findTop100ByNameContainingIgnoreCaseOrderByIdAsc(String keyword);

    /**
     * 根据分类查询商品
     */
    List<Item> findByCategory(String category);

    /**
     * 根据分类分页查询商品
     */
    Page<Item> findByCategory(String category, Pageable pageable);

    /**
     * 查询所有不重复的分类
     */
    @Query("SELECT DISTINCT i.category FROM Item i WHERE i.category IS NOT NULL AND i.category <> '' ORDER BY i.category")
    List<String> findAllCategories();

    /**
     * 根据ID查询单个商品
     */
    Optional<Item> findById(Long id);

    /**
     * 根据名称查询商品
     */
    Optional<Item> findByName(String name);

    /**
     * 统计分类下的商品数量
     */
    long countByCategory(String category);
}
