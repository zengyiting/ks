SELECT r.user_id, r.item_id, i.name, i.category, r.score
FROM ratings r
JOIN items i ON i.id = r.item_id
WHERE r.user_id = 1
ORDER BY r.score DESC, r.item_id ASC
LIMIT 20;

SELECT i.category, AVG(r.score) AS avg_score, COUNT(*) AS cnt
FROM ratings r
JOIN items i ON i.id = r.item_id
WHERE r.user_id = 1
GROUP BY i.category
ORDER BY avg_score DESC, cnt DESC;
