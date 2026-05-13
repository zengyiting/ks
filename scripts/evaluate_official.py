#!/usr/bin/env python3
"""
使用MovieLens-100K官方划分(ua.base/ua.test)复现论文结果
"""

import numpy as np
import pandas as pd
from collections import defaultdict
import math

TRAIN_PATH = "docs/ml-100k/ua.base"
TEST_PATH = "docs/ml-100k/ua.test"

def load_official_split():
    """加载官方划分"""
    columns = ['user_id', 'item_id', 'rating', 'timestamp']

    train_df = pd.read_csv(TRAIN_PATH, sep='\t', names=columns, encoding='latin-1')
    test_df = pd.read_csv(TEST_PATH, sep='\t', names=columns, encoding='latin-1')

    train_user_items = defaultdict(dict)
    for _, row in train_df.iterrows():
        train_user_items[row['user_id']][row['item_id']] = row['rating']

    # rating>=4为相关物品
    test_user_items = defaultdict(dict)
    for _, row in test_df.iterrows():
        if row['rating'] >= 4:
            test_user_items[row['user_id']][row['item_id']] = row['rating']

    return train_user_items, test_user_items, train_df, test_df

def pearson_similarity(ratings1, ratings2):
    common = set(ratings1.keys()) & set(ratings2.keys())
    if len(common) < 2:
        return 0
    vec1 = [ratings1[item] for item in common]
    vec2 = [ratings2[item] for item in common]
    mean1, mean2 = sum(vec1)/len(vec1), sum(vec2)/len(vec2)
    centered1 = [r - mean1 for r in vec1]
    centered2 = [r - mean2 for r in vec2]
    dot = sum(a*b for a,b in zip(centered1, centered2))
    norm1 = math.sqrt(sum(a*a for a in centered1))
    norm2 = math.sqrt(sum(b*b for b in centered2))
    if norm1 == 0 or norm2 == 0:
        return 0
    return dot / (norm1 * norm2)

class UserBasedCF:
    def __init__(self, N=15):
        self.N = N

    def fit(self, user_items):
        self.user_items = user_items
        users = list(user_items.keys())
        print(f"[UserCF] 用户数: {len(users)}")

        self.user_similarity = {u: {} for u in users}

        for i, u in enumerate(users):
            for j, v in enumerate(users):
                if i >= j:
                    continue
                common = set(user_items[u].keys()) & set(user_items[v].keys())
                if len(common) >= 2:
                    sim = pearson_similarity(user_items[u], user_items[v])
                    if sim > 0:
                        self.user_similarity[u][v] = sim
                        self.user_similarity[v][u] = sim

    def recommend(self, user_id):
        if user_id not in self.user_items:
            return []
        neighbors = sorted(self.user_similarity.get(user_id, {}).items(),
                         key=lambda x: x[1], reverse=True)[:self.N]
        item_scores = defaultdict(float)
        item_sim_sums = defaultdict(float)
        for neighbor_id, sim in neighbors:
            for item_id, rating in self.user_items[neighbor_id].items():
                if item_id not in self.user_items[user_id]:
                    item_scores[item_id] += sim * rating
                    item_sim_sums[item_id] += abs(sim)
        recommendations = []
        for item_id, score in item_scores.items():
            if item_sim_sums[item_id] > 0:
                recommendations.append((item_id, score / item_sim_sums[item_id]))
        recommendations.sort(key=lambda x: x[1], reverse=True)
        return recommendations

def evaluate(recommender, train_user_items, test_user_items, K):
    precision_sum = 0
    recall_sum = 0
    n_users = 0

    for user_id in test_user_items.keys():
        if user_id not in train_user_items:
            continue
        relevant_items = set(test_user_items[user_id].keys())
        if len(relevant_items) == 0:
            continue

        recommendations = recommender.recommend(user_id)[:K]
        recommended_items = set([item_id for item_id, _ in recommendations])

        hits = len(recommended_items & relevant_items)
        precision = hits / K if K > 0 else 0
        recall = hits / len(relevant_items) if len(relevant_items) > 0 else 0

        precision_sum += precision
        recall_sum += recall
        n_users += 1

    return precision_sum/n_users if n_users > 0 else 0, recall_sum/n_users if n_users > 0 else 0

def main():
    print("="*60)
    print("使用官方划分 ua.base/ua.test 评估")
    print("="*60)

    train_user_items, test_user_items, train_df, test_df = load_official_split()

    print(f"训练集: {len(train_df)}条")
    print(f"测试集: {len(test_df)}条")
    print(f"测试用户数: {len(test_user_items)}")
    print(f"测试集相关物品(rating>=4): {sum(len(v) for v in test_user_items.values())}个")
    print(f"平均每用户相关物品: {sum(len(v) for v in test_user_items.values())/len(test_user_items):.2f}")

    user_cf = UserBasedCF(N=15)
    user_cf.fit(train_user_items)

    print("\n" + "="*60)
    print("评估结果")
    print("="*60)
    print(f"\n{'K':<6} {'Precision':<12} {'Recall':<12}")
    print("-"*35)
    for K in [5, 10, 15, 20]:
        p, r = evaluate(user_cf, train_user_items, test_user_items, K)
        print(f"{K:<6} {p:<12.4f} {r:<12.4f}")

    print("\n" + "="*60)
    print("对比论文 (K=10时精确率~0.25)")
    print("="*60)

if __name__ == "__main__":
    main()
