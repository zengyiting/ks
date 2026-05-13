#!/usr/bin/env python3
"""
快速实验脚本 - 测试用户建议的优化方案
"""

import os
import math
from collections import defaultdict
from tqdm import tqdm

# ==================== 数据加载 ====================

def load_movielens_ratings(filepath):
    """加载MovieLens评分数据"""
    user_ratings = defaultdict(dict)
    item_ratings = defaultdict(dict)
    
    with open(filepath, 'r') as f:
        for line in f:
            parts = line.strip().split('\t')
            if len(parts) >= 4:
                user_id = int(parts[0])
                item_id = int(parts[1])
                rating = float(parts[2])
                user_ratings[user_id][item_id] = rating
                item_ratings[item_id][user_id] = rating
    
    return user_ratings, item_ratings

def train_test_split(user_ratings, test_ratio=0.2, seed=42):
    """分割训练集和测试集"""
    import random
    random.seed(seed)
    
    train = defaultdict(dict)
    test = defaultdict(dict)
    
    for user_id, ratings in user_ratings.items():
        items = list(ratings.keys())
        random.shuffle(items)
        split_idx = int(len(items) * (1 - test_ratio))
        
        for item_id in items[:split_idx]:
            train[user_id][item_id] = ratings[item_id]
        for item_id in items[split_idx:]:
            test[user_id][item_id] = ratings[item_id]
    
    return train, test

# ==================== 相似度计算 ====================

def pearson_similarity(ratings1, ratings2, min_overlap=2):
    """Pearson相关系数"""
    common_items = set(ratings1.keys()) & set(ratings2.keys())
    if len(common_items) < min_overlap:
        return 0.0
    
    sum1 = sum(ratings1[item] for item in common_items)
    sum2 = sum(ratings2[item] for item in common_items)
    sum1_sq = sum(ratings1[item]**2 for item in common_items)
    sum2_sq = sum(ratings2[item]**2 for item in common_items)
    sum_prod = sum(ratings1[item] * ratings2[item] for item in common_items)
    n = len(common_items)
    
    num = sum_prod - (sum1 * sum2 / n)
    den = math.sqrt((sum1_sq - sum1**2 / n) * (sum2_sq - sum2**2 / n))
    
    if den == 0:
        return 0.0
    
    return num / den

def adjusted_cosine(ratings1, ratings2, global_mean=3.5, min_overlap=2):
    """调整余弦相似度"""
    common_items = set(ratings1.keys()) & set(ratings2.keys())
    if len(common_items) < min_overlap:
        return 0.0
    
    dot = sum((ratings1[item] - global_mean) * (ratings2[item] - global_mean) for item in common_items)
    norm1 = math.sqrt(sum((r - global_mean)**2 for r in ratings1.values()))
    norm2 = math.sqrt(sum((r - global_mean)**2 for r in ratings2.values()))
    
    if norm1 == 0 or norm2 == 0:
        return 0.0
    
    return dot / (norm1 * norm2)

def apply_shrinkage(sim, overlap, shrinkage_factor=10):
    """应用shrinkage"""
    return sim * overlap / (overlap + shrinkage_factor)

# ==================== UserCF ====================

class UserCF:
    def __init__(self, user_ratings, item_ratings, config):
        self.user_ratings = user_ratings
        self.item_ratings = item_ratings
        self.config = config
        self.user_means = {u: sum(rs.values())/len(rs) for u, rs in user_ratings.items()}
    
    def recommend(self, user_id, top_n=10):
        """生成推荐"""
        target_ratings = self.user_ratings.get(user_id, {})
        if not target_ratings:
            return []
        
        target_mean = self.user_means.get(user_id, 3.5)
        
        # 找相似用户
        neighbors = []
        for other_id, other_ratings in self.user_ratings.items():
            if other_id == user_id:
                continue
            
            overlap = len(set(target_ratings.keys()) & set(other_ratings.keys()))
            if overlap < self.config['min_overlap']:
                continue
            
            sim = pearson_similarity(target_ratings, other_ratings, self.config['min_overlap'])
            
            if self.config['shrinkage']:
                sim = apply_shrinkage(sim, overlap, self.config['shrinkage_factor'])
            
            if sim > self.config['min_similarity']:
                neighbors.append((sim, other_id))
        
        neighbors.sort(reverse=True, key=lambda x: x[0])
        top_neighbors = neighbors[:self.config['neighbor_count']]
        
        # 预测评分 - 根据配置选择模式
        predictions = {}
        sim_sums = {}
        
        for sim, other_id in top_neighbors:
            other_ratings = self.user_ratings[other_id]
            other_mean = self.user_means.get(other_id, 3.5)
            
            # 用户建议：只遍历邻居的高分物品
            for item_id, rating in other_ratings.items():
                if item_id in target_ratings:
                    continue
                
                # 只考虑高分物品
                if self.config['high_rating_only'] and rating < self.config['rating_threshold']:
                    continue
                
                if self.config['use_deviation']:
                    # 传统评分预测模式
                    deviation = rating - other_mean
                    predictions[item_id] = predictions.get(item_id, 0) + sim * deviation
                else:
                    # 用户建议的Ranking模式
                    predictions[item_id] = predictions.get(item_id, 0) + sim * rating
                sim_sums[item_id] = sim_sums.get(item_id, 0) + abs(sim)
        
        # 计算最终得分
        results = []
        for item_id, total in predictions.items():
            if sim_sums[item_id] > 0:
                if self.config['use_baseline']:
                    score = target_mean + total / sim_sums[item_id]
                else:
                    score = total / sim_sums[item_id]
                results.append((item_id, score))
        
        results.sort(reverse=True, key=lambda x: x[1])
        return [item_id for item_id, score in results[:top_n]]

