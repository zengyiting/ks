UPDATE items
SET name = CONCAT('synthetic-item-', LPAD(id - 20000, 5, '0'))
WHERE id >= 20001 AND id < 20601;
