package com.example.recommend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "user_item_flags",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_item_flag", columnNames = {"user_id", "item_id"})
)
public class UserItemFlag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private boolean favorite;

    @Column(name = "in_cart", nullable = false)
    private boolean inCart;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public UserItemFlag() {}

    public UserItemFlag(Long userId, Long itemId) {
        this.userId = userId;
        this.itemId = itemId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public boolean isInCart() {
        return inCart;
    }

    public void setInCart(boolean inCart) {
        this.inCart = inCart;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
