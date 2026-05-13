#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
评估 Java 推荐算法的 Python 脚本
通过 REST API 调用 Java 后端，在内存中计算评估指标
数据集：ml-100k
"""

import os
import random
import math
import json
import urllib.request
import urllib.parse

def load_movielens_100k(data_path, sample_ratio=0.05):
    """
    加载 ml-100k 数据集
    sample_ratio: 采样比例，默认0.05表示只使用1/20的数据加速评估
    """
    all_user_item = {}
    all_items = set()
    
    with open(data_path, 'r') as f:
        for line in f:
            parts = line.strip().split('\t')
            if len(parts) >= 3:
                user_id = int(parts[0])
                item_id = int(parts[1])
                rating = float(parts[2])
                if user_id not in all_user_item:
                    all_user_item[user_id] = {}
                all_user_item[user_id][item_id] = rating
                all_items.add(item_id)
    
    # 采样：只使用部分用户
    all_users = list(all_user_item.keys())
    random.shuffle(all_users)
    sample_size = max(1, int(len(all_users) * sample_ratio))
    sampled_users = set(all_users[:sample_size])
    
    user_item = {u: all_user_item[u] for u in sampled_users if u in all_user_item}
    
    print(f"数据集加载完成（采样 {sample_ratio*100:.1f}%）：{len(user_item)} 用户，{len(all_items)} 物品，{sum(len(ratings) for ratings in user_item.values())} 条评分")
    return user_item, all_items

def split_train_test(user_item, test_ratio=0.2, seed=42):
    """划分训练集和测试集"""
    random.seed(seed)
    train = {}
    test = {}
    
    for user_id, ratings in user_item.items():
        train[user_id] = {}
        test[user_id] = {}
        
        items = list(ratings.keys())
        random.shuffle(items)
        test_size = int(len(items) * test_ratio)
        
        for i, item_id in enumerate(items):
            if i < test_size:
                test[user_id][item_id] = ratings[item_id]
            else:
                train[user_id][item_id] = ratings[item_id]
    
    train_count = sum(len(ratings) for ratings in train.values())
    test_count = sum(len(ratings) for ratings in test.values())
    print(f"训练集：{train_count} 条评分，测试集：{test_count} 条评分")
    return train, test

def call_recommend_api(user_id, top_n=10, algo='hybrid'):
    """调用 Java 后端的推荐 API"""
    url = f"http://localhost:8080/api/recommendations/{user_id}?n={top_n}&algo={algo}"
    try:
        response = urllib.request.urlopen(url, timeout=30)
        data = json.loads(response.read().decode('utf-8'))
        return [item['itemId'] for item in data]
    except Exception as e:
        print(f"调用API失败 用户{user_id}: {e}")
        return []

def precision_at_k(recommended, relevant, k):
    """计算 Precision@K"""
    recommended_k = recommended[:k]
    if len(recommended_k) == 0:
        return 0.0
    hits = len(set(recommended_k) & set(relevant))
    return hits / len(recommended_k)

def recall_at_k(recommended, relevant, k):
    """计算 Recall@K"""
    if len(relevant) == 0:
        return 0.0
    recommended_k = recommended[:k]
    hits = len(set(recommended_k) & set(relevant))
    return hits / len(relevant)

def ndcg_at_k(recommended, relevant, k):
    """计算 NDCG@K"""
    recommended_k = recommended[:k]
    if len(recommended_k) == 0:
        return 0.0
    
    # DCG
    dcg = 0.0
    for i, item_id in enumerate(recommended_k):
        if item_id in relevant:
            dcg += 1.0 / math.log2(i + 2)  # i从0开始，所以+2
    
    # IDCG (理想DCG)
    idcg = 0.0
    ideal_size = min(k, len(relevant))
    for i in range(ideal_size):
        idcg += 1.0 / math.log2(i + 2)
    
    return dcg / idcg if idcg > 0 else 0.0

def evaluate_algorithm(user_item_train, user_item_test, all_items, algo='hybrid', top_n=10, relevance_threshold=4.0):
    """评估指定算法"""
    total_precision = 0.0
    total_recall = 0.0
    total_ndcg = 0.0
    all_recommended = set()
    evaluable_users = 0
    
    print(f"\n正在评估 {algo.upper()} 算法...")
    
    for user_id in user_item_test.keys():
        # 获取用户在测试集中的相关物品（评分>=阈值）
        relevant_items = [item_id for item_id, rating in user_item_test[user_id].items() if rating >= relevance_threshold]
        
        if len(relevant_items) == 0:
            continue
        
        # 调用推荐API
        recommended = call_recommend_api(user_id, top_n, algo)
        
        if len(recommended) == 0:
            continue
        
        # 计算指标
        total_precision += precision_at_k(recommended, relevant_items, top_n)
        total_recall += recall_at_k(recommended, relevant_items, top_n)
        total_ndcg += ndcg_at_k(recommended, relevant_items, top_n)
        
        # 更新覆盖率
        all_recommended.update(recommended)
        evaluable_users += 1
    
    # 计算平均值
    avg_precision = total_precision / evaluable_users if evaluable_users > 0 else 0.0
    avg_recall = total_recall / evaluable_users if evaluable_users > 0 else 0.0
    avg_ndcg = total_ndcg / evaluable_users if evaluable_users > 0 else 0.0
    coverage = len(all_recommended) / len(all_items) if len(all_items) > 0 else 0.0
    
    print(f"评估完成！可评估用户数: {evaluable_users}")
    print(f"Precision@{top_n}: {avg_precision:.4f}")
    print(f"Recall@{top_n}: {avg_recall:.4f}")
    print(f"NDCG@{top_n}: {avg_ndcg:.4f}")
    print(f"Coverage: {coverage:.4f}")
    
    return {
        'algorithm': algo,
        'precision_at_k': avg_precision,
        'recall_at_k': avg_recall,
        'ndcg_at_k': avg_ndcg,
        'coverage': coverage,
        'evaluable_users': evaluable_users
    }

def main():
    # 数据路径
    data_path = os.path.join(os.path.dirname(__file__), '..', 'docs', 'ml-100k', 'u.data')
    if not os.path.exists(data_path):
        print(f"错误：找不到数据文件 {data_path}")
        return
    
    # 加载数据
    user_item, all_items = load_movielens_100k(data_path)
    
    # 划分训练集和测试集
    train, test = split_train_test(user_item, test_ratio=0.2)
    
    # 评估各算法
    algorithms = ['user', 'item', 'hybrid']
    results = []
    
    for algo in algorithms:
        result = evaluate_algorithm(train, test, all_items, algo, top_n=10, relevance_threshold=4.0)
        results.append(result)
    
    # 输出汇总结果
    print("\n" + "="*60)
    print("算法评估结果汇总")
    print("="*60)
    print(f"{'算法':<12} {'Precision@10':<12} {'Recall@10':<10} {'NDCG@10':<10} {'Coverage':<10}")
    print("-"*60)
    for result in results:
        print(f"{result['algorithm'].upper():<12} "
              f"{result['precision_at_k']:.4f}          "
              f"{result['recall_at_k']:.4f}        "
              f"{result['ndcg_at_k']:.4f}        "
              f"{result['coverage']:.4f}")
    print("="*60)
    
    # 保存结果到文件
    output_file = os.path.join(os.path.dirname(__file__), '..', 'reports', 'offline-eval', 'evaluation_results.json')
    os.makedirs(os.path.dirname(output_file), exist_ok=True)
    with open(output_file, 'w') as f:
        json.dump(results, f, indent=2)
    print(f"\n结果已保存到: {output_file}")

if __name__ == '__main__':
    main()
