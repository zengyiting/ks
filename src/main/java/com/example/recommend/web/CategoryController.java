package com.example.recommend.web;

import com.example.recommend.model.Category;
import com.example.recommend.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 商品类别管理控制器
 */
@RestController
@RequestMapping("/api/admin/category")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * 获取所有启用的类别列表
     */
    @GetMapping
    public List<CategoryDto> listCategories() {
        return categoryService.getAllEnabledCategories().stream()
                .map(this::toCategoryDto)
                .toList();
    }

    /**
     * 获取所有类别名称（用于下拉框）
     */
    @GetMapping("/names")
    public List<String> listCategoryNames() {
        return categoryService.getAllCategoryNames();
    }

    /**
     * 获取类别树形结构
     */
    @GetMapping("/tree")
    public List<Map<String, Object>> getCategoryTree() {
        return categoryService.getCategoryTree();
    }

    /**
     * 获取顶级类别
     */
    @GetMapping("/top")
    public List<CategoryDto> listTopCategories() {
        return categoryService.getTopLevelCategories().stream()
                .map(this::toCategoryDto)
                .toList();
    }

    /**
     * 获取子类别
     */
    @GetMapping("/{parentId}/children")
    public List<CategoryDto> listSubCategories(@PathVariable Long parentId) {
        return categoryService.getSubCategories(parentId).stream()
                .map(this::toCategoryDto)
                .toList();
    }

    /**
     * 获取单个类别详情
     */
    @GetMapping("/{id}")
    public CategoryDto getCategory(@PathVariable Long id) {
        return categoryService.getCategoryById(id)
                .map(this::toCategoryDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "类别不存在"));
    }

    /**
     * 创建新类别
     */
    @PostMapping
    public ResponseEntity<CategoryDto> createCategory(@RequestBody CategoryUpsertRequest request) {
        Category category = new Category();
        category.setName(request.name());
        category.setParentId(request.parentId());
        category.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        category.setIconUrl(request.iconUrl());
        category.setEnabled(request.enabled() != null ? request.enabled() : true);
        category.setDescription(request.description());

        Category created = categoryService.createCategory(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(toCategoryDto(created));
    }

    /**
     * 更新类别
     */
    @PutMapping("/{id}")
    public CategoryDto updateCategory(@PathVariable Long id, @RequestBody CategoryUpsertRequest request) {
        Category category = new Category();
        category.setName(request.name());
        category.setSortOrder(request.sortOrder());
        category.setIconUrl(request.iconUrl());
        category.setEnabled(request.enabled());
        category.setDescription(request.description());

        Category updated = categoryService.updateCategory(id, category);
        return toCategoryDto(updated);
    }

    /**
     * 删除类别（软删除）
     */
    @DeleteMapping("/{id}")
    public void deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
    }

    /**
     * 批量创建类别
     */
    @PostMapping("/batch")
    public ResponseEntity<List<CategoryDto>> batchCreateCategories(@RequestBody List<CategoryUpsertRequest> requests) {
        List<Category> categories = requests.stream()
                .map(req -> {
                    Category cat = new Category();
                    cat.setName(req.name());
                    cat.setParentId(req.parentId());
                    cat.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
                    cat.setIconUrl(req.iconUrl());
                    cat.setEnabled(req.enabled() != null ? req.enabled() : true);
                    cat.setDescription(req.description());
                    return cat;
                })
                .toList();

        List<Category> created = categoryService.batchCreateCategories(categories);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(created.stream().map(this::toCategoryDto).toList());
    }

    /**
     * 将Category实体转换为DTO
     */
    private CategoryDto toCategoryDto(Category category) {
        return new CategoryDto(
                category.getId(),
                category.getName(),
                category.getParentId(),
                category.getLevel(),
                category.getSortOrder(),
                category.getIconUrl(),
                category.getEnabled(),
                category.getDescription(),
                category.getCreatedAt() != null ? category.getCreatedAt().toString() : null
        );
    }

    // 请求DTO
    public record CategoryUpsertRequest(
            String name,
            Long parentId,
            Integer sortOrder,
            String iconUrl,
            Boolean enabled,
            String description
    ) {}

    // 响应DTO
    public record CategoryDto(
            Long id,
            String name,
            Long parentId,
            Integer level,
            Integer sortOrder,
            String iconUrl,
            Boolean enabled,
            String description,
            String createdAt
    ) {}
}
