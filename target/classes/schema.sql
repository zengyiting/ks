-- MySQL DDL (idempotent)
CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(100) NOT NULL UNIQUE,
  phone VARCHAR(20) UNIQUE,
  password_hash VARCHAR(64),
  disabled TINYINT(1) NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(200) NOT NULL,
  category VARCHAR(100),
  image_url VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ratings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  score DOUBLE NOT NULL,
  rated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_r_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_r_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
  CONSTRAINT uk_user_item UNIQUE (user_id, item_id),
  CONSTRAINT ck_score CHECK (score >= 0 AND score <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_item_flags (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  favorite TINYINT(1) NOT NULL DEFAULT 0,
  in_cart TINYINT(1) NOT NULL DEFAULT 0,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_flag_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_flag_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
  CONSTRAINT uk_user_item_flag UNIQUE (user_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Query indexes (repeatable with sql.init.continue-on-error=true)
ALTER TABLE ratings ADD INDEX idx_r_item_id (item_id);
ALTER TABLE ratings ADD INDEX idx_r_rated_at (rated_at);
ALTER TABLE ratings ADD INDEX idx_r_user_rated_at (user_id, rated_at);
ALTER TABLE items ADD INDEX idx_items_name (name);
ALTER TABLE items ADD INDEX idx_items_category (category);
ALTER TABLE user_item_flags ADD INDEX idx_flag_user (user_id);
ALTER TABLE user_item_flags ADD INDEX idx_flag_item (item_id);

ALTER TABLE items ADD COLUMN image_url VARCHAR(255);
ALTER TABLE users ADD COLUMN disabled TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN phone VARCHAR(20) UNIQUE;
ALTER TABLE users ADD COLUMN password_hash VARCHAR(64);
