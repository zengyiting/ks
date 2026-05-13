package com.example.recommend.service;

import com.example.recommend.model.Item;
import com.example.recommend.model.User;
import com.example.recommend.repository.ItemRepository;
import com.example.recommend.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 管理员CRUD服务类
 *
 * <p>提供用户和商品的增删改查功能，包括：
 * <ul>
 *   <li>用户管理：查询、创建、更新、删除</li>
 *   <li>商品管理：查询、创建、更新、删除</li>
 * </ul>
 * 所有操作都包含数据验证和异常处理。
 */
@Service
public class AdminCrudService {
    /** 用户列表分页大小，每页200条记录 */
    private static final int USER_PAGE_SIZE = 200;

    /** 商品列表分页大小，每页300条记录 */
    private static final int ITEM_PAGE_SIZE = 300;

    /** 用户数据访问层 */
    private final UserRepository userRepository;

    /** 商品数据访问层 */
    private final ItemRepository itemRepository;

    /** 推荐缓存管理服务 */
    private final RecommendationCacheService recommendationCacheService;

    /** 商品关联预计算服务 */
    private final ItemAssociationPrecomputeService itemAssociationPrecomputeService;

    /**
     * 构造函数，注入依赖
     *
     * @param userRepository 用户仓库
     * @param itemRepository 商品仓库
     */
    public AdminCrudService(
            UserRepository userRepository,
            ItemRepository itemRepository,
            RecommendationCacheService recommendationCacheService,
            ItemAssociationPrecomputeService itemAssociationPrecomputeService
    ) {
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.recommendationCacheService = recommendationCacheService;
        this.itemAssociationPrecomputeService = itemAssociationPrecomputeService;
    }

    /**
     * 查询用户列表（支持关键词搜索）
     *
     * <p>根据关键词搜索用户，返回第一页结果。如果不提供关键词则返回所有用户。
     * 搜索结果按ID升序排列。
     *
     * @param keyword 搜索关键词，可为null或空字符串
     * @return 用户列表，最多返回USER_PAGE_SIZE条记录
     */
    @Transactional(readOnly = true)
    public List<User> listUsers(String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        Pageable pageable = PageRequest.of(0, USER_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id"));
        if (k.isBlank()) {
            return userRepository.findAll(pageable).getContent();
        }
        return userRepository.findByUsernameContainingIgnoreCase(k, pageable).getContent();
    }

    /**
     * 查询商品列表（支持关键词搜索）
     *
     * <p>根据关键词搜索商品，返回第一页结果。如果不提供关键词则返回所有商品。
     * 搜索结果按ID升序排列。
     *
     * @param keyword 搜索关键词，可为null或空字符串
     * @return 商品列表，最多返回ITEM_PAGE_SIZE条记录
     */
    @Transactional(readOnly = true)
    public List<Item> listItems(String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        Pageable pageable = PageRequest.of(0, ITEM_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id"));
        if (k.isBlank()) {
            return itemRepository.findAll(pageable).getContent();
        }
        return itemRepository.findByNameContainingIgnoreCase(k, pageable).getContent();
    }

    /**
     * 默认密码常量
     */
    private static final String DEFAULT_PASSWORD = "123456";

    /**
     * 创建新用户
     *
     * <p>验证用户名非空且唯一后创建新用户，默认密码为123456。
     *
     * @param username 用户名，不能为空
     * @return 创建的用户对象
     * @throws ResponseStatusException 当用户名为空或已存在时抛出异常
     */
    @Transactional
    public User createUser(String username) {
        String name = normalize(username, "用户名不能为空");
        if (userRepository.findByUsername(name).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        User user = new User(name);
        user.setPasswordHash(hashPassword(DEFAULT_PASSWORD));
        User created = userRepository.save(user);
        recommendationCacheService.invalidateAll();
        return created;
    }

    /**
     * SHA-256密码加密
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * 更新用户信息
     *
     * <p>验证用户ID有效且用户存在后，更新用户名。确保新用户名不与其他用户冲突。
     *
     * @param id 用户ID，必须大于0
     * @param username 新的用户名，不能为空
     * @return 更新后的用户对象
     * @throws ResponseStatusException 当ID非法、用户不存在或用户名已存在时抛出异常
     */
    @Transactional
    public User updateUser(Long id, String username) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户ID非法");
        }
        String name = normalize(username, "用户名不能为空");
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        userRepository.findByUsername(name).ifPresent(exists -> {
            if (!exists.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
            }
        });
        user.setUsername(name);
        User updated = userRepository.save(user);
        recommendationCacheService.invalidateAll();
        return updated;
    }

    /**
     * 更新用户禁用状态
     *
     * @param id 用户ID，必须大于0
     * @param disabled 是否禁用
     * @return 更新后的用户对象
     */
    @Transactional
    public User updateUserDisabled(Long id, boolean disabled) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户ID非法");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        user.setDisabled(disabled);
        User updated = userRepository.save(user);
        recommendationCacheService.invalidateAll();
        return updated;
    }