# ==================== ItemCF ====================

class ItemCF:
    def __init__(self, user_ratings, item_ratings, config):
        self.user_ratings = user_ratings
        self.item_ratings = item_ratings
        self.config = config
        self.similarity_cache = {}
    
    def compute_similarity(self, i1, i2):
        """计算物品相似度"""
        if (i1, i2) in self.similarity_cache:
            return self.similarity_cache[(i1, i2)]
        
        r1 = self.item_ratings[i1]
        r2 = self.item_ratings[i2]
        overlap = len(set(r1.keys()) & set(r2.keys()))
        
        if overlap < self.config['min_overlap']:
            result = 0.0
        else:
            sim = adjusted_cosine(r1, r2, min_overlap=self.config['min_overlap'])
            result = apply_shrinkage(sim, overlap, self.config['shrinkage_factor'])
        
        self.similarity_cache[(i1, i2)] = result
        self.similarity_cache[(i2, i1)] = result
        return result
    
    def recommend(self, user_id, top_n=10):
        """生成推荐"""
        target_ratings = self.user_ratings.get(user_id, {})
        if not target_ratings:
            return []
        
        # 获取用户已评分的物品（只考虑高分）
        rated_items = [(item, rating) for item, rating in target_ratings.items()]
        if self.config['high_rating_only']:
            rated_items = [(item, rating) for item, rating in rated_items 
                          if rating >= self.config['rating_threshold']]
        
        # 从相似物品中召回
        candidates = {}
        sim_sums = {}
        
        for rated_item, rating in rated_items:
            similar_items = []
            for item_id in self.item_ratings.keys():
                if item_id == rated_item or item_id in target_ratings:
                    continue
                
                sim = self.compute_similarity(rated_item, item_id)
                if sim > self.config['min_similarity']:
                    similar_items.append((sim, item_id))
            
            # 取TopK相似物品
            similar_items.sort(reverse=True, key=lambda x: x[0])
            top_similar = similar_items[:self.config['similar_item_count']]
            
            # 累加得分
            for sim, item_id in top_similar:
                if self.config['use_ranking_mode']:
                    # 用户建议：直接用rating
                    candidates[item_id] = candidates.get(item_id, 0) + sim * rating
                else:
                    # 传统模式：用deviation
                    candidates[item_id] = candidates.get(item_id, 0) + sim * (rating - 3.5)
                sim_sums[item_id] = sim_sums.get(item_id, 0) + sim
        
        # 计算最终得分
        results = []
        for item_id, total in candidates.items():
            if sim_sums[item_id] > 0:
                score = total / sim_sums[item_id]
                results.append((item_id, score))
        
        results.sort(reverse=True, key=lambda x: x[1])
        return [item_id for item_id, score in results[:top_n]]

# ==================== 评估 ====================

def evaluate(recommender, test_set, top_n=10, relevance_threshold=4.0):
    """评估推荐系统"""
    precisions = []
    recalls = []
    ndcgs = []
    
    for user_id, test_ratings in tqdm(test_set.items(), desc="Evaluating", leave=False):
        if user_id not in recommender.user_ratings:
            continue
        
        recommended = recommender.recommend(user_id, top_n)
        relevant_items = set(item_id for item_id, rating in test_ratings.items() 
                            if rating >= relevance_threshold)
        
        if not relevant_items:
            continue
        
        hits = len(set(recommended) & relevant_items)
        precision = hits / min(top_n, len(recommended)) if recommended else 0.0
        recall = hits / len(relevant_items)
        
        dcg = 0.0
        idcg = sum(1.0 / math.log2(i + 2) for i in range(min(len(relevant_items), top_n)))
        for i, item_id in enumerate(recommended[:top_n]):
            if item_id in relevant_items:
                dcg += 1.0 / math.log2(i + 2)
        ndcg = dcg / idcg if idcg > 0 else 0.0
        
        precisions.append(precision)
        recalls.append(recall)
        ndcgs.append(ndcg)
    
    return {
        'precision': sum(precisions) / len(precisions) if precisions else 0.0,
        'recall': sum(recalls) / len(recalls) if recalls else 0.0,
        'ndcg': sum(ndcgs) / len(ndcgs) if ndcgs else 0.0
    }

# ==================== 主函数 ====================

