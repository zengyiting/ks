LOAD DATA LOCAL INFILE 'd:/app/ks/recommendtwo/sql/tokopedia_products.csv'
INTO TABLE items
CHARACTER SET utf8mb4
FIELDS TERMINATED BY ','
OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 ROWS
(name, category, @dummy, @dummy, @dummy, price, @dummy, @dummy, description, @dummy, @dummy, @dummy, @dummy, @dummy, @dummy, @dummy, image_url, @dummy, @dummy, @dummy, @dummy, @dummy, @dummy, @dummy, @dummy, @dummy, @dummy, @dummy, @dummy, @dummy, @dummy, @dummy, @dummy)
SET category = IF(category = '' OR category IS NULL, 'Uncategorized', category),
    price = IF(price = '' OR price IS NULL OR price = 0, NULL, CAST(REPLACE(price, ',', '.') AS DECIMAL(10,2))),
    image_url = NULLIF(image_url, ''),
    description = LEFT(description, 2000);