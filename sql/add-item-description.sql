-- 为items表添加缺失的description列（如果已存在则跳过）
-- 使用MySQL存储过程方式添加列（兼容MySQL 8.0）
DROP PROCEDURE IF EXISTS add_description_column;
DELIMITER //
CREATE PROCEDURE add_description_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'items'
        AND COLUMN_NAME = 'description'
    ) THEN
        ALTER TABLE items ADD COLUMN description VARCHAR(2000);
    END IF;
END //
DELIMITER ;
CALL add_description_column();
DROP PROCEDURE IF EXISTS add_description_column;
