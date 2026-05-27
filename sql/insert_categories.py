import mysql.connector

conn = mysql.connector.connect(
    host="localhost",
    user="root",
    password="2004zyta",
    database="recommend"
)

cursor = conn.cursor()

# 清空表
cursor.execute("SET FOREIGN_KEY_CHECKS=0")
cursor.execute("TRUNCATE TABLE categories")
cursor.execute("SET FOREIGN_KEY_CHECKS=1")

# 插入分类数据
categories = [
    ("数码产品", None, 1, 1, "手机、电脑、数码配件等"),
    ("服饰鞋包", None, 1, 2, "服装、鞋类、箱包等"),
    ("家居生活", None, 1, 3, "家具、家居用品、厨具等"),
    ("美妆护肤", None, 1, 4, "化妆品、护肤品、香水等"),
    ("食品饮料", None, 1, 5, "零食、饮料、生鲜等"),
    ("母婴用品", None, 1, 6, "婴儿用品、孕妇用品等"),
    ("运动户外", None, 1, 7, "运动器材、户外装备等"),
    ("图书文具", None, 1, 8, "书籍、文具用品等")
]

insert_sql = "INSERT INTO categories (name, parent_id, level, sort_order, description) VALUES (%s, %s, %s, %s, %s)"
cursor.executemany(insert_sql, categories)

conn.commit()
cursor.close()
conn.close()

print("分类数据插入成功！")
