-- 示例数据：用户、物品、评分
-- 注意：商品数据已通过 import_movielens_as_items.sql 从 MovieLens 1M 导入

-- 评分：0-5 (示例数据，实际数据来自 MovieLens ratings.dat)
INSERT INTO ratings (user_id, item_id, score) VALUES
  (1, 1, 5), (1, 2, 4), (1, 6, 3),
  (2, 1, 4), (2, 3, 5), (2, 10, 4),
  (3, 2, 5), (3, 6, 4), (3, 16, 4),
  (4, 1, 2), (4, 10, 5), (4, 16, 3)
ON DUPLICATE KEY UPDATE score = VALUES(score);
