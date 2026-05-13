SELECT i.id, i.name, i.category
FROM items i
WHERE i.category = 'toy'
  AND NOT EXISTS (
    SELECT 1 FROM ratings r
    WHERE r.user_id = 1 AND r.item_id = i.id
  )
ORDER BY i.id;
