-- 清空指定库的所有表数据（保留表结构）
-- 用法示例：
--   mysql -u root -p -h 127.0.0.1 -P 3306 recommend < .\\sql\\clear-db-data-only.sql
--
-- 注意：
-- 1) 这是 DELETE（不是 DROP/TRUNCATE），会保留表结构。
-- 2) 会临时关闭 FOREIGN_KEY_CHECKS 以避免外键删除顺序问题。
-- 3) 默认库名使用当前连接库；如果你想显式指定库名，可修改下面 @schema。

SET @schema := DATABASE(); -- 或者改成 'recommend'
SET FOREIGN_KEY_CHECKS = 0;

DROP PROCEDURE IF EXISTS clear_db_data_only;
DELIMITER $$
CREATE PROCEDURE clear_db_data_only(IN p_schema VARCHAR(128))
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE v_table VARCHAR(128);
  DECLARE cur CURSOR FOR
    SELECT table_name
    FROM information_schema.tables
    WHERE table_schema = p_schema
      AND table_type = 'BASE TABLE';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_table;
    IF done = 1 THEN
      LEAVE read_loop;
    END IF;
    SET @sql = CONCAT('DELETE FROM `', p_schema, '`.`', v_table, '`');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;

CALL clear_db_data_only(@schema);
DROP PROCEDURE clear_db_data_only;

SET FOREIGN_KEY_CHECKS = 1;

-- 可选：检查各表行数（information_schema.table_rows 对 InnoDB 是估算值）
SELECT table_name, table_rows
FROM information_schema.tables
WHERE table_schema = @schema
  AND table_type = 'BASE TABLE'
ORDER BY table_name;

