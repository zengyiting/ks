import csv

with open("d:/app/ks/recommendtwo/sql/chinese_products.csv", "r", encoding="utf-8-sig") as f:
    reader = csv.DictReader(f)
    
    with open("d:/app/ks/recommendtwo/sql/insert_chinese_products.sql", "w", encoding="utf-8") as out:
        out.write("SET FOREIGN_KEY_CHECKS=0;\n")
        out.write("TRUNCATE TABLE items;\n\n")
        
        count = 0
        for row in reader:
            name = row["name"].replace("'", "''")
            category = row["category"].replace("'", "''")
            price = row["price"]
            image_url = row["image_url"].replace("'", "''")
            desc = row["description"].replace("'", "''")
            
            out.write(f"INSERT INTO items (name, category, price, image_url, description) VALUES ('{name}', '{category}', {price}, '{image_url}', '{desc}');\n")
            count += 1
            
        out.write("\nSET FOREIGN_KEY_CHECKS=1;\n")
        out.write(f"SELECT COUNT(*) AS total FROM items;\n")
        
print(f"成功生成 {count} 条INSERT语句！")
