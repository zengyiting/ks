package com.example.recommend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 商品实体类
 *
 * <p>该类映射到数据库的items表，用于存储和管理商品信息。
 * 包含商品的基本属性：ID、名称、分类和创建时间。
 */
@Entity
@Table(name = "items")
public class Item {
    /** 商品唯一标识ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 商品名称，必填字段，最大长度200字符 */
    @Column(nullable = false, length = 200)
    private String name;

    /** 商品分类名称（用于兼容旧数据和显示） */
    @Column(length = 100)
    private String category;

    /** 商品图片地址 */
    @Column(name = "image_url", length = 255)
    private String imageUrl;

    /** 商品价格 */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    /** 商品描述 */
    @Column(name = "description", length = 2000)
    private String description;

    /** 商品是否下架，默认上架 */
    @Column(name = "disabled")
    private boolean disabled;

    /** 商品创建时间，默认为当前时间 */
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    /**
     * 默认无参构造函数
     *
     * <p>JPA规范要求实体类必须提供无参构造函数
     */
    public Item() {}

    /**
     * 带参数的构造函数
     *
     * @param name 商品名称
     * @param category 商品分类
     */
    public Item(String name, String category) {
        this.name = name;
        this.category = category;
    }

    /**
     * 获取商品ID
     *
     * @return 商品的唯一标识ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取商品名称
     *
     * @return 商品名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置商品名称
     *
     * @param name 要设置的商品名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取商品分类
     *
     * @return 商品分类
     */
    public String getCategory() {
        return category;
    }

    /**
     * 设置商品分类
     *
     * @param category 要设置的商品分类
     */
    public void setCategory(String category) {
        this.category = category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    /**
     * 获取商品创建时间
     *
     * @return 商品创建的时间戳
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置商品创建时间
     *
     * @param createdAt 要设置的创建时间
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

