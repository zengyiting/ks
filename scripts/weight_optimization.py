#!/usr/bin/env python3
"""
权重自动优化脚本
通过网格搜索自动调优混合推荐策略的权重参数
"""

import pandas as pd
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from collections import defaultdict
import json
import os

DATA_PATH = "docs/ml-100k/u.data"
OUTPUT_DIR = "reports/weight-optimization"

os.makedirs(OUTPUT_DIR, exist_ok=True)

print("=" * 60)
print("权重自动优化脚本")
print("=" * 60)

print("\n加载数据...")
columns = ['user_id', 'item_id', 'rating', 'timestamp']
df = pd.read_csv(DATA_PATH, sep='\t', names=columns, encoding='latin-1')

print("构建用户-物品矩阵...")
user_item_matrix = df.pivot(index='user_id', columns='item_id', values='rating').fillna(0)

print(f"矩阵大小: {user_item_matrix.shape}")

print("计算用户相似度...")
user_similarity = cosine_similarity(user_item_matrix)
user_similarity_df = pd.DataFrame(user_similarity,
                                  index=user_item_matrix.index,
                                  columns=user_item_matrix.index)

print("划分训练集和测试集...")
np.random.seed(42)
indices = np.random.permutation(len(df))
split_idx = int(len(df) * 0.8)
train_df = df.iloc[indices[:split_idx]]
test_df = df.iloc[indices[split_idx:]]

train_matrix = train_df.pivot(index='user_id', columns='item_id', values='rating').fillna(0)
train_similarity = cosine_similarity(train_matrix)
train_similarity_df = pd.DataFrame(train_similarity,
                                   index=train_matrix.index,
                                   columns=train_matrix.index)

test_user_items = defaultdict(set)
for _, row in test_df.iterrows():
    if row['rating'] >= 4:
        test_user_items[row['user_id']].add(row['item_id'])

print(f"测试用户数: {len(test_user_items)}")

def predict_rating_usercf(user_id, item_id, train_matrix, train_similarity_df, k=15):
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

def predict_rating_itemcf(user_id, item_id, train_matrix, k=15):
    if user_id not in train_matrix.index or item_id not in train_matrix.columns:
        return 0
    
    user_ratings = train_matrix.loc[user_id]
    rated_items = user_ratings[user_ratings > 0]
    
    if len(rated_items) == 0:
        return 0
    
    item_vector = train_matrix[item_id].values.reshape(1, -1)
    similarities = []
    for rated_item in rated_items.index:
        rated_vector = train_matrix[rated_item].values.reshape(1, -1)
        sim = cosine_similarity(item_vector, rated_vector)[0][0]
        similarities.append((rated_item, sim))
    
    similarities.sort(key=lambda x: x[1], reverse=True)
    top_similar = similarities[:k]
    
    if len(top_similar) == 0:
        return 0
    
    weighted_sum = sum(rated_items[item] * sim for item, sim in top_similar)
    sim_sum = sum(sim for _, sim in top_similar)
    
    return weighted_sum / sim_sum if sim_sum > 0 else 0

def hybrid_recommend(user_id, train_matrix, train_similarity_df, weights, N=10, k=15):
    if user_id not in train_matrix.index:
        return []
    
    items_not_rated = train_matrix.columns[train_matrix.loc[user_id] == 0]
    predictions = {}
    
    usercf_weight, itemcf_weight, popularity_weight = weights
    
    item_popularity = train_matrix.sum(axis=0)
    max_pop = item_popularity.max()
    
    for item in items_not_rated:
        usercf_score = predict_rating_usercf(user_id, item, train_matrix, train_similarity_df, k)
        itemcf_score = predict_rating_itemcf(user_id, item, train_matrix, k)
        pop_score = item_popularity[item] / max_pop if max_pop > 0 else 0
        
        hybrid_score = (usercf_weight * usercf_score + 
                       itemcf_weight * itemcf_score + 
                       popularity_weight * pop_score)
        
        if hybrid_score > 0:
            predictions[item] = hybrid_score
    
    top_n = sorted(predictions.items(), key=lambda x: x[1], reverse=True)[:N]
    return top_n

def evaluate_weights(weights, train_matrix, train_similarity_df, test_user_items, K=10):
    precision_sum = 0
    recall_sum = 0
    n_users = 0
    
    for user_id, relevant_items in test_user_items.items():
        if user_id not in train_matrix.index:
            continue
        if len(relevant_items) == 0:
            continue
        
        recommendations = hybrid_recommend(user_id, train_matrix, train_similarity_df, weights, N=K)
        recommended_items = set([item for item, _ in recommendations])
        
        hits = len(recommended_items & relevant_items)
        precision = hits / K
        recall = hits / len(relevant_items)
        
        precision_sum += precision
        recall_sum += recall
        n_users += 1
    
    return precision_sum / n_users if n_users > 0 else 0, recall_sum / n_users if n_users > 0 else 0

print("\n开始网格搜索权重优化...")
print("-" * 60)

weight_range = np.arange(0.1, 0.8, 0.1)

best_weights = None
best_precision = 0
best_recall = 0
results = []

total_combinations = len(weight_range) * len(weight_range) * len(weight_range)
current = 0

for w1 in weight_range:
    for w2 in weight_range:
        for w3 in weight_range:
            if abs(w1 + w2 + w3 - 1.0) > 0.01:
                continue
            
            current += 1
            weights = (w1, w2, w3)
            precision, recall = evaluate_weights(weights, train_matrix, train_similarity_df, test_user_items, K=10)
            
            results.append({
                'usercf_weight': w1,
                'itemcf_weight': w2,
                'popularity_weight': w3,
                'precision': precision,
                'recall': recall
            })
            
            if precision > best_precision:
                best_precision = precision
                best_recall = recall
                best_weights = weights
            
            print(f"进度: {current}/{total_combinations} | 权重: ({w1:.1f}, {w2:.1f}, {w3:.1f}) | "
                  f"Precision: {precision:.4f} | Recall: {recall:.4f}")

print("\n" + "=" * 60)
print("优化结果")
print("=" * 60)
print(f"\n最佳权重组合:")
print(f"  UserCF权重: {best_weights[0]:.2f}")
print(f"  ItemCF权重: {best_weights[1]:.2f}")
print(f"  Popularity权重: {best_weights[2]:.2f}")
print(f"\n最佳性能:")
print(f"  Precision@10: {best_precision:.4f}")
print(f"  Recall@10: {best_recall:.4f}")

results_df = pd.DataFrame(results)
results_df = results_df.sort_values('precision', ascending=False)

output_csv = os.path.join(OUTPUT_DIR, 'weight_optimization_results.csv')
results_df.to_csv(output_csv, index=False)
print(f"\n结果已保存到: {output_csv}")

output_json = os.path.join(OUTPUT_DIR, 'best_weights.json')
with open(output_json, 'w') as f:
    json.dump({
        'best_weights': {
            'usercf': best_weights[0],
            'itemcf': best_weights[1],
            'popularity': best_weights[2]
        },
        'best_performance': {
            'precision': best_precision,
            'recall': best_recall
        }
    }, f, indent=2)
print(f"最佳权重已保存到: {output_json}")

print("\nTop 10 权重组合:")
print(results_df.head(10).to_string())

print("\n" + "=" * 60)
print("权重优化完成!")
print("=" * 60)
