-- =====================================================
-- 数据清洗脚本：移除ml-1m冷启动用户和物品
-- 清洗规则：
--   1. 移除评分数量 < 5 的用户（冷启动用户）
--   2. 移除评分数量 < 3 的物品（冷启动物品）
--   3. 级联删除：移除后可能产生的空评分记录
-- =====================================================

-- 步骤1：分析清洗前的数据分布
SELECT '=== 清洗前数据统计 ===' as info;
SELECT COUNT(DISTINCT user_id) as user_count FROM ratings;
SELECT COUNT(DISTINCT item_id) as item_count FROM ratings;
SELECT COUNT(*) as total_ratings FROM ratings;

-- 分析用户评分分布
SELECT
    CASE
        WHEN rating_count < 5 THEN '< 5 (将被移除)'
        WHEN rating_count < 10 THEN '5-9'
        WHEN rating_count < 20 THEN '10-19'
        WHEN rating_count < 50 THEN '20-49'
        ELSE '50+'
    END as rating_range,
    COUNT(*) as user_count
FROM (
    SELECT user_id, COUNT(*) as rating_count
    FROM ratings
    GROUP BY user_id
) t
GROUP BY rating_range
ORDER BY rating_range;

-- 分析物品评分分布
SELECT
    CASE
        WHEN rating_count < 3 THEN '< 3 (将被移除)'
        WHEN rating_count < 10 THEN '3-9'
        WHEN rating_count < 50 THEN '10-49'
        ELSE '50+'
    END as rating_range,
    COUNT(*) as item_count
FROM (
    SELECT item_id, COUNT(*) as rating_count
    FROM ratings
    GROUP BY item_id
) t
GROUP BY rating_range
ORDER BY rating_range;

-- =====================================================
-- 开始清洗
-- =====================================================

-- 步骤2：创建临时表存储需要保留的用户ID
CREATE TEMPORARY TABLE IF NOT EXISTS valid_users AS
SELECT user_id FROM ratings
GROUP BY user_id
HAVING COUNT(*) >= 5;

-- 步骤3：创建临时表存储需要保留的物品ID
CREATE TEMPORARY TABLE IF NOT EXISTS valid_items AS
SELECT item_id FROM ratings
GROUP BY item_id
HAVING COUNT(*) >= 3;

-- 步骤4：统计将被删除的记录数
SELECT '=== 将被删除的记录 ===' as info;
SELECT
    (SELECT COUNT(*) FROM ratings WHERE user_id NOT IN (SELECT user_id FROM valid_users)) as cold_start_users_deleted,
    (SELECT COUNT(*) FROM ratings WHERE item_id NOT IN (SELECT item_id FROM valid_items)) as cold_start_items_deleted,
    (SELECT COUNT(*) FROM ratings) as total_before;

-- 步骤5：执行清洗（只保留有效用户和物品的评分）
DELETE FROM ratings
WHERE user_id NOT IN (SELECT user_id FROM valid_users)
   OR item_id NOT IN (SELECT item_id FROM valid_items);

-- 步骤6：清洗后统计
SELECT '=== 清洗后数据统计 ===' as info;
SELECT COUNT(DISTINCT user_id) as user_count FROM ratings;
SELECT COUNT(DISTINCT item_id) as item_count FROM ratings;
SELECT COUNT(*) as total_ratings FROM ratings;

-- 清理临时表
DROP TEMPORARY TABLE IF EXISTS valid_users;
DROP TEMPORARY TABLE IF EXISTS valid_items;
