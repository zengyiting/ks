-- 示例数据：用户、物品、评分
INSERT INTO users (id, username) VALUES
  (1, 'alice'),
  (2, 'bob'),
  (3, 'carol'),
  (4, 'dave')
ON DUPLICATE KEY UPDATE username = VALUES(username);

INSERT INTO items (id, name, category, image_url) VALUES
  (101, '肖申克的救赎', 'movies', 'https://image.tmdb.org/t/p/w500/9cj1846f89Xz7k8n3g8hP7qQ8q.jpg'),
  (102, '阿甘正传', 'movies', 'https://image.tmdb.org/t/p/w500/arw2vc3hEP1eIiLqCjz0x3VgqPj.jpg'),
  (103, '黑客帝国', 'movies', 'https://image.tmdb.org/t/p/w500/f89U3ADr1oixB1sU7yKXr8a7XK.jpg'),
  (201, '盗梦空间', 'movies', 'https://image.tmdb.org/t/p/w500/ljsZTbVsrQSqZgWeep2B1QiDKuh.jpg'),
  (202, '星际穿越', 'movies', 'https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg'),
  (203, '蝙蝠侠：黑暗骑士', 'movies', 'https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911BTUgMe1nNaD.jpg')
ON DUPLICATE KEY UPDATE name = VALUES(name), category = VALUES(category), image_url = VALUES(image_url);

-- 评分：0-5
INSERT INTO ratings (user_id, item_id, score) VALUES
  (1, 101, 5), (1, 102, 4), (1, 201, 3),
  (2, 101, 4), (2, 103, 5), (2, 202, 4),
  (3, 102, 5), (3, 201, 4), (3, 203, 4),
  (4, 101, 2), (4, 202, 5), (4, 203, 3)
ON DUPLICATE KEY UPDATE score = VALUES(score);
