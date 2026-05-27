# -*- coding: utf-8 -*-
"""
清空原有数据，加载 ml-100k 电影数据并转化为商品数据
"""

import pymysql
import random

random.seed(42)

conn = pymysql.connect(
    host='localhost',
    user='root',
    password='2004zyta',
    database='recommend',
    charset='utf8mb4'
)

cursor = conn.cursor()

# ========== 1. 清空所有数据 ==========
print("清空原有数据...")
cursor.execute("SET FOREIGN_KEY_CHECKS=0")
cursor.execute("TRUNCATE TABLE ratings")
cursor.execute("TRUNCATE TABLE user_item_flags")
cursor.execute("TRUNCATE TABLE items")
cursor.execute("TRUNCATE TABLE users")
cursor.execute("SET FOREIGN_KEY_CHECKS=1")
cursor.execute("ALTER TABLE items AUTO_INCREMENT = 1")
cursor.execute("ALTER TABLE users AUTO_INCREMENT = 1")
cursor.execute("ALTER TABLE ratings AUTO_INCREMENT = 1")
cursor.execute("ALTER TABLE user_item_flags AUTO_INCREMENT = 1")
conn.commit()
print("已清空")

# ========== 2. 类别映射 ==========
GENRE_MAP = {
    'Action': '动作',
    'Adventure': '冒险',
    'Animation': '动画',
    "Children's": '儿童',
    'Comedy': '喜剧',
    'Crime': '犯罪',
    'Documentary': '纪录片',
    'Drama': '剧情',
    'Fantasy': '奇幻',
    'Film-Noir': '黑色电影',
    'Horror': '恐怖',
    'Musical': '音乐',
    'Mystery': '悬疑',
    'Romance': '爱情',
    'Sci-Fi': '科幻',
    'Thriller': '惊悚',
    'War': '战争',
    'Western': '西部',
}

GENRE_DETAILS = {
    '动作': '紧张刺激动作场面',
    '冒险': '探索未知冒险旅程',
    '动画': '精美动画制作',
    '儿童': '寓教于乐家庭观影',
    '喜剧': '轻松幽默娱乐体验',
    '犯罪': '悬疑推理犯罪故事',
    '纪录片': '真实记录深度内容',
    '剧情': '深刻情感故事描写',
    '奇幻': '想象力丰富奇幻世界',
    '黑色电影': '经典复古黑色风格',
    '恐怖': '惊悚恐怖氛围体验',
    '音乐': '优美音乐歌舞表演',
    '悬疑': '扑朔迷离悬疑剧情',
    '爱情': '浪漫温馨爱情故事',
    '科幻': '未来科技想象空间',
    '惊悚': '紧张刺激悬疑氛围',
    '战争': '历史战争宏大场面',
    '西部': '经典西部牛仔风格',
}

EDITIONS = ['收藏版', '限定版', '精装版', '经典版', '纪念版', '豪华版']

DESCRIPTIONS = [
    '本商品属于{category}类影视作品，具有较高用户评分与观影热度，适合喜欢{genre_detail}题材的用户。',
    '经典{category}类作品，深受用户喜爱与好评，适合{genre_detail}爱好者收藏与观影。',
    '本商品为{category}题材优质内容，用户口碑良好，推荐喜欢{genre_detail}的用户购买。',
    '热门{category}类影视作品，具有广泛的用户基础与良好评价，适合{genre_detail}爱好者。',
]

# ========== 3. 解析 u.item 并插入商品 ==========
print("解析 ml-100k 电影数据...")
items_file = r'D:\app\ks\recommendtwo\docs\ml-100k\u.item'

items = []
with open(items_file, 'r', encoding='latin-1') as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        parts = line.split('|')
        if len(parts) < 19:
            continue
        movie_id = int(parts[0])
        title = parts[1]
        # genres are at index 5-23
        genre_flags = parts[5:24]
        genre_names = []
        for i, flag in enumerate(genre_flags):
            if flag == '1':
                # Find genre name by index
                for gname, gidx in GENRE_MAP.items():
                    # We need to map index to genre name
                    pass
                # Use the order from u.genre
                genre_order = ['unknown', 'Action', 'Adventure', 'Animation', "Children's",
                              'Comedy', 'Crime', 'Documentary', 'Drama', 'Fantasy',
                              'Film-Noir', 'Horror', 'Musical', 'Mystery', 'Romance',
                              'Sci-Fi', 'Thriller', 'War', 'Western']
                gname = genre_order[i]
                if gname != 'unknown' and gname in GENRE_MAP:
                    genre_names.append(GENRE_MAP[gname])
        
        if not genre_names:
            genre_names = ['其他']
        
        category = '/'.join(genre_names)
        edition = random.choice(EDITIONS)
        name = f"{title} {edition}"
        price = round(random.uniform(29.0, 399.9), 1)
        primary_genre = genre_names[0]
        genre_detail = GENRE_DETAILS.get(primary_genre, '优质影视内容')
        desc_template = random.choice(DESCRIPTIONS)
        description = desc_template.format(category=category, genre_detail=genre_detail)
        image_url = 'https://image.tmdb.org/t/p/w500/default_poster.jpg'
        
        items.append((movie_id, name, category, price, image_url, description))

print(f"解析到 {len(items)} 个商品")

# 批量插入商品
batch = []
batch_size = 500
for item in items:
    batch.append(item)
    if len(batch) >= batch_size:
        cursor.executemany(
            "INSERT INTO items (id, name, category, price, image_url, description) VALUES (%s, %s, %s, %s, %s, %s)",
            batch
        )
        conn.commit()
        batch = []

if batch:
    cursor.executemany(
        "INSERT INTO items (id, name, category, price, image_url, description) VALUES (%s, %s, %s, %s, %s, %s)",
        batch
    )
    conn.commit()

cursor.execute("SELECT COUNT(*) FROM items")
item_count = cursor.fetchone()[0]
print(f"已插入 {item_count} 个商品")

# ========== 4. 解析 u.data 并插入评分 ==========
print("解析 ml-100k 评分数据...")
ratings_file = r'D:\app\ks\recommendtwo\docs\ml-100k\u.data'

existing_items = set(item[0] for item in items)
user_ids = set()
valid_ratings = []

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
            continue
        
        user_ids.add(user_id)
        valid_ratings.append((user_id, item_id, score))

print(f"有效评分: {len(valid_ratings)} 条")
print(f"需要创建 {len(user_ids)} 个用户")

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

# ========== 5. 统计 ==========
cursor.execute("SELECT COUNT(*) FROM items")
item_count = cursor.fetchone()[0]
cursor.execute("SELECT COUNT(*) FROM users")
user_count = cursor.fetchone()[0]
cursor.execute("SELECT COUNT(*) FROM ratings")
rating_count = cursor.fetchone()[0]

print(f"\n===== 导入完成 =====")
print(f"  商品数: {item_count}")
print(f"  用户数: {user_count}")
print(f"  评分数: {rating_count}")

cursor.close()
conn.close()
