SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE items;
LOAD DATA LOCAL INFILE 'd:/app/ks/recommendtwo/sql/items_clean.csv'
INTO TABLE items
CHARACTER SET utf8mb4
FIELDS TERMINATED BY ','
OPTIONALLY ENCLOSED BY '"'
ESCAPED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 ROWS
(name, category, price, image_url, description);
SET FOREIGN_KEY_CHECKS=1;
SELECT COUNT(*) AS total_inserted FROM items;