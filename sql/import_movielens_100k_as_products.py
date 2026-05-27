import pymysql
import random
import os

conn = pymysql.connect(
    host='localhost',
    user='root',
    password='2004zyta',
    database='recommend',
    charset='utf8mb4'
)

CATEGORY_MAP = {
    'Action': '动作片',
    'Adventure': '冒险片',
    'Animation': '动画片',
    "Children's": '儿童片',
    'Comedy': '喜剧片',
    'Crime': '犯罪片',
    'Documentary': '纪录片',
    'Drama': '剧情片',
    'Fantasy': '奇幻片',
    'Film-Noir': '黑色电影',
    'Horror': '恐怖片',
    'Musical': '音乐剧',
    'Mystery': '悬疑片',
    'Romance': '爱情片',
    'Sci-Fi': '科幻片',
    'Thriller': '惊悚片',
    'War': '战争片',
    'Western': '西部片',
    'unknown': '其他'
}

MOVIE_TRANSLATIONS = {
    'Toy Story': '玩具总动员',
    'GoldenEye': '黄金眼',
    'Four Rooms': '四个房间',
    'Get Shorty': '矮子当上老板',
    'Copycat': '复制娇妻',
    'Shanghai Triad': '上海滩',
    'Twelve Monads': '十二猴子',
    'Babe': '小猪宝贝',
    'Dead Man Walking': '死囚漫步',
    'Richard III': '理查三世',
    'Postino': '邮差',
    'Titanic': '泰坦尼克号',
    'Scream': '惊声尖叫',
    'The Net': '网络迷宫',
    'Independence Day': '独立日',
    'Casper': '鬼马小精灵',
    'Batman Forever': '蝙蝠侠永远',
    'Jaws': '大白鲨',
    'Mary Poppins': '欢乐满人间',
    'The Sound of Music': '音乐之声',
    'Casablanca': '卡萨布兰卡',
    'Rocky': '洛奇',
    'Star Wars': '星球大战',
    'E.T.': 'E.T.外星人',
    'The Godfather': '教父',
    'Gone with the Wind': '乱世佳人',
    'Forrest Gump': '阿甘正传',
    'Schindler': '辛德勒的名单',
    'Raging Bull': '愤怒的公牛',
    'Wizard of Oz': '绿野仙踪',
    'Psycho': '惊魂记',
    'The African Queen': '非洲女王号',
    'The Usual Suspects': '普通嫌疑犯',
    'American Beauty': '美国美人',
    'The Sixth Sense': '灵异第六感',
    'L.A. Confidential': '洛城机密',
    'Shakespeare': '莎翁情史',
    'The English Patient': '英国病人',
    'Saving Private Ryan': '拯救大兵瑞恩',
    'The Silence of the Lambs': '沉默的羔羊',
    'Good Will Hunting': '心灵捕手',
    'Rain Man': '雨人',
    'My Left Foot': '我的左脚',
    'Chariots of Fire': '火战车',
    'Gandhi': '甘地传',
    'Platoon': '野战排',
    'The Last Emperor': '末代皇帝',
    'Amadeus': '莫扎特传',
    'Out of Africa': '走出非洲',
    'The Bridge on the River Kwai': '桂河大桥',
    'Ben-Hur': '宾虚',
    'Lawrence of Arabia': '阿拉伯的劳伦斯',
    'The Godfather Part II': '教父2',
    'Apocalypse Now': '现代启示录',
    'Return of the Jedi': '绝地归来',
    'Raiders of the Lost Ark': '夺宝奇兵',
    'Back to the Future': '回到未来',
    'Home Alone': '小鬼当家',
    'Jurassic Park': '侏罗纪公园',
    'The Lost World': '失落的世界',
    'Mission: Impossible': '不可能的任务',
    'Speed': '生死时速',
    'Die Hard': '虎胆龙威',
    'The Rock': '勇闯夺命岛',
    'Armageddon': '世界末日',
    'The Mask of Zorro': '佐罗的面具',
    'The Mummy': '木乃伊',
    'Jerry Maguire': '征服情海',
    'A Beautiful Mind': '美丽心灵',
    'Gladiator': '角斗士',
    'Braveheart': '勇敢的心',
    'The Princess Bride': '公主新娘',
    'Cast Away': '荒岛余生',
    'The Terminal': '幸福终点站',
    'Catch Me If You Can': '猫鼠游戏',
    'Meet the Parents': '拜见岳父母',
    'Nutty Professor': '肥佬教授',
    'Big Lebowski': '大人物拉里',
    'Fargo': '冰血暴',
    'No Country for Old Men': '老无所依',
    'There Will Be Blood': '血色将至',
    'Pulp Fiction': '低俗小说',
    'Reservoir Dogs': '落水狗',
    'Kill Bill': '杀死比尔',
    'Inglourious Basterds': '恶名昭著',
    'Django Unchained': '被解救的姜戈',
    'The Hateful Eight': '八恶人',
    'Once Upon a Time in Hollywood': '好莱坞往事',
    'Memento': '记忆碎片',
    'The Dark Knight': '黑暗骑士',
    'The Dark Knight Rises': '黑暗骑士崛起',
    'Inception': '盗梦空间',
    'Interstellar': '星际穿越',
    'The Prestige': '致命魔术',
    'The Departed': '无间行者',
    'Shutter Island': '禁闭岛',
    'The King Speech': '国王的演讲',
    'Black Swan': '黑天鹅',
    'Whiplash': '爆裂鼓手',
    'Birdman': '鸟人',
    'La La Land': '爱乐之城',
    'Moonlight': '月光男孩',
    'Parasite': '寄生虫',
    'Bohemian Rhapsody': '波西米亚狂想曲',
    'Joker': '小丑',
    '1917': '1917',
    'Casino Royale': '007：皇家赌场',
    'Skyfall': '007：大破天幕杀机',
    'Spectre': '007：幽灵党',
    'Shrek': '怪物史瑞克',
    'Shrek 2': '怪物史瑞克2',
    'Ice Age': '冰河世纪',
    'Finding Nemo': '海底总动员',
    'The Incredibles': '超人特工队',
    'Up': '飞屋环游记',
    'Inside Out': '头脑特工队',
    'Coco': '寻梦环游记',
    'Soul': '心灵奇旅',
    'Monsters Inc': '怪物电力公司',
    'Cars': '汽车总动员',
    'Ratatouille': '美食总动员',
    'WALL-E': '机器人瓦力',
    'Brave': '勇敢传说',
    'Frozen': '冰雪奇缘',
    'Moana': '海洋奇缘',
    'Zootopia': '疯狂动物城',
    'The Lion King': '狮子王',
    'Beauty and the Beast': '美女与野兽',
    'Aladdin': '阿拉丁',
    'The Little Mermaid': '小美人鱼',
    'Mulan': '花木兰',
    'Tarzan': '泰山',
    'Hercules': '大力士',
    'Cinderella': '灰姑娘',
    'Snow White': '白雪公主',
    'Pinocchio': '木偶奇遇记',
    'Dumbo': '小飞象',
    'Bambi': '小鹿斑比',
    '101 Dalmatians': '101斑点狗',
    'Fantasia': '幻想曲',
    'Dinosaur': '恐龙',
    'Atlantis: The Lost Empire': '亚特兰蒂斯',
    'Treasure Planet': '星银岛',
    'Brother Bear': '熊的传说',
    'Tangled': '魔发奇缘',
    'Wreck-It Ralph': '无敌破坏王',
    'Big Hero 6': '超能陆战队',
    'Encanto': '魔法满屋',
}

