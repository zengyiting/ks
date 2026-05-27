package com.example.recommend.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 商品类别实体类
 * 
 * <p>该类映射到数据库的categories表，用于存储和管理商品分类信息。
 * 支持多级分类结构，类似淘宝/拼多多的分类体系。
 */
@Entity
@Table(name = "categories")
public class Category {
    /** 类别唯一标识ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 类别名称，必填字段，最大长度100字符 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 父类别ID，用于构建多级分类树，顶级分类为null */
    @Column(name = "parent_id")
    private Long parentId;

    /** 类别层级，顶级分类为1 */
    @Column(name = "level", nullable = false)
    private Integer level = 1;

    /** 排序号，用于控制类别显示顺序 */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /** 类别图标URL */
    @Column(name = "icon_url", length = 255)
    private String iconUrl;

    /** 是否启用 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 类别描述 */
    @Column(length = 500)
    private String description;

    /** 类别创建时间 */
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    /** 类别更新时间 */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * 默认无参构造函数
     */
    public Category() {}

    /**
     * 带参数的构造函数
     *
     * @param name 类别名称
     * @param parentId 父类别ID
     * @param level 类别层级
     */
    public Category(String name, Long parentId, Integer level) {
        this.name = name;
        this.parentId = parentId;
        this.level = level;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}