# -*- coding: utf-8 -*-
"""
导入 MovieLens 1M 评分数据到 recommend 数据库
"""

import pymysql

conn = pymysql.connect(
    host='localhost',
    user='root',
    password='2004zyta',
    database='recommend',
    charset='utf8mb4'
)

cursor = conn.cursor()

# 获取已存在的用户ID
cursor.execute("SELECT id FROM users")
existing_users = set(row[0] for row in cursor.fetchall())

# 获取已存在的商品ID
cursor.execute("SELECT id FROM items")
existing_items = set(row[0] for row in cursor.fetchall())

print(f"已存在用户: {len(existing_users)}")
print(f"已存在商品: {len(existing_items)}")

# 第一遍：收集所有需要的用户ID
ratings_file = r'D:\app\ks\recommendtwo\docs\ml-1m\ratings.dat'
user_ids_to_create = set()
valid_ratings = []

with open(ratings_file, 'r', encoding='latin-1') as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        parts = line.split('::')
        if len(parts) != 4:
            continue
        user_id = int(parts[0])
        item_id = int(parts[1])
        score = int(parts[2])
        if item_id not in existing_items:
            continue
        if user_id not in existing_users:
            user_ids_to_create.add(user_id)
        valid_ratings.append((user_id, item_id, score))

print(f"需要创建 {len(user_ids_to_create)} 个用户")
print(f"有效评分记录: {len(valid_ratings)} 条")

# 创建缺失的用户
created = 0
for uid in sorted(user_ids_to_create):
    try:
        cursor.execute(
            "INSERT INTO users (id, username, password_hash) VALUES (%s, %s, %s)",
            (uid, f'movielens_user_{uid}', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92')
        )
        created += 1
    except:
        pass

conn.commit()
print(f"已创建 {created} 个用户")

# 第二遍：导入评分
batch = []
batch_size = 5000
total_inserted = 0
total_updated = 0

for user_id, item_id, score in valid_ratings:
    batch.append((user_id, item_id, score))
    if len(batch) >= batch_size:
        cursor.executemany(
            "INSERT INTO ratings (user_id, item_id, score) VALUES (%s, %s, %s) ON DUPLICATE KEY UPDATE score = VALUES(score)",
            batch
        )
        total_inserted += cursor.rowcount
        batch = []
        if total_inserted % 100000 == 0:
            print(f"已导入 {total_inserted} 条...")

if batch:
    cursor.executemany(
        "INSERT INTO ratings (user_id, item_id, score) VALUES (%s, %s, %s) ON DUPLICATE KEY UPDATE score = VALUES(score)",
        batch
    )
    total_inserted += cursor.rowcount

conn.commit()

cursor.execute("SELECT COUNT(*) FROM ratings")
final_count = cursor.fetchone()[0]
print(f"导入完成，ratings 表共 {final_count} 条记录")

cursor.close()
conn.close()
