-- 清空categories表并重新插入匹配的分类
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE categories;
SET FOREIGN_KEY_CHECKS=1;

-- 插入顶级分类
INSERT INTO categories (name, parent_id, level, sort_order, description) VALUES
('数码产品', NULL, 1, 1, '手机、电脑、数码配件等'),
('服饰鞋包', NULL, 1, 2, '服装、鞋类、箱包等'),
('家居生活', NULL, 1, 3, '家具、家居用品、厨具等'),
('美妆护肤', NULL, 1, 4, '化妆品、护肤品、香水等'),
('食品饮料', NULL, 1, 5, '零食、饮料、生鲜等'),
('母婴用品', NULL, 1, 6, '婴儿用品、孕妇用品等'),
('运动户外', NULL, 1, 7, '运动器材、户外装备等'),
('图书文具', NULL, 1, 8, '书籍、文具用品等');

-- 查看插入结果
SELECT * FROM categories;
