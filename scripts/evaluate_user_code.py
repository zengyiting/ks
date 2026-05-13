#!/usr/bin/env python3
"""
使用sklearn优化版本 - 参考用户提供的伪代码
"""

import pandas as pd
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from collections import defaultdict

DATA_PATH = "docs/ml-100k/u.data"

print("加载数据...")
columns = ['user_id', 'item_id', 'rating', 'timestamp']
df = pd.read_csv(DATA_PATH, sep='\t', names=columns, encoding='latin-1')

print("构建用户-物品矩阵...")
user_item_matrix = df.pivot(index='user_id', columns='item_id', values='rating').fillna(0)

print(f"矩阵大小: {user_item_matrix.shape}")

# 计算用户余弦相似度
print("计算用户相似度...")
user_similarity = cosine_similarity(user_item_matrix)
user_similarity_df = pd.DataFrame(user_similarity,
                                  index=user_item_matrix.index,
                                  columns=user_item_matrix.index)

print("相似度矩阵计算完成!")

# 预测评分函数
def predict_rating(user_id, item_id, user_item_matrix, user_similarity_df, k=15):
    if user_id not in user_item_matrix.index or item_id not in user_item_matrix.columns:
        return 0

    sim_users = user_similarity_df.loc[user_id].drop(user_id)
    ratings = user_item_matrix.loc[:, item_id]

    mask = ratings > 0
    valid_users = sim_users[mask].sort_values(ascending=False)[:k]

    if len(valid_users) == 0 or valid_users.sum() == 0:
        return 0

    valid_ratings = ratings[valid_users.index]
    pred = np.dot(valid_users.values, valid_ratings.values) / valid_users.sum()
    return pred

# Top-N推荐函数
def recommend_movies(user_id, user_item_matrix, user_similarity_df, N=10, k=15):
    if user_id not in user_item_matrix.index:
        return []

    items_not_rated = user_item_matrix.columns[user_item_matrix.loc[user_id] == 0]
    predicted_ratings = {}

    for item in items_not_rated:
        pred = predict_rating(user_id, item, user_item_matrix, user_similarity_df, k)
        if pred > 0:
            predicted_ratings[item] = pred

    top_n = sorted(predicted_ratings.items(), key=lambda x: x[1], reverse=True)[:N]
    return top_n

# 评估
print("\n开始评估...")

# 8:2划分
np.random.seed(42)
indices = np.random.permutation(len(df))
split_idx = int(len(df) * 0.8)
train_df = df.iloc[indices[:split_idx]]
test_df = df.iloc[indices[split_idx:]]

# 训练集矩阵
train_matrix = train_df.pivot(index='user_id', columns='item_id', values='rating').fillna(0)

# 重新计算训练集的相似度
print("重新计算训练集相似度...")
train_similarity = cosine_similarity(train_matrix)
train_similarity_df = pd.DataFrame(train_similarity,
                                    index=train_matrix.index,
                                    columns=train_matrix.index)

# 测试集ground truth (rating>=4为相关)
test_user_items = defaultdict(set)
for _, row in test_df.iterrows():
    if row['rating'] >= 4:
        test_user_items[row['user_id']].add(row['item_id'])

print(f"测试用户数: {len(test_user_items)}")
print(f"平均每用户相关物品: {sum(len(v) for v in test_user_items.values())/len(test_user_items):.2f}")

# 评估函数
def evaluate(train_matrix, train_similarity_df, test_user_items, K, k=15):
    precision_sum = 0
    recall_sum = 0
    n_users = 0

    test_users = list(test_user_items.keys())

    for idx, user_id in enumerate(test_users):
        if idx % 100 == 0:
            print(f"  评估进度: {idx}/{len(test_users)}")

        if user_id not in train_matrix.index:
            continue

        relevant_items = test_user_items[user_id]
        if len(relevant_items) == 0:
            continue

        # 获取Top-K推荐
        recommendations = recommend_movies(user_id, train_matrix, train_similarity_df, N=K, k=k)
        recommended_items = set([item for item, _ in recommendations])

        hits = len(recommended_items & relevant_items)
        precision = hits / K
        recall = hits / len(relevant_items)

        precision_sum += precision
        recall_sum += recall
        n_users += 1

    return precision_sum / n_users, recall_sum / n_users

print("\n" + "="*60)
print("评估结果 (K=推荐数量, k=15近邻数)")
print("="*60)
print(f"\n{'K':<6} {'Precision':<12} {'Recall':<12}")
print("-"*35)

for K in [5, 10, 15, 20]:
    p, r = evaluate(train_matrix, train_similarity_df, test_user_items, K, k=15)
    print(f"{K:<6} {p:<12.4f} {r:<12.4f}")

print("\n" + "="*60)
print("对比: 论文K=10时精确率约0.25")
print("="*60)
