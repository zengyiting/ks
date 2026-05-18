-- 示例数据：用户、物品、评分
INSERT INTO users (id, username) VALUES
  (1, 'alice'),
  (2, 'bob'),
  (3, 'carol'),
  (4, 'dave')
ON DUPLICATE KEY UPDATE username = VALUES(username);

INSERT INTO items (id, name, category, image_url) VALUES
  (101, '《算法导论》', 'books', 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=400&h=300&fit=crop'),
  (102, '《Java 编程思想》', 'books', 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=400&h=300&fit=crop'),
  (103, '《机器学习》', 'books', 'https://images.unsplash.com/photo-1555949963-aa79dcee981c?w=400&h=300&fit=crop'),
  (201, '机械键盘', 'electronics', 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=400&h=300&fit=crop'),
  (202, '降噪耳机', 'electronics', 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=400&h=300&fit=crop'),
  (203, '人体工学椅', 'furniture', 'https://images.unsplash.com/photo-1592078615290-033ee584e267?w=400&h=300&fit=crop')
ON DUPLICATE KEY UPDATE name = VALUES(name), category = VALUES(category), image_url = VALUES(image_url);

-- 评分：0-5
INSERT INTO ratings (user_id, item_id, score) VALUES
  (1, 101, 5), (1, 102, 4), (1, 201, 3),
  (2, 101, 4), (2, 103, 5), (2, 202, 4),
  (3, 102, 5), (3, 201, 4), (3, 203, 4),
  (4, 101, 2), (4, 202, 5), (4, 203, 3)
ON DUPLICATE KEY UPDATE score = VALUES(score);
