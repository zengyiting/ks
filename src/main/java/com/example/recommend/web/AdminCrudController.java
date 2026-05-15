package com.example.recommend.web;

import com.example.recommend.model.Item;
import com.example.recommend.model.User;
import com.example.recommend.service.AdminCrudService;
import com.example.recommend.service.FileStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 管理员CRUD操作控制器
 * 提供用户和商品的增删改查REST API接口
 */
@RestController
@RequestMapping("/api/admin")
public class AdminCrudController {
    private final AdminCrudService adminCrudService;
    private final FileStorageService fileStorageService;

    public AdminCrudController(AdminCrudService adminCrudService, FileStorageService fileStorageService) {
        this.adminCrudService = adminCrudService;
        this.fileStorageService = fileStorageService;
    }

    /**
     * 查询用户列表，支持按关键字过滤
     *
     * @param keyword 用户名关键字，可选参数
     * @return 用户数据传输对象列表
     */
    @GetMapping("/users")
    public List<UserDto> listUsers(@RequestParam(name = "keyword", required = false) String keyword) {
        return adminCrudService.listUsers(keyword).stream()
                .map(this::toUserDto)
                .toList();
    }

    /**
     * 创建新用户
     *
     * @param request 用户创建请求，包含用户名
     * @return 创建后的用户数据传输对象
     */
    @PostMapping("/users")
    public UserDto createUser(@RequestBody UserUpsertRequest request) {
        return toUserDto(adminCrudService.createUser(request.username()));
    }

    /**
     * 更新指定用户的信息
     *
     * @param id 用户ID
     * @param request 用户更新请求，包含新用户名
     * @return 更新后的用户数据传输对象
     */
    @PutMapping("/users/{id}")
    public UserDto updateUser(@PathVariable Long id, @RequestBody UserUpsertRequest request) {
        return toUserDto(adminCrudService.updateUser(id, request.username()));
    }

    @PutMapping("/users/{id}/disabled")
    public UserDto updateUserDisabled(@PathVariable Long id, @RequestBody UserDisabledRequest request) {
        return toUserDto(adminCrudService.updateUserDisabled(id, request.disabled()));
    }

    /**
     * 删除指定用户
     *
     * @param id 要删除的用户ID
     */
    @DeleteMapping("/users/{id}")
    public void deleteUser(@PathVariable Long id) {
        adminCrudService.deleteUser(id);
    }

    /**
     * 查询商品列表，支持按关键字过滤
     *
     * @param keyword 商品名称或分类关键字，可选参数
     * @return 商品数据传输对象列表
     */
    @GetMapping("/items")
    public List<ItemDto> listItems(@RequestParam(name = "keyword", required = false) String keyword) {
        return adminCrudService.listItems(keyword).stream()
                .map(this::toItemDto)
                .toList();
    }

    /**
     * 创建新商品
     *
     * @param request 商品创建请求，包含商品名称和分类
     * @return 创建后的商品数据传输对象
     */
    @PostMapping("/items")
    public ItemDto createItem(@RequestBody ItemUpsertRequest request) {
        return toItemDto(adminCrudService.createItem(request.name(), request.category()));
    }

    /**
     * 更新指定商品的信息
     *
     * @param id 商品ID
     * @param request 商品更新请求，包含新名称和分类
     * @return 更新后的商品数据传输对象
     */
    @PutMapping("/items/{id}")
    public ItemDto updateItem(@PathVariable Long id, @RequestBody ItemUpsertRequest request) {
        return toItemDto(adminCrudService.updateItem(id, request.name(), request.category()));
    }

    @PostMapping(value = "/items/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemDto uploadItemImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        FileStorageService.StoredFile stored = fileStorageService.storeItemImage(id, file);
        return toItemDto(adminCrudService.updateItemImage(id, stored.url()));
    }

    /**
     * 删除指定商品
     *
     * @param id 要删除的商品ID
     */
    @DeleteMapping("/items/{id}")
    public void deleteItem(@PathVariable Long id) {
        adminCrudService.deleteItem(id);
    }

    /**
     * 将User实体对象转换为UserDto数据传输对象
     *
     * @param u User实体对象，包含用户的基本信息
     * @return UserDto数据传输对象，包含用户的id、用户名和创建时间（字符串格式）
     */
    private UserDto toUserDto(User u) {
        return new UserDto(
                u.getId(),
                u.getUsername(),
                u.isDisabled(),
                u.getCreatedAt() == null ? null : u.getCreatedAt().toString()
        );
    }

    /**
     * 将Item实体对象转换为ItemDto数据传输对象
     *
     * @param i Item实体对象，包含商品的基本信息
     * @return ItemDto数据传输对象，包含商品的id、名称、分类和创建时间（字符串格式）
     */
    private ItemDto toItemDto(Item i) {
        return new ItemDto(
                i.getId(),
                i.getName(),
                i.getCategory(),
                i.getImageUrl(),
                i.getCreatedAt() == null ? null : i.getCreatedAt().toString()
        );
    }

    /**
     * 查询商品详情
     *
     * @param id 商品ID
     * @return 商品数据传输对象
     */
    @GetMapping("/items/{id}")
    public ItemDto getItemById(@PathVariable Long id) {
        return adminCrudService.getItemById(id)
                .map(this::toItemDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
    }

    /**
     * 获取所有分类列表
     *
     * @return 分类名称列表
     */
    @GetMapping("/categories")
    public List<String> listCategories() {
        return adminCrudService.listAllCategories();
    }

    /**
     * 根据分类查询商品列表
     *
     * @param category 分类名称
     * @return 商品数据传输对象列表
     */
    @GetMapping("/categories/{category}/items")
    public List<ItemDto> listItemsByCategory(@PathVariable String category) {
        return adminCrudService.listItemsByCategory(category).stream()
                .map(this::toItemDto)
                .toList();
    }

    /**
     * 获取分类统计信息
     *
     * @return 分类统计信息列表
     */
    @GetMapping("/categories/stats")
    public List<CategoryStatsDto> listCategoryStats() {
        return adminCrudService.listCategoryStats().stream()
                .map(stats -> new CategoryStatsDto(stats.category(), stats.count()))
                .toList();
    }

    /**
     * 批量更新商品分类
     *
     * @param request 分类更新请求
     * @return 更新结果
     */
    @PutMapping("/categories/batch-update")
    public ResponseEntity<Map<String, Object>> updateItemsCategory(@RequestBody CategoryUpdateRequest request) {
        int count = adminCrudService.updateItemsCategory(request.oldCategory(), request.newCategory());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "分类更新成功",
                "updatedCount", count
        ));
    }

    // 请求DTO
    public record UserUpsertRequest(String username) {}
    public record UserDisabledRequest(boolean disabled) {}
    public record ItemUpsertRequest(String name, String category) {}
    public record CategoryUpdateRequest(String oldCategory, String newCategory) {}

    // 响应DTO
    public record UserDto(Long id, String username, boolean disabled, String createdAt) {}
    public record ItemDto(Long id, String name, String category, String imageUrl, String createdAt) {}
    public record CategoryStatsDto(String category, long count) {}
}
