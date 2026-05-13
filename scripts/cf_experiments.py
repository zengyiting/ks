#!/usr/bin/env python3
"""
协同过滤算法实验框架
用于快速验证UserCF/ItemCF的各种优化方案
"""

import os
import sys
import random
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

def cosine_similarity(ratings1, ratings2, min_overlap=2):
    """余弦相似度"""
    common_items = set(ratings1.keys()) & set(ratings2.keys())
    if len(common_items) < min_overlap:
        return 0.0
    
    dot = sum(ratings1[item] * ratings2[item] for item in common_items)
    norm1 = math.sqrt(sum(r**2 for r in ratings1.values()))
    norm2 = math.sqrt(sum(r**2 for r in ratings2.values()))
    
    if norm1 == 0 or norm2 == 0:
        return 0.0
    
    return dot / (norm1 * norm2)

def adjusted_cosine(ratings1, ratings2, global_mean=3.5, min_overlap=2):
    """调整余弦相似度（减去全局均值）"""
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

def confidence_weighted(sim, overlap):
    """置信度加权"""
    return sim * math.log1p(overlap) / math.log1p(100)

# ==================== UserCF ====================

class UserCF:
    def __init__(self, user_ratings, item_ratings, config):
        self.user_ratings = user_ratings
        self.item_ratings = item_ratings
        self.config = config
        self.user_means = {u: sum(rs.values())/len(rs) for u, rs in user_ratings.items()}
    
    def compute_similarity(self, u1, u2):
        """计算用户相似度"""
        r1 = self.user_ratings[u1]
        r2 = self.user_ratings[u2]
        overlap = len(set(r1.keys()) & set(r2.keys()))
        
        if self.config['similarity_type'] == 'pearson':
            sim = pearson_similarity(r1, r2, self.config['min_overlap'])
        elif self.config['similarity_type'] == 'cosine':
            sim = cosine_similarity(r1, r2, self.config['min_overlap'])
        elif self.config['similarity_type'] == 'adjusted_cosine':
            sim = adjusted_cosine(r1, r2, min_overlap=self.config['min_overlap'])
        else:
            sim = pearson_similarity(r1, r2, self.config['min_overlap'])
        
        if overlap < self.config['min_overlap']:
            return 0.0
        
        if self.config['shrinkage']:
            sim = apply_shrinkage(sim, overlap, self.config['shrinkage_factor'])
        elif self.config['confidence_weight']:
            sim = confidence_weighted(sim, overlap)
        
        return sim
    
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
            sim = self.compute_similarity(user_id, other_id)
            if sim > self.config['min_similarity']:
                neighbors.append((sim, other_id))
        
        neighbors.sort(reverse=True, key=lambda x: x[0])
        top_neighbors = neighbors[:self.config['neighbor_count']]
        
        # 预测评分
        predictions = {}
        sim_sums = {}
        
        for sim, other_id in top_neighbors:
            other_ratings = self.user_ratings[other_id]
            other_mean = self.user_means.get(other_id, 3.5)
            
            for item_id, rating in other_ratings.items():
                if item_id in target_ratings:
                    continue
                
                if self.config['use_deviation']:
                    deviation = rating - other_mean
                    predictions[item_id] = predictions.get(item_id, 0) + sim * deviation
                else:
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
        
        if self.config['similarity_type'] == 'adjusted_cosine':
            sim = adjusted_cosine(r1, r2, min_overlap=self.config['min_overlap'])
        elif self.config['similarity_type'] == 'pearson':
            sim = pearson_similarity(r1, r2, self.config['min_overlap'])
        else:
            sim = cosine_similarity(r1, r2, self.config['min_overlap'])
        
        if overlap < self.config['min_overlap']:
            result = 0.0
        elif self.config['shrinkage']:
            result = apply_shrinkage(sim, overlap, self.config['shrinkage_factor'])
        else:
            result = sim
        
        self.similarity_cache[(i1, i2)] = result
        self.similarity_cache[(i2, i1)] = result
        return result
    
    def recommend(self, user_id, top_n=10):
        """生成推荐"""
        target_ratings = self.user_ratings.get(user_id, {})
        if not target_ratings:
            return []
        
        # 获取用户已评分的物品及其评分
        rated_items = list(target_ratings.items())
        
        # 只从高分物品的相似物品中召回
        if self.config['high_rating_only']:
            rated_items = [(item, rating) for item, rating in rated_items 
                          if rating >= self.config['rating_threshold']]
        
        # 找到相似物品
        candidates = {}
        sim_sums = {}
        
        for rated_item, rating in rated_items:
            # 找相似物品
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
                    candidates[item_id] = candidates.get(item_id, 0) + sim * rating
                else:
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

# ==================== 评估指标 ====================

def evaluate(recommender, test_set, top_n=10, relevance_threshold=4.0):
    """评估推荐系统"""
    precisions = []
    recalls = []
    ndcgs = []
    
    for user_id, test_ratings in tqdm(test_set.items(), desc="Evaluating"):
        if user_id not in recommender.user_ratings:
            continue
        
        recommended = recommender.recommend(user_id, top_n)
        relevant_items = set(item_id for item_id, rating in test_ratings.items() 
                            if rating >= relevance_threshold)
        
        if not relevant_items:
            continue
        
        # Precision
        hits = len(set(recommended) & relevant_items)
        precision = hits / min(top_n, len(recommended)) if recommended else 0.0
        
        # Recall
        recall = hits / len(relevant_items)
        
        # NDCG
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
        'ndcg': sum(ndcgs) / len(ndcgs) if ndcgs else 0.0,
        'coverage': len(set(item for recs in [recommender.recommend(u, top_n) for u in test_set.keys()] for item in recs)) / len(recommender.item_ratings) if recommender.item_ratings else 0.0
    }

