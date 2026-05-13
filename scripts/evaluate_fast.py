#!/usr/bin/env python3
"""
简化版评估 - 只对测试集物品预测
"""

import pandas as pd
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from collections import defaultdict
import time

DATA_PATH = "docs/ml-100k/u.data"

print("加载数据...")
columns = ['user_id', 'item_id', 'rating', 'timestamp']
df = pd.read_csv(DATA_PATH, sep='\t', names=columns, encoding='latin-1')

# 8:2划分
np.random.seed(42)
indices = np.random.permutation(len(df))
split_idx = int(len(df) * 0.8)
train_df = df.iloc[indices[:split_idx]]
test_df = df.iloc[indices[split_idx:]]

print("构建训练集矩阵...")
train_matrix = train_df.pivot(index='user_id', columns='item_id', values='rating').fillna(0)
print(f"训练集矩阵: {train_matrix.shape}")

print("计算用户相似度...")
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
print(f"测试集相关物品总数: {sum(len(v) for v in test_user_items.values())}")

def predict_rating(user_id, item_id, train_matrix, train_similarity_df, k=15):
    if user_id not in train_matrix.index or item_id not in train_matrix.columns:
        return 0

    sim_users = train_similarity_df.loc[user_id].drop(user_id)
    ratings = train_matrix.loc[:, item_id]

    mask = ratings > 0
    valid_users = sim_users[mask].sort_values(ascending=False)[:k]

    if len(valid_users) == 0 or valid_users.sum() == 0:
        return 0

    valid_ratings = ratings[valid_users.index]
    pred = np.dot(valid_users.values, valid_ratings.values) / valid_users.sum()
    return pred

def evaluate_at_k(test_user_items, train_matrix, train_similarity_df, K, k=15):
    precision_sum = 0
    recall_sum = 0
    n_users = 0

    for user_id, relevant_items in test_user_items.items():
        if user_id not in train_matrix.index:
            continue
        if len(relevant_items) == 0:
            continue

        # 只预测测试集中的物品
        candidate_items = relevant_items | set(train_matrix.columns[train_matrix.loc[user_id] == 0])

        # 预测评分
        predicted = []
        for item in candidate_items:
            if item not in train_matrix.columns:
                continue
            if train_matrix.loc[user_id, item] > 0:  # 跳过训练集中已评分的
                continue
            pred = predict_rating(user_id, item, train_matrix, train_similarity_df, k)
            if pred > 0:
                predicted.append((item, pred))

        # 取Top-K
        predicted.sort(key=lambda x: x[1], reverse=True)
        top_k = set([item for item, _ in predicted[:K]])

        hits = len(top_k & relevant_items)
        precision = hits / K if K > 0 else 0
        recall = hits / len(relevant_items) if len(relevant_items) > 0 else 0

        precision_sum += precision
        recall_sum += recall
        n_users += 1

    return precision_sum / n_users if n_users > 0 else 0, recall_sum / n_users if n_users > 0 else 0

print("\n开始评估...")
start = time.time()

print("\n" + "="*60)
print("评估结果 (K=推荐数量, k=15近邻数)")
print("="*60)
print(f"\n{'K':<6} {'Precision':<12} {'Recall':<12}")
print("-"*35)

for K in [5, 10, 15, 20]:
    p, r = evaluate_at_k(test_user_items, train_matrix, train_similarity_df, K, k=15)
    print(f"{K:<6} {p:<12.4f} {r:<12.4f}")

print(f"\n耗时: {time.time()-start:.2f}秒")
print("\n" + "="*60)
print("对比: 论文K=10时精确率约0.25")
print("="*60)