def translate_movie_name(english_name):
    base_name = english_name.split('(')[0].strip()
    if base_name in MOVIE_TRANSLATIONS:
        return MOVIE_TRANSLATIONS[base_name]
    words = base_name.split()
    if len(words) >= 2:
        return ''.join(words[:2])
    return base_name

def get_categories(genre_flags):
    """将类别标志转换为中文类别列表"""
    genres = []
    genre_names = ['Action', 'Adventure', 'Animation', "Children's", 'Comedy', 'Crime',
                   'Documentary', 'Drama', 'Fantasy', 'Film-Noir', 'Horror', 'Musical',
                   'Mystery', 'Romance', 'Sci-Fi', 'Thriller', 'War', 'Western', 'unknown']
    for i, flag in enumerate(genre_flags):
        if flag == '1':
            genres.append(CATEGORY_MAP.get(genre_names[i], genre_names[i]))
    return genres if genres else ['其他']

cursor = conn.cursor()

print("1. 清空items表...")
cursor.execute("DELETE FROM items")
conn.commit()
print(f"   items表已清空")

print("2. 清空ratings表...")
cursor.execute("DELETE FROM ratings")
conn.commit()
print(f"   ratings表已清空")

print("\n3. 读取并转换MovieLens 100K u.item数据...")
ml_100k_path = r"d:\app\ks\recommendtwo\docs\ml-100k\u.item"
items_data = []
with open(ml_100k_path, 'r', encoding='utf-8', errors='ignore') as f:
    for line in f:
        parts = line.strip().split('|')
        if len(parts) < 19:
            continue
        item_id = int(parts[0])
        movie_name_en = parts[1]
        release_date = parts[2] if parts[2] else '1995-01-01'
        imdb_url = parts[3] if len(parts) > 3 and parts[3] else ''
        genre_flags = parts[4:23]

        movie_name_cn = translate_movie_name(movie_name_en)
        categories = get_categories(genre_flags)
        main_category = categories[0]

        year = '1995'
        if '(' in movie_name_en and ')' in movie_name_en:
            try:
                year = movie_name_en.split('(')[1].replace(')', '').strip()
            except:
                pass

        description = f"一部{main_category}，讲述了一个引人入胜的故事。{movie_name_en}是一部{year}年上映的经典影片。"

        price = round(random.uniform(9.9, 99.9), 2)

        image_url = f"https://picsum.photos/seed/{item_id}/400/400"

        items_data.append((movie_name_cn, main_category, image_url, description, price))

