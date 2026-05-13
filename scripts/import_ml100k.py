#!/usr/bin/env python3
"""
导入MovieLens ml-100k数据集到推荐系统数据库
"""
import os
import argparse
import pymysql
from datetime import datetime

def parse_args():
    parser = argparse.ArgumentParser(description='导入ml-100k数据集到MySQL数据库')
    parser.add_argument('--host', default='localhost', help='数据库主机')
    parser.add_argument('--port', type=int, default=3306, help='数据库端口')
    parser.add_argument('--user', default='root', help='数据库用户名')
    parser.add_argument('--password', default='2004zyta', help='数据库密码')
    parser.add_argument('--database', default='recommend', help='数据库名称')
    parser.add_argument('--data-dir', default='docs/ml-100k', help='数据集目录')
    parser.add_argument('--clear-existing', action='store_true', help='是否清除现有数据')
    return parser.parse_args()

def connect_db(args):
    """连接数据库"""
    try:
        conn = pymysql.connect(
            host=args.host,
            port=args.port,
            user=args.user,
            password=args.password,
            database=args.database,
            charset='utf8mb4'
        )
        return conn
    except Exception as e:
        print(f"数据库连接失败: {e}")
        exit(1)

def clear_tables(conn):
    """清除现有数据"""
    try:
        with conn.cursor() as cursor:
            cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
            cursor.execute("TRUNCATE TABLE ratings")
            cursor.execute("TRUNCATE TABLE user_item_flags")
            cursor.execute("TRUNCATE TABLE items")
            cursor.execute("TRUNCATE TABLE users")
            cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
            conn.commit()
        print("已清除现有数据")
    except Exception as e:
        print(f"清除数据失败: {e}")
        conn.rollback()
        raise

def import_users(conn, data_dir):
    """导入用户数据"""
    users_file = os.path.join(data_dir, 'u.user')
    if not os.path.exists(users_file):
        print(f"用户文件不存在: {users_file}")
        return 0

    count = 0
    with open(users_file, 'r', encoding='latin-1') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split('|')
            if len(parts) >= 1:
                user_id = int(parts[0])
                username = f'user_{user_id}'
                with conn.cursor() as cursor:
                    sql = "INSERT INTO users (id, username) VALUES (%s, %s) ON DUPLICATE KEY UPDATE username = VALUES(username)"
                    cursor.execute(sql, (user_id, username))
                count += 1

    conn.commit()
    print(f"导入用户: {count}")
    return count

def import_movies(conn, data_dir):
    """导入电影数据"""
    movies_file = os.path.join(data_dir, 'u.item')
    if not os.path.exists(movies_file):
        print(f"电影文件不存在: {movies_file}")
        return 0

    count = 0
    with open(movies_file, 'r', encoding='latin-1') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split('|')
            if len(parts) >= 2:
                movie_id = int(parts[0])
                title = parts[1]
                # 处理年份信息
                if '(' in title and ')' in title:
                    title = title[:title.rfind('(')].strip()
                category = 'Unknown'
                # 检查是否有类别信息
                if len(parts) >= 25:
                    # 最后19个字段是类别
                    categories = []
                    for i in range(5, 24):
                        if i < len(parts) and parts[i] == '1':
                            categories.append(str(i-4))
                    if categories:
                        category = categories[0]
                
                with conn.cursor() as cursor:
                    sql = "INSERT INTO items (id, name, category) VALUES (%s, %s, %s) ON DUPLICATE KEY UPDATE name = VALUES(name), category = VALUES(category)"
                    cursor.execute(sql, (movie_id, title, category))
                count += 1

    conn.commit()
    print(f"导入电影: {count}")
    return count

def import_ratings(conn, data_dir):
    """导入评分数据"""
    ratings_file = os.path.join(data_dir, 'u.data')
    if not os.path.exists(ratings_file):
        print(f"评分文件不存在: {ratings_file}")
        return 0

    count = 0
    batch_size = 1000
    batch = []

    with open(ratings_file, 'r', encoding='latin-1') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split('\t')
            if len(parts) >= 4:
                user_id = int(parts[0])
                movie_id = int(parts[1])
                score = float(parts[2])
                timestamp = int(parts[3])
                rated_at = datetime.fromtimestamp(timestamp)
                batch.append((user_id, movie_id, score, rated_at))

                if len(batch) >= batch_size:
                    with conn.cursor() as cursor:
                        sql = "INSERT INTO ratings (user_id, item_id, score, rated_at) VALUES (%s, %s, %s, %s) ON DUPLICATE KEY UPDATE score = VALUES(score), rated_at = VALUES(rated_at)"
                        cursor.executemany(sql, batch)
                    count += len(batch)
                    batch = []

    # 处理剩余数据
    if batch:
        with conn.cursor() as cursor:
            sql = "INSERT INTO ratings (user_id, item_id, score, rated_at) VALUES (%s, %s, %s, %s) ON DUPLICATE KEY UPDATE score = VALUES(score), rated_at = VALUES(rated_at)"
            cursor.executemany(sql, batch)
        count += len(batch)

    conn.commit()
    print(f"导入评分: {count}")
    return count

def main():
    args = parse_args()
    conn = connect_db(args)

    try:
        if args.clear_existing:
            clear_tables(conn)

        print("开始导入ml-100k数据集...")
        start = datetime.now()

        user_count = import_users(conn, args.data_dir)
        movie_count = import_movies(conn, args.data_dir)
        rating_count = import_ratings(conn, args.data_dir)

        end = datetime.now()
        duration = end - start

        print(f"\n导入完成!")
        print(f"用户数: {user_count}")
        print(f"电影数: {movie_count}")
        print(f"评分数: {rating_count}")
        print(f"耗时: {duration}")

    finally:
        conn.close()

if __name__ == '__main__':
    main()