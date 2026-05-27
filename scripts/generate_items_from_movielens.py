# -*- coding: utf-8 -*-
"""
MovieLens 1M -> 电商商品数据转换脚本
读取 movies.dat 并生成 SQL 插入语句
"""

import random

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

EDITION_SUFFIXES = ['收藏版', '限定版', '精装版', '经典版', '纪念版', '豪华版']

DESCRIPTION_TEMPLATES = [
    '本商品属于{category}类影视作品，具有较高用户评分与观影热度，适合喜欢{genre_detail}题材的用户。',
    '经典{category}类作品，深受用户喜爱与好评，适合{genre_detail}爱好者收藏与观影。',
    '本商品为{category}题材优质内容，用户口碑良好，推荐喜欢{genre_detail}的用户购买。',
    '热门{category}类影视作品，具有广泛的用户基础与良好评价，适合{genre_detail}爱好者。',
]

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


def parse_genres(genre_str):
    genres = genre_str.split('|')
    return [g.strip() for g in genres if g.strip()]


def genres_to_chinese(genres):
    chinese = []
    for g in genres:
        if g in GENRE_MAP:
            chinese.append(GENRE_MAP[g])
    return '/'.join(chinese) if chinese else '其他'


def generate_price():
    base = random.randint(29, 399)
    decimal = random.choice([0, 9, 5])
    return f"{base}.{decimal}"


def generate_description(genres):
    chinese_genres = genres_to_chinese(genres)
    primary_genre = genres[0] if genres else 'Drama'
    genre_detail = GENRE_DETAILS.get(GENRE_MAP.get(primary_genre, 'Drama'), '优质影视内容')
    template = random.choice(DESCRIPTION_TEMPLATES)
    return template.format(category=chinese_genres, genre_detail=genre_detail)


def main():
    random.seed(42)
    
    movies_file = r'D:\app\ks\recommendtwo\docs\ml-1m\movies.dat'
    output_file = r'D:\app\ks\recommendtwo\sql\import_movielens_as_items.sql'
    
    sql_lines = []
    sql_lines.append('-- =====================================================')
    sql_lines.append('-- MovieLens 1M -> 电商商品数据转换脚本')
    sql_lines.append('-- 数据来源: MovieLens 1M (movies.dat)')
    sql_lines.append('-- 说明: 将电影数据包装为电商商品数据')
    sql_lines.append('-- =====================================================')
    sql_lines.append('')
    sql_lines.append('SET FOREIGN_KEY_CHECKS=0;')
    sql_lines.append('SET NAMES utf8mb4;')
    sql_lines.append('')
    sql_lines.append('TRUNCATE TABLE items;')
    sql_lines.append('')
    
    item_count = 0
    
    with open(movies_file, 'r', encoding='latin-1') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            
            parts = line.split('::')
            if len(parts) != 3:
                continue
            
            movie_id = int(parts[0])
            title = parts[1]
            genre_str = parts[2]
            
            genres = parse_genres(genre_str)
            category = genres_to_chinese(genres)
            price = generate_price()
            image_url = 'https://image.tmdb.org/t/p/w500/default_poster.jpg'
            description = generate_description(genres)
            
            edition = random.choice(EDITION_SUFFIXES)
            product_name = f"{title} {edition}"
            
            product_name_escaped = product_name.replace("'", "''")
            description_escaped = description.replace("'", "''")
            
            sql_line = f"INSERT INTO items (id, name, category, price, image_url, description) VALUES ({movie_id}, '{product_name_escaped}', '{category}', {price}, '{image_url}', '{description_escaped}');"
            sql_lines.append(sql_line)
            
            item_count += 1
    
    sql_lines.append('')
    sql_lines.append(f'ALTER TABLE items AUTO_INCREMENT = {item_count + 1000};')
    sql_lines.append('')
    sql_lines.append('SET FOREIGN_KEY_CHECKS=1;')
    sql_lines.append('')
    sql_lines.append('SELECT COUNT(*) AS total_items FROM items;')
    sql_lines.append('')
    sql_lines.append(f'-- 共生成 {item_count} 条商品数据')
    
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write('\n'.join(sql_lines))
    
    print(f"Success: {item_count} items generated")
    print(f"Saved to: {output_file}")


if __name__ == '__main__':
    main()