def main():
    # 加载数据
    data_path = 'docs/ml-100k/u.data'
    print(f"Loading data from {data_path}...")
    user_ratings, item_ratings = load_movielens_ratings(data_path)
    print(f"Loaded {len(user_ratings)} users, {len(item_ratings)} items")
    
    # 分割数据集
    train_set, test_set = train_test_split(user_ratings, test_ratio=0.2)
    print(f"Train: {sum(len(rs) for rs in train_set.values())} ratings")
    print(f"Test: {sum(len(rs) for rs in test_set.values())} ratings")
    
    # ============== UserCF 实验 ==============
    print("\n" + "="*50)
    print("UserCF 实验")
    print("="*50)
    
    # 基础配置
    base_config = {
        'neighbor_count': 50,
        'min_overlap': 2,
        'min_similarity': 0.01,
        'shrinkage': True,
        'shrinkage_factor': 3,
        'use_deviation': True,
        'use_baseline': True,
        'high_rating_only': False,
        'rating_threshold': 4.0
    }
    
    experiments = [
        ("原始配置", base_config.copy()),
        ("移除deviation", {**base_config, 'use_deviation': False}),
        ("移除baseline", {**base_config, 'use_baseline': False}),
        ("双移除(deviation+baseline)", {**base_config, 'use_deviation': False, 'use_baseline': False}),
        ("只遍历高分物品(>=4.0)", {**base_config, 'high_rating_only': True, 'rating_threshold': 4.0}),
        ("高分+移除deviation", {**base_config, 'high_rating_only': True, 'rating_threshold': 4.0, 'use_deviation': False}),
        ("高分+移除deviation+移除baseline", {**base_config, 'high_rating_only': True, 'rating_threshold': 4.0, 'use_deviation': False, 'use_baseline': False}),
        ("邻居数100", {**base_config, 'neighbor_count': 100}),
        ("shrinkage_factor=5", {**base_config, 'shrinkage_factor': 5}),
        ("无shrinkage", {**base_config, 'shrinkage': False}),
        ("min_similarity=0.02", {**base_config, 'min_similarity': 0.02}),
    ]
    
    results = []
    for name, config in experiments:
        print(f"\n测试: {name}")
        print(f"配置: {config}")
        recommender = UserCF(train_set, item_ratings, config)
        metrics = evaluate(recommender, test_set, top_n=10)
        print(f"结果: P={metrics['precision']:.4f} R={metrics['recall']:.4f} N={metrics['ndcg']:.4f}")
        results.append((name, metrics))
    
    # 排序并输出最佳结果
    results.sort(key=lambda x: x[1]['precision'], reverse=True)
    print("\n" + "="*50)
    print("UserCF 实验结果排序")
    print("="*50)
    for name, metrics in results:
        print(f"{name}: P={metrics['precision']:.4f} R={metrics['recall']:.4f} N={metrics['ndcg']:.4f}")
    
    # ============== ItemCF 实验 ==============
    print("\n" + "="*50)
    print("ItemCF 实验")
    print("="*50)
    
    base_itemcf = {
        'min_overlap': 4,
        'min_similarity': 0.1,
        'shrinkage': True,
        'shrinkage_factor': 10,
        'similar_item_count': 50,
        'use_ranking_mode': True,
        'high_rating_only': True,
        'rating_threshold': 4.0
    }
    
    itemcf_experiments = [
        ("原始配置", base_itemcf.copy()),
        ("使用deviation模式", {**base_itemcf, 'use_ranking_mode': False}),
        ("高分阈值3.5", {**base_itemcf, 'rating_threshold': 3.5}),
        ("不限制高分", {**base_itemcf, 'high_rating_only': False}),
        ("similar_item_count=30", {**base_itemcf, 'similar_item_count': 30}),
        ("similar_item_count=80", {**base_itemcf, 'similar_item_count': 80}),
        ("min_overlap=3", {**base_itemcf, 'min_overlap': 3}),
        ("min_overlap=5", {**base_itemcf, 'min_overlap': 5}),
        ("min_similarity=0.05", {**base_itemcf, 'min_similarity': 0.05}),
        ("min_similarity=0.15", {**base_itemcf, 'min_similarity': 0.15}),
        ("shrinkage_factor=5", {**base_itemcf, 'shrinkage_factor': 5}),
        ("shrinkage_factor=15", {**base_itemcf, 'shrinkage_factor': 15}),
    ]
    
    item_results = []
    for name, config in itemcf_experiments:
        print(f"\n测试: {name}")
        print(f"配置: {config}")
        recommender = ItemCF(train_set, item_ratings, config)
        metrics = evaluate(recommender, test_set, top_n=10)
        print(f"结果: P={metrics['precision']:.4f} R={metrics['recall']:.4f} N={metrics['ndcg']:.4f}")
        item_results.append((name, metrics))
    
    # 排序并输出最佳结果
    item_results.sort(key=lambda x: x[1]['precision'], reverse=True)
    print("\n" + "="*50)
    print("ItemCF 实验结果排序")
    print("="*50)
    for name, metrics in item_results:
        print(f"{name}: P={metrics['precision']:.4f} R={metrics['recall']:.4f} N={metrics['ndcg']:.4f}")

if __name__ == '__main__':
    main()
