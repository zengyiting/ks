package com.example.recommend.service;

import com.example.recommend.model.Category;
import com.example.recommend.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 商品类别服务层
 */
@Service
public class CategoryService {
    
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * 获取所有启用的类别
     */
    public List<Category> getAllEnabledCategories() {
        return categoryRepository.findByEnabledTrueOrderBySortOrderAsc();
    }

    /**
     * 获取所有类别名称（用于下拉框）
     */
    public List<String> getAllCategoryNames() {
        return categoryRepository.findAllEnabledNames();
    }

    /**
     * 获取顶级类别
     */
    public List<Category> getTopLevelCategories() {
        return categoryRepository.findByLevelAndEnabledTrueOrderBySortOrderAsc(1);
    }

    /**
     * 获取子类别
     */
    public List<Category> getSubCategories(Long parentId) {
        return categoryRepository.findByParentIdAndEnabledTrueOrderBySortOrderAsc(parentId);
    }

    /**
     * 获取类别详情
     */
    public Optional<Category> getCategoryById(Long id) {
        return categoryRepository.findById(id);
    }

    /**
     * 根据名称获取类别
     */
    public Optional<Category> getCategoryByName(String name) {
        return categoryRepository.findByName(name);
    }

    /**
     * 创建新类别
     */
    @Transactional
    public Category createCategory(Category category) {
        if (categoryRepository.existsByName(category.getName())) {
            throw new IllegalArgumentException("类别名称已存在: " + category.getName());
        }
        
        // 如果有父类别，设置正确的层级
        if (category.getParentId() != null) {
            Category parent = categoryRepository.findById(category.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("父类别不存在: " + category.getParentId()));
            category.setLevel(parent.getLevel() + 1);
        } else {
            category.setLevel(1);
        }
        
        return categoryRepository.save(category);
    }

    /**
     * 更新类别
     */
    @Transactional
    public Category updateCategory(Long id, Category category) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("类别不存在: " + id));
        
        // 检查名称是否与其他类别冲突
        Optional<Category> existingByName = categoryRepository.findByName(category.getName());
        if (existingByName.isPresent() && !existingByName.get().getId().equals(id)) {
            throw new IllegalArgumentException("类别名称已存在: " + category.getName());
        }
        
        existing.setName(category.getName());
        existing.setDescription(category.getDescription());
        existing.setSortOrder(category.getSortOrder());
        existing.setEnabled(category.getEnabled());
        existing.setIconUrl(category.getIconUrl());
        
        return categoryRepository.save(existing);
    }

    /**
     * 删除类别（软删除，设置enabled=false）
     */
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("类别不存在: " + id));
        
        // 检查是否有子类别
        List<Category> children = categoryRepository.findByParentIdOrderBySortOrderAsc(id);
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("该类别下存在子类别，无法删除");
        }
        
        category.setEnabled(false);
        categoryRepository.save(category);
    }

    /**
     * 获取树形结构的类别数据
     */
    public List<Map<String, Object>> getCategoryTree() {
        List<Category> allCategories = categoryRepository.findAllEnabledWithOrder();
        
        // 构建父ID到子类别列表的映射
        Map<Long, List<Category>> childrenMap = new HashMap<>();
        List<Category> topLevel = new ArrayList<>();
        
        for (Category category : allCategories) {
            if (category.getParentId() == null) {
                topLevel.add(category);
            } else {
                childrenMap.computeIfAbsent(category.getParentId(), k -> new ArrayList<>())
                          .add(category);
            }
        }
        
        // 递归构建树形结构
        return topLevel.stream()
                .map(cat -> buildTree(cat, childrenMap))
                .toList();
    }

    private Map<String, Object> buildTree(Category category, Map<Long, List<Category>> childrenMap) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", category.getId());
        node.put("name", category.getName());
        node.put("level", category.getLevel());
        node.put("sortOrder", category.getSortOrder());
        node.put("iconUrl", category.getIconUrl());
        
        List<Category> children = childrenMap.getOrDefault(category.getId(), Collections.emptyList());
        if (!children.isEmpty()) {
            node.put("children", children.stream()
                    .map(child -> buildTree(child, childrenMap))
                    .toList());
        }
        
        return node;
    }

    /**
     * 批量创建类别（用于初始化数据）
     */
    @Transactional
    public List<Category> batchCreateCategories(List<Category> categories) {
        return categoryRepository.saveAll(categories);
    }

    /**
     * 检查类别是否存在
     */
    public boolean categoryExists(Long id) {
        return categoryRepository.existsById(id);
    }

    /**
     * 获取所有类别（包含禁用的）
     */
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }
}