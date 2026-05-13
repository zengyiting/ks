#!/usr/bin/env python3
"""
使用sklearn余弦相似度复现论文算法
"""

import pandas as pd
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from collections import defaultdict

TRAIN_PATH = "docs/ml-100k/ua.base"
TEST_PATH = "docs/ml-100k/ua.test"

def load_data():
    columns = ['user_id', 'item_id', 'rating', 'timestamp']
    train_df = pd.read_csv(TRAIN_PATH, sep='\t', names=columns, encoding='latin-1')
    test_df = pd.read_csv(TEST_PATH, sep='\t', names=columns, encoding='latin-1')
    return train_df, test_df

def build_matrix(train_df, test_df):
    """构建用户-物品矩阵"""
    # 合并训练集和测试集的所有数据来确定矩阵的物品范围
    all_df = pd.concat([train_df[['user_id', 'item_id', 'rating']],
                        test_df[['user_id', 'item_id', 'rating']]])

    # 构建用户-物品矩阵 (训练集)
    train_matrix = train_df.pivot(index='user_id', columns='item_id', values='rating').fillna(0)

    # 测试集中有评分的物品作为ground truth
    test_user_items = defaultdict(set)
    for _, row in test_df.iterrows():
        if row['rating'] >= 4:  # rating>=4为相关
            test_user_items[row['user_id']].add(row['item_id'])

    return train_matrix, test_user_items

def compute_user_similarity(train_matrix):
    """计算用户余弦相似度"""
    user_similarity = cosine_similarity(train_matrix)
    user_similarity_df = pd.DataFrame(user_similarity,
                                      index=train_matrix.index,
                                      columns=train_matrix.index)
    return user_similarity_df

def predict_rating(user_id, item_id, train_matrix, user_similarity_df, k=15):
    """预测用户对物品的评分"""
    if user_id not in train_matrix.index:
        return 0
    if item_id not in train_matrix.columns:
        return 0

    # 找出对目标用户最相似的k个用户(评分过该物品)
    sim_users = user_similarity_df.loc[user_id].drop(user_id)
    ratings = train_matrix[item_id]

    # 只考虑对该物品有评分的用户
    mask = ratings > 0
    valid_users = sim_users[mask].sort_values(ascending=False)[:k]

    if len(valid_users) == 0:
        return 0

    valid_ratings = ratings[valid_users.index]

    # 加权平均
    pred = np.dot(valid_users.values, valid_ratings.values) / valid_users.sum()
    return pred

def recommend_movies(user_id, train_matrix, user_similarity_df, N=10, k=15):
    """为用户生成Top-N推荐"""
    if user_id not in train_matrix.index:
        return []

    # 找出用户未评分的物品
    items_not_rated = train_matrix.columns[train_matrix.loc[user_id] == 0]

    # 预测所有未评分物品的评分
    predicted_ratings = {}
    for item in items_not_rated:
        pred = predict_rating(user_id, item, train_matrix, user_similarity_df, k)
        if pred > 0:
            predicted_ratings[item] = pred

    # 返回Top-N
    top_n = sorted(predicted_ratings.items(), key=lambda x: x[1], reverse=True)[:N]
    return top_n

def evaluate(train_matrix, user_similarity_df, test_user_items, K):
    """评估精确率和召回率"""
    precision_sum = 0
    recall_sum = 0
    n_users = 0

    for user_id, relevant_items in test_user_items.items():
        if user_id not in train_matrix.index:
            continue
        if len(relevant_items) == 0:
            continue

        # 获取Top-K推荐
        recommendations = recommend_movies(user_id, train_matrix, user_similarity_df, N=K, k=15)
        recommended_items = set([item for item, _ in recommendations])

        # 计算命中
        hits = len(recommended_items & relevant_items)

        precision = hits / K if K > 0 else 0
        recall = hits / len(relevant_items) if len(relevant_items) > 0 else 0

        precision_sum += precision
        recall_sum += recall
        n_users += 1

    return precision_sum / n_users if n_users > 0 else 0, recall_sum / n_users if n_users > 0 else 0

def main():
    print("="*60)
    print("使用sklearn余弦相似度复现")
    print("="*60)

    train_df, test_df = load_data()
    print(f"训练集: {len(train_df)}条")
    print(f"测试集: {len(test_df)}条")

    train_matrix, test_user_items = build_matrix(train_df, test_df)
    print(f"用户数: {len(train_matrix)}")
    print(f"物品数: {len(train_matrix.columns)}")
    print(f"测试用户数: {len(test_user_items)}")

    # 计算相似度
    print("\n计算用户相似度矩阵...")
    user_similarity_df = compute_user_similarity(train_matrix)
    print(f"相似度矩阵大小: {user_similarity_df.shape}")

    # 评估
    print("\n" + "="*60)
    print("评估结果 (K=推荐数量, k=15近邻数)")
    print("="*60)
    print(f"\n{'K':<6} {'Precision':<12} {'Recall':<12}")
    print("-"*35)

    for K in [5, 10, 15, 20]:
        p, r = evaluate(train_matrix, user_similarity_df, test_user_items, K)
        print(f"{K:<6} {p:<12.4f} {r:<12.4f}")

    print("\n" + "="*60)
    print("对比: 论文K=10时精确率约0.25")
    print("="*60)

if __name__ == "__main__":
    main()