# ==================== 参数搜索 ====================

def grid_search_usercf(user_ratings, item_ratings, train_set, test_set):
    """UserCF参数搜索"""
    best_config = None
    best_score = 0.0
    
    params = {
        'similarity_type': ['pearson', 'cosine'],
        'neighbor_count': [50, 100, 150, 200],
        'min_overlap': [2, 3, 4],
        'min_similarity': [0.01, 0.02, 0.05],
        'shrinkage': [False, True],
        'shrinkage_factor': [3, 5, 10],
        'confidence_weight': [False, True],
        'use_deviation': [True, False],
        'use_baseline': [True, False]
    }
    
    total = 1
    for v in params.values():
        total *= len(v)
    print(f"Total combinations: {total}")
    
    count = 0
    for sim_type in params['similarity_type']:
        for neighbor_count in params['neighbor_count']:
            for min_overlap in params['min_overlap']:
                for min_sim in params['min_similarity']:
                    for shrinkage in params['shrinkage']:
                        for shrink_factor in params['shrinkage_factor']:
                            if not shrinkage:
                                continue
                            for conf_weight in params['confidence_weight']:
                                if shrinkage and conf_weight:
                                    continue
                                for use_dev in params['use_deviation']:
                                    for use_base in params['use_baseline']:
                                        count += 1
                                        if count % 100 == 0:
                                            print(f"Progress: {count}/{total}")
                                        
                                        config = {
                                            'similarity_type': sim_type,
                                            'neighbor_count': neighbor_count,
                                            'min_overlap': min_overlap,
                                            'min_similarity': min_sim,
                                            'shrinkage': shrinkage,
                                            'shrinkage_factor': shrink_factor,
                                            'confidence_weight': conf_weight,
                                            'use_deviation': use_dev,
                                            'use_baseline': use_base
                                        }
                                        
                                        recommender = UserCF(train_set, item_ratings, config)
                                        metrics = evaluate(recommender, test_set, top_n=10)
                                        
                                        if metrics['precision'] > best_score:
                                            best_score = metrics['precision']
                                            best_config = config
                                            best_config.update(metrics)
                                            print(f"New best! Precision: {best_score:.4f}")
                                            print(f"Config: {best_config}")
    
    return best_config

def grid_search_itemcf(user_ratings, item_ratings, train_set, test_set):
    """ItemCF参数搜索"""
    best_config = None
    best_score = 0.0
    
    params = {
        'similarity_type': ['adjusted_cosine', 'cosine'],
        'min_overlap': [3, 4, 5],
        'min_similarity': [0.05, 0.1, 0.15],
        'shrinkage': [True],
        'shrinkage_factor': [5, 10, 15],
        'similar_item_count': [30, 50, 80],
        'use_ranking_mode': [True, False],
        'high_rating_only': [True, False],
        'rating_threshold': [3.5, 4.0]
    }
    
    total = 1
    for v in params.values():
        total *= len(v)
    print(f"Total combinations: {total}")
    
    count = 0
    for sim_type in params['similarity_type']:
        for min_overlap in params['min_overlap']:
            for min_sim in params['min_similarity']:
                for shrinkage in params['shrinkage']:
                    for shrink_factor in params['shrinkage_factor']:
                        for similar_count in params['similar_item_count']:
                            for ranking_mode in params['use_ranking_mode']:
                                for high_rating in params['high_rating_only']:
                                    for rating_thresh in params['rating_threshold']:
                                        if not high_rating:
                                            continue
                                        
                                        count += 1
                                        if count % 50 == 0:
                                            print(f"Progress: {count}/{total}")
                                        
                                        config = {
                                            'similarity_type': sim_type,
                                            'min_overlap': min_overlap,
                                            'min_similarity': min_sim,
                                            'shrinkage': shrinkage,
                                            'shrinkage_factor': shrink_factor,
                                            'similar_item_count': similar_count,
                                            'use_ranking_mode': ranking_mode,
                                            'high_rating_only': high_rating,
                                            'rating_threshold': rating_thresh
                                        }
                                        
                                        recommender = ItemCF(train_set, item_ratings, config)
                                        metrics = evaluate(recommender, test_set, top_n=10)
                                        
                                        if metrics['precision'] > best_score:
                                            best_score = metrics['precision']
                                            best_config = config
                                            best_config.update(metrics)
                                            print(f"New best! Precision: {best_score:.4f}")
                                            print(f"Config: {best_config}")
    
    return best_config

# ==================== 主函数 ====================

def main():
    # 加载数据
    data_path = '../docs/ml-100k/u.data'
    if not os.path.exists(data_path):
        data_path = 'docs/ml-100k/u.data'
    
    print(f"Loading data from {data_path}...")
    user_ratings, item_ratings = load_movielens_ratings(data_path)
    print(f"Loaded {len(user_ratings)} users, {len(item_ratings)} items")
    
    # 分割数据集
    train_set, test_set = train_test_split(user_ratings, test_ratio=0.2)
    print(f"Train: {sum(len(rs) for rs in train_set.values())} ratings")
    print(f"Test: {sum(len(rs) for rs in test_set.values())} ratings")
    
    # 实验1: UserCF参数搜索
    print("\n" + "="*50)
    print("UserCF 参数搜索")
    print("="*50)
    best_usercf = grid_search_usercf(user_ratings, item_ratings, train_set, test_set)
    print("\nBest UserCF config:")
    print(best_usercf)
    
    # 实验2: ItemCF参数搜索
    print("\n" + "="*50)
    print("ItemCF 参数搜索")
    print("="*50)
    best_itemcf = grid_search_itemcf(user_ratings, item_ratings, train_set, test_set)
    print("\nBest ItemCF config:")
    print(best_itemcf)

if __name__ == '__main__':
    main()
