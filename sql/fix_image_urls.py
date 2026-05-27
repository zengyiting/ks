import pymysql

conn = pymysql.connect(
    host='localhost',
    user='root',
    password='2004zyta',
    database='recommend',
    charset='utf8mb4'
)
cursor = conn.cursor()

cursor.execute("SELECT id FROM items")
items = cursor.fetchall()

update_sql = "UPDATE items SET image_url = %s WHERE id = %s"

count = 0
for (item_id,) in items:
    image_url = f"https://picsum.photos/seed/{item_id}/400/400"
    cursor.execute(update_sql, (image_url, item_id))
    count += 1

conn.commit()
print(f"已更新 {count} 条商品图片URL为picsum.photos随机图片")

cursor.execute("SELECT id, name, category, image_url FROM items LIMIT 5")
sample = cursor.fetchall()
for s in sample:
    print(f"ID: {s[0]}, 名称: {s[1]}, 图片: {s[3]}")

cursor.close()
conn.close()