    /**
     * 删除用户
     *
     * <p>验证用户ID有效且用户存在后执行删除操作。
     *
     * @param id 用户ID，必须大于0
     * @throws ResponseStatusException 当ID非法或用户不存在时抛出异常
     */
    @Transactional
    public void deleteUser(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户ID非法");
        }
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        userRepository.deleteById(id);
        itemAssociationPrecomputeService.markDirty();
        recommendationCacheService.invalidateAll();
    }

    /**
     * 创建新商品
     *
     * <p>验证商品名非空后创建新商品。分类可以为空。
     *
     * @param name 商品名称，不能为空
     * @param category 商品分类，可以为空
     * @return 创建的商品对象
     * @throws ResponseStatusException 当商品名为空时抛出异常
     */
    @Transactional
    public Item createItem(String name, String category) {
        String safeName = normalize(name, "商品名不能为空");
        Item item = new Item(safeName, normalizeNullable(category));
        Item created = itemRepository.save(item);
        recommendationCacheService.invalidateAll();
        return created;
    }

    /**
     * 更新商品信息
     *
     * <p>验证商品ID有效且商品存在后，更新商品名称和分类。
     *
     * @param id 商品ID，必须大于0
     * @param name 商品名称，不能为空
     * @param category 商品分类，可以为空
     * @return 更新后的商品对象
     * @throws ResponseStatusException 当ID非法或商品不存在时抛出异常
     */
    @Transactional
    public Item updateItem(Long id, String name, String category) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品ID非法");
        }
        String safeName = normalize(name, "商品名不能为空");
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        item.setName(safeName);
        item.setCategory(normalizeNullable(category));
        Item updated = itemRepository.save(item);
        recommendationCacheService.invalidateAll();
        return updated;
    }

    @Transactional
    public Item updateItemImage(Long id, String imageUrl) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品ID非法");
        }
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        item.setImageUrl(normalizeNullable(imageUrl));
        Item updated = itemRepository.save(item);
        recommendationCacheService.invalidateAll();
        return updated;
    }

    /**
     * 删除商品
     *
     * <p>验证商品ID有效且商品存在后执行删除操作。
     *
     * @param id 商品ID，必须大于0
     * @throws ResponseStatusException 当ID非法或商品不存在时抛出异常
     */
    @Transactional
    public void deleteItem(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品ID非法");
        }
        if (!itemRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        itemRepository.deleteById(id);
        itemAssociationPrecomputeService.markDirty();
        recommendationCacheService.invalidateAll();
    }

    /**
     * 规范化字符串（非空验证）
     *
     * <p>去除字符串首尾空格，并验证非空。如果为空则抛出异常。
     *
     * @param value 待规范的字符串
     * @param message 验证失败时的错误消息
     * @return 去除空格后的字符串
     * @throws ResponseStatusException 当字符串为null或空白时抛出异常
     */
    private String normalize(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    /**
     * 规范化可空字符串
     *
     * <p>去除字符串首尾空格，如果结果为空白则返回null。
     *
     * @param value 待规范的字符串，可以为null
     * @return 去除空格后的字符串，如果为空白则返回null
     */
    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isBlank() ? null : v;
    }
}
