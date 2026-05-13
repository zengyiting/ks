-- 示例数据：用户、物品、评分
INSERT INTO users (id, username) VALUES
  (1, 'alice'),
  (2, 'bob'),
  (3, 'carol'),
  (4, 'dave')
ON DUPLICATE KEY UPDATE username = VALUES(username);

INSERT INTO items (id, name, category, image_url) VALUES
  (101, '《算法导论》', 'books', '/images/seed/book-1.svg'),
  (102, '《Java 编程思想》', 'books', '/images/seed/book-2.svg'),
  (103, '《机器学习》', 'books', '/images/seed/book-3.svg'),
  (201, '机械键盘', 'electronics', '/images/seed/device-1.svg'),
  (202, '降噪耳机', 'electronics', '/images/seed/device-2.svg'),
  (203, '人体工学椅', 'furniture', NULL)
ON DUPLICATE KEY UPDATE name = VALUES(name), category = VALUES(category), image_url = VALUES(image_url);

-- 评分：0-5
INSERT INTO ratings (user_id, item_id, score) VALUES
  (1, 101, 5), (1, 102, 4), (1, 201, 3),
  (2, 101, 4), (2, 103, 5), (2, 202, 4),
  (3, 102, 5), (3, 201, 4), (3, 203, 4),
  (4, 101, 2), (4, 202, 5), (4, 203, 3)
ON DUPLICATE KEY UPDATE score = VALUES(score);
