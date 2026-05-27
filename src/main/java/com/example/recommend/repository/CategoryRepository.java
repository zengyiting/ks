package com.example.recommend.repository;

import com.example.recommend.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商品类别数据访问接口
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    
    /**
     * 查询所有启用的类别
     */
    List<Category> findByEnabledTrueOrderBySortOrderAsc();
    
    /**
     * 查询顶级类别（level=1）
     */
    List<Category> findByLevelAndEnabledTrueOrderBySortOrderAsc(Integer level);
    
    /**
     * 查询指定父类别下的子类别
     */
    List<Category> findByParentIdAndEnabledTrueOrderBySortOrderAsc(Long parentId);
    
    /**
     * 根据名称查询类别
     */
    Optional<Category> findByName(String name);
    
    /**
     * 检查类别名称是否存在
     */
    boolean existsByName(String name);
    
    /**
     * 查询指定层级的所有类别
     */
    List<Category> findByLevelOrderBySortOrderAsc(Integer level);
    
    /**
     * 根据父ID查询所有子类别（包含禁用的）
     */
    List<Category> findByParentIdOrderBySortOrderAsc(Long parentId);
    
    /**
     * 获取所有类别名称（用于下拉框）
     */
    @Query("SELECT c.name FROM Category c WHERE c.enabled = true ORDER BY c.sortOrder ASC")
    List<String> findAllEnabledNames();
    
    /**
     * 获取树形结构的类别数据
     */
    @Query("SELECT c FROM Category c WHERE c.enabled = true ORDER BY c.parentId ASC NULLS FIRST, c.sortOrder ASC")
    List<Category> findAllEnabledWithOrder();
}