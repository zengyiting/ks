#!/usr/bin/env python3
"""
分析论文中的评估方法差异
"""

import numpy as np
import pandas as pd
from collections import defaultdict
import math

DATA_PATH = "docs/ml-100k/u.data"

def load_data():
    columns = ['user_id', 'item_id', 'rating', 'timestamp']
    df = pd.read_csv(DATA_PATH, sep='\t', names=columns, encoding='latin-1')
    return df

def analyze_dataset():
    df = load_data()

    print("="*60)
    print("MovieLens-100K 数据集分析")
    print("="*60)

    user_counts = df.groupby('user_id').size()
    item_counts = df.groupby('item_id').size()

    print(f"总评分数: {len(df)}")
    print(f"用户数: {df['user_id'].nunique()}")
    print(f"物品数: {df['item_id'].nunique()}")
    print(f"平均每个用户评分: {user_counts.mean():.2f}")
    print(f"平均每个物品被评分: {item_counts.mean():.2f}")
    print(f"评分分布:\n{df['rating'].value_counts().sort_index()}")

    # 分析8:2划分后的数据
    np.random.seed(42)
    indices = np.random.permutation(len(df))
    split_idx = int(len(df) * 0.8)
    train_df = df.iloc[indices[:split_idx]]
    test_df = df.iloc[indices[split_idx:]]

    print(f"\n8:2划分后:")
    print(f"训练集: {len(train_df)}条")
    print(f"测试集: {len(test_df)}条")

    # 分析测试集中每个用户的评分数量
    test_user_items = defaultdict(list)
    for _, row in test_df.iterrows():
        test_user_items[row['user_id']].append(row['rating'])

    test_user_relevant = defaultdict(list)
    for user_id, ratings in test_user_items.items():
        relevant = [r for r in ratings if r >= 4]
        test_user_relevant[user_id] = relevant

    n_relevant_per_user = [len(v) for v in test_user_relevant.values()]

    print(f"\n测试集中每个用户的评分数量:")
    print(f"  总评分物品数: {sum(len(v) for v in test_user_items.values())}")
    print(f"  相关物品数(rating>=4): {sum(len(v) for v in test_user_relevant.values())}")
    print(f"  用户平均评分物品数: {np.mean([len(v) for v in test_user_items.values()]):.2f}")
    print(f"  用户平均相关物品数: {np.mean(n_relevant_per_user):.2f}")
    print(f"  最大相关物品数: {max(n_relevant_per_user)}")
    print(f"  0相关物品的用户数: {sum(1 for v in test_user_relevant.values() if len(v) == 0)}")

    print("\n" + "="*60)
    print("关键发现")
    print("="*60)
    print("""
如果测试集中用户平均只有约2个相关物品(rating>=4)，
那么当推荐Top-10时：
  - Precision = Hits / 10
  - Recall = Hits / 2

即使全部命中（Hits=2）：
  - Precision = 2/10 = 0.20
  - Recall = 2/2 = 1.00

论文中K=10时精确率约0.25，意味着平均每个用户命中2.5个，
这要求测试集中每个用户平均有超过2.5个相关物品。

这说明论文可能使用了不同的划分方法或相关性定义。
""")

if __name__ == "__main__":
    analyze_dataset()
