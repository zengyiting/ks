# -*- coding: utf-8 -*-
"""
导入 ml-100k u.data 评分数据
先创建用户，再插入评分
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

# 获取已存在的商品ID
cursor.execute("SELECT id FROM items")
existing_items = set(row[0] for row in cursor.fetchall())
print(f"已存在商品: {len(existing_items)} 个")

# 第一遍：收集所有用户ID和有效评分
ratings_file = r'D:\app\ks\recommendtwo\docs\ml-100k\u.data'

user_ids = set()
valid_ratings = []
total_skipped = 0

with open(ratings_file, 'r', encoding='utf-8') as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        parts = line.split('\t')
        if len(parts) < 3:
            continue
        user_id = int(parts[0])
        item_id = int(parts[1])
        score = int(parts[2])
        
        if item_id not in existing_items:
            total_skipped += 1
            continue
        
        user_ids.add(user_id)
        valid_ratings.append((user_id, item_id, score))

print(f"需要创建 {len(user_ids)} 个用户")
print(f"有效评分: {len(valid_ratings)} 条")

# 创建用户
created = 0
for uid in sorted(user_ids):
    try:
        cursor.execute(
            "INSERT INTO users (id, username, password_hash) VALUES (%s, %s, %s)",
            (uid, f'user_{uid}', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92')
        )
        created += 1
    except:
        pass

conn.commit()
print(f"已创建 {created} 个用户")

# 插入评分
batch = []
batch_size = 2000
total_inserted = 0

for rating in valid_ratings:
    batch.append(rating)
    if len(batch) >= batch_size:
        cursor.executemany(
            "INSERT INTO ratings (user_id, item_id, score) VALUES (%s, %s, %s)",
            batch
        )
        total_inserted += len(batch)
        batch = []

if batch:
    cursor.executemany(
        "INSERT INTO ratings (user_id, item_id, score) VALUES (%s, %s, %s)",
        batch
    )
    total_inserted += len(batch)

conn.commit()

# 统计
cursor.execute("SELECT COUNT(*) FROM ratings")
rating_count = cursor.fetchone()[0]
cursor.execute("SELECT COUNT(*) FROM users")
user_count = cursor.fetchone()[0]

print(f"\n导入完成:")
print(f"  用户数: {user_count}")
print(f"  评分数: {rating_count}")
print(f"  跳过: {total_skipped}")

cursor.close()
conn.close()