print(f"   读取了 {len(items_data)} 条商品数据")

print("\n4. 插入商品数据到items表...")
insert_sql = "INSERT INTO items (name, category, image_url, description, price) VALUES (%s, %s, %s, %s, %s)"
cursor.executemany(insert_sql, items_data)
conn.commit()
print(f"   成功插入 {cursor.rowcount} 条商品数据")

print("\n5. 读取并导入MovieLens 100K ratings数据...")
ml_100k_ratings_path = r"d:\app\ks\recommendtwo\docs\ml-100k\u.data"
cursor.execute("SELECT MAX(id) FROM ratings")
max_id = cursor.fetchone()[0] or 0

cursor.execute("SET FOREIGN_KEY_CHECKS=0")

ratings_data = []
batch_size = 1000
total_ratings = 0

with open(ml_100k_ratings_path, 'r', encoding='utf-8', errors='ignore') as f:
    batch = []
    for line in f:
        parts = line.strip().split('\t')
        if len(parts) < 4:
            continue
        user_id = int(parts[0])
        item_id = int(parts[1])
        rating = float(parts[2])
        timestamp = int(parts[3])

        max_id += 1
        batch.append((max_id, user_id, item_id, rating, timestamp))

        if len(batch) >= batch_size:
            cursor.executemany(
                "INSERT INTO ratings (id, user_id, item_id, score, rated_at) VALUES (%s, %s, %s, %s, FROM_UNIXTIME(%s))",
                batch
            )
            conn.commit()
            total_ratings += len(batch)
            print(f"   已导入 {total_ratings} 条评分数据...")
            batch = []

    if batch:
        cursor.executemany(
            "INSERT INTO ratings (id, user_id, item_id, score, rated_at) VALUES (%s, %s, %s, %s, FROM_UNIXTIME(%s))",
            batch
        )
        conn.commit()
        total_ratings += len(batch)

print(f"   总共导入 {total_ratings} 条评分数据")

print("\n6. 验证数据...")
cursor.execute("SELECT COUNT(*) FROM items")
item_count = cursor.fetchone()[0]
print(f"   items表记录数: {item_count}")

cursor.execute("SELECT COUNT(*) FROM ratings")
rating_count = cursor.fetchone()[0]
print(f"   ratings表记录数: {rating_count}")

cursor.execute("SELECT COUNT(DISTINCT user_id) FROM ratings")
user_count = cursor.fetchone()[0]
print(f"   评分用户数: {user_count}")

cursor.execute("SELECT COUNT(DISTINCT item_id) FROM ratings")
rated_item_count = cursor.fetchone()[0]
print(f"   被评分商品数: {rated_item_count}")

print("\n7. 展示部分商品数据示例...")
cursor.execute("SELECT id, name, category, price, image_url FROM items LIMIT 5")
for row in cursor.fetchall():
    print(f"   ID: {row[0]}, 名称: {row[1]}, 类别: {row[2]}, 价格: {row[3]}, 图片: {row[4][:50]}...")

cursor.execute("SET FOREIGN_KEY_CHECKS=1")
cursor.close()
conn.close()

print("\n数据导入完成！")
