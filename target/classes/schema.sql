-- 创建商品表
CREATE TABLE IF NOT EXISTS items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(100),
    price DECIMAL(10,2),
    image_url VARCHAR(255),
    description VARCHAR(2000),
    disabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_items_name (name),
    INDEX idx_items_category (category),
    INDEX idx_items_disabled (disabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 兼容已存在的表：添加 disabled 列（如果尚不存在）
-- 注意：此语句在表已存在时会报错，但 continue-on-error: true 会忽略
ALTER TABLE items ADD COLUMN disabled BOOLEAN DEFAULT FALSE AFTER description;
ALTER TABLE items ADD INDEX idx_items_disabled (disabled);
