import csv
import re

def escape_sql(s, max_len=2000):
    if s is None:
        return ''
    s = str(s)
    s = s.encode('utf-8', errors='ignore').decode('utf-8', errors='ignore')
    s = s.replace("'", "''").replace("\\", "\\\\")
    if len(s) > max_len:
        s = s[:max_len]
    return s

with open('d:/app/ks/recommendtwo/sql/shein_products.csv', 'r', encoding='utf-8', errors='ignore') as f:
    reader = csv.DictReader(f)

    with open('d:/app/ks/recommendtwo/sql/items_insert.sql', 'w', encoding='utf-8', errors='ignore') as out:
        out.write("-- Shein商品数据导入\n")
        out.write("-- 生成时间: 2026-05-22\n\n")
        out.write("SET FOREIGN_KEY_CHECKS=0;\n")
        out.write("TRUNCATE TABLE items;\n\n")

        count = 0
        for row in reader:
            name = escape_sql(row.get('product_name', ''), 200)
            if not name:
                continue

            category = escape_sql(row.get('category', 'Uncategorized'), 100)
            if not category:
                category = 'Uncategorized'

            price_str = row.get('final_price', '0') or '0'
            try:
                price = float(price_str) if price_str else 0
            except:
                price = 0

            image_url = escape_sql(row.get('main_image', ''), 255)
            description = escape_sql(row.get('description', ''), 2000)

            if image_url:
                image_part = "'" + image_url + "'"
            else:
                image_part = 'NULL'

            sql = "INSERT INTO items (name, category, price, image_url, description) VALUES ('{}', '{}', {}, {}, '{}');\n".format(
                name, category, price, image_part, description)
            out.write(sql)
            count += 1

        out.write("\nSET FOREIGN_KEY_CHECKS=1;\n")
        out.write("SELECT COUNT(*) AS total FROM items;\n")

print('Generated {} INSERT statements'.format(count))
