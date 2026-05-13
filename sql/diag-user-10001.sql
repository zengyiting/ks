SELECT COUNT(*) AS total_items FROM items;
SELECT COUNT(*) AS total_users FROM users;
SELECT COUNT(*) AS user_10001_ratings FROM ratings WHERE user_id = 10001;
SELECT COUNT(DISTINCT item_id) AS user_10001_distinct_items FROM ratings WHERE user_id = 10001;
SELECT COUNT(*) AS unseen_items_for_10001
FROM items i
WHERE NOT EXISTS (
  SELECT 1 FROM ratings r WHERE r.user_id = 10001 AND r.item_id = i.id
);
