package com.example.recommend.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 评分实体类
 * 
 * <p>该类映射到数据库的ratings表，用于存储用户对商品的评分信息。
 * 包含评分的基本属性：ID、用户、商品、评分分数和评分时间。
 * 通过唯一约束确保同一用户对同一商品只能有一个评分记录。
 */
@Entity
@Table(
    name = "ratings",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_item", columnNames = {"user_id", "item_id"})
)
public class Rating {
    /** 评分记录唯一标识ID，自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 评分用户，多对一关联关系，延迟加载，必填字段 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 被评分的商品，多对一关联关系，延迟加载，必填字段 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** 评分分数，必填字段 */
    @Column(nullable = false)
    private Double score;

    /** 评分时间，默认为当前时间 */
    @Column(name = "rated_at")
    private Instant ratedAt = Instant.now();

    /**
     * 默认无参构造函数
     * 
     * <p>JPA规范要求实体类必须提供无参构造函数
     */
    public Rating() {}

    /**
     * 带参数的构造函数
     * 
     * @param user 评分用户
     * @param item 被评分的商品
     * @param score 评分分数
     */
    public Rating(User user, Item item, Double score) {
        this.user = user;
        this.item = item;
        this.score = score;
    }

    /**
     * 获取评分记录ID
     * 
     * @return 评分记录的唯一标识ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取评分用户
     * 
     * @return 评分的用户对象
     */
    public User getUser() {
        return user;
    }

    /**
     * 设置评分用户
     * 
     * @param user 要设置的评分用户
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * 获取被评分的商品
     * 
     * @return 被评分的商品对象
     */
    public Item getItem() {
        return item;
    }

    /**
     * 设置被评分的商品
     * 
     * @param item 要设置的商品对象
     */
    public void setItem(Item item) {
        this.item = item;
    }

    /**
     * 获取评分分数
     * 
     * @return 评分分数值
     */
    public Double getScore() {
        return score;
    }

    /**
     * 设置评分分数
     * 
     * @param score 要设置的评分分数
     */
    public void setScore(Double score) {
        this.score = score;
    }

    /**
     * 获取评分时间
     * 
     * @return 评分的时间戳
     */
    public Instant getRatedAt() {
        return ratedAt;
    }

    /**
     * 设置评分时间
     * 
     * @param ratedAt 要设置的评分时间
     */
    public void setRatedAt(Instant ratedAt) {
        this.ratedAt = ratedAt;
    }
}

