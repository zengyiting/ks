#!/usr/bin/env python3
"""
简化版算法评估脚本 - 使用小数据集快速评估
"""
import os
import math
import time
import random
from collections import defaultdict

def load_ratings(data_dir, sample_users=500):
    """加载评分数据并采样"""
    all_ratings = defaultdict(dict)
    filepath = os.path.join(data_dir, 'ratings.dat')
    
    with open(filepath, 'r', encoding='latin-1') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split('::')
            if len(parts) >= 3:
                user_id = int(parts[0])
                movie_id = int(parts[1])
                score = float(parts[2])
                all_ratings[user_id][movie_id] = score
    
    # 采样用户
    users = list(all_ratings.keys())
    if len(users) > sample_users:
        users = random.sample(users, sample_users)
    
    ratings = {user_id: all_ratings[user_id] for user_id in users}
    return ratings

def split_train_test(ratings, test_ratio=0.2):
    """划分训练集和测试集"""
    train = {}
    test = {}
    
    for user_id, user_ratings in ratings.items():
        items = list(user_ratings.keys())
        n = len(items)
        if n < 2:
            train[user_id] = user_ratings.copy()
            continue
        
        random.shuffle(items)
        test_count = max(1, int(math.ceil(n * test_ratio)))
        train_items = set(items[:-test_count])
        test_items = set(items[-test_count:])
        
        train[user_id] = {item_id: user_ratings[item_id] for item_id in train_items}
        test[user_id] = {item_id: user_ratings[item_id] for item_id in test_items}
    
    return train, test

def pearson_similarity(a, b):
    """皮尔逊相关系数"""
    common_keys = set(a.keys()) & set(b.keys())
    if len(common_keys) < 2:
        return 0.0
    
    sum_a = sum(a[k] for k in common_keys)
    sum_b = sum(b[k] for k in common_keys)
    n = len(common_keys)
    mean_a = sum_a / n
    mean_b = sum_b / n
    
    cov = 0.0
    var_a = 0.0
    var_b = 0.0
    
    for k in common_keys:
        da = a[k] - mean_a
        db = b[k] - mean_b
        cov += da * db
        var_a += da * da
        var_b += db * db
    
    if var_a == 0 or var_b == 0:
        return 0.0
    return cov / (math.sqrt(var_a) * math.sqrt(var_b))

class UserBasedCF:
    """基于用户的协同过滤"""
    def recommend(self, user_item, user_id, top_n):
        if user_id not in user_item:
            return []
        target = user_item[user_id]
        if not target:
            return []
        
        target_mean = sum(target.values()) / len(target)
        neighbors = []
        
        for other_id, other in user_item.items():
            if other_id == user_id:
                continue
            
            sim = pearson_similarity(target, other)
            if sim > 0.1:
                neighbors.append((other_id, sim))
        
        if not neighbors:
            return []
        
        neighbors.sort(key=lambda x: -x[1])
        neighbors = neighbors[:20]
        
        scores = defaultdict(float)
        sim_sums = defaultdict(float)
        
        for other_id, sim in neighbors:
            other = user_item[other_id]
            other_mean = sum(other.values()) / len(other)
            for item_id, score in other.items():
                if item_id not in target:
                    scores[item_id] += (score - other_mean) * sim
                    sim_sums[item_id] += abs(sim)
        
        results = []
        for item_id, score_sum in scores.items():
            if sim_sums[item_id] > 0:
                pred = target_mean + score_sum / sim_sums[item_id]
                results.append((item_id, pred))
        
        results.sort(key=lambda x: -x[1])
        return results[:top_n]

class ItemBasedCF:
    """基于物品的协同过滤 - 使用预计算相似度"""
    def __init__(self):
        self.item_similarity_cache = {}
    
    def recommend(self, user_item, user_id, top_n):
        if user_id not in user_item:
            return []
        target = user_item[user_id]
        if not target:
            return []
        
        scores = defaultdict(float)
        sim_sums = defaultdict(float)
        
        for item_i, score_i in target.items():
            for item_j in user_item.values():
                for candidate_item in item_j.keys():
                    if candidate_item in target:
                        continue
                    
                    key = tuple(sorted([item_i, candidate_item]))
                    if key not in self.item_similarity_cache:
                        # 快速计算相似度
                        users_i = {}
                        users_j = {}
                        for uid, ratings in user_item.items():
                            if item_i in ratings:
                                users_i[uid] = ratings[item_i]
                            if candidate_item in ratings:
                                users_j[uid] = ratings[candidate_item]
                        
                        sim = pearson_similarity(users_i, users_j)
                        self.item_similarity_cache[key] = sim
                    
                    sim = self.item_similarity_cache[key]
                    if sim > 0:
                        scores[candidate_item] += sim * score_i
                        sim_sums[candidate_item] += sim
        
        results = [(item_id, scores[item_id] / sim_sums[item_id] if sim_sums[item_id] > 0 else 0) 
                   for item_id in scores if sim_sums[item_id] > 0]
        results.sort(key=lambda x: -x[1])
        return results[:top_n]

class BehaviorBasedCF:
    """基于行为的推荐"""
    def recommend(self, user_item, user_id, top_n):
        if user_id not in user_item or not user_item[user_id]:
            return []
        
        implicit = {}
        for uid, ratings in user_item.items():
            row = {}
            for item_id, score in ratings.items():
                row[item_id] = score / 5.0  # 归一化
            implicit[uid] = row
        
        item_cf = ItemBasedCF()
        return item_cf.recommend(implicit, user_id, top_n)

class HybridCF:
    """混合推荐"""
    def __init__(self):
        self.user_cf = UserBasedCF()
        self.item_cf = ItemBasedCF()
    
    def recommend(self, user_item, user_id, top_n):
        if user_id not in user_item or not user_item[user_id]:
            return []
        
        target = user_item[user_id]
        
        user_recs = self.user_cf.recommend(user_item, user_id, top_n * 2)
        item_recs = self.item_cf.recommend(user_item, user_id, top_n * 2)
        
        user_rank = {item_id: 1.0 / (1.0 + i) for i, (item_id, _) in enumerate(user_recs)}
        item_rank = {item_id: 1.0 / (1.0 + i) for i, (item_id, _) in enumerate(item_recs)}
        
        candidates = set(user_rank.keys()) | set(item_rank.keys())
        candidates -= set(target.keys())
        
        merged = []
        for item_id in candidates:
            score = 0.5 * user_rank.get(item_id, 0.0) + 0.5 * item_rank.get(item_id, 0.0)
            if score > 0:
                merged.append((item_id, score))
        
        merged.sort(key=lambda x: -x[1])
        return merged[:top_n]

def evaluate(algorithm, train, test, top_n=10, threshold=4.0):
    """评估算法"""
    precision = 0.0
    recall = 0.0
    ndcg = 0.0
    users = 0
    
    for user_id, test_ratings in test.items():
        if user_id not in train:
            continue
        
        relevant = set(item_id for item_id, score in test_ratings.items() if score >= threshold)
        if not relevant:
            continue
        
        recs = algorithm.recommend(train, user_id, top_n)
        rec_items = [item_id for item_id, score in recs]
        
        hits = sum(1 for item_id in rec_items if item_id in relevant)
        
        precision += hits / top_n
        recall += hits / len(relevant)
        
        dcg = sum(1.0 / math.log2(i + 2) for i, item_id in enumerate(rec_items) if item_id in relevant)
        ideal_hits = min(top_n, len(relevant))
        idcg = sum(1.0 / math.log2(i + 2) for i in range(ideal_hits))
        ndcg += dcg / idcg if idcg > 0 else 0.0
        
        users += 1
    
    if users == 0:
        return {'precision': 0.0, 'recall': 0.0, 'ndcg': 0.0, 'users': 0}
    
    return {
        'precision': precision / users,
        'recall': recall / users,
        'ndcg': ndcg / users,
        'users': users
    }

def main():
    data_dir = 'docs/ml-1m'
    sample_users = 200
    
    print(f"加载ml-1m数据集 (采样{sample_users}用户)...")
    start_time = time.time()
    ratings = load_ratings(data_dir, sample_users)
    print(f"  加载完成: {len(ratings)}用户, {sum(len(r) for r in ratings.values())}评分")
    print(f"  耗时: {time.time() - start_time:.2f}秒")
    
    print(f"\n划分训练集和测试集...")
    train, test = split_train_test(ratings)
    
    algorithms = {
        'USER_BASED': UserBasedCF(),
        'ITEM_BASED': ItemBasedCF(),
        'BEHAVIOR_BASED': BehaviorBasedCF(),
        'HYBRID': HybridCF()
    }
    
    print(f"\n评估各算法...")
    results = {}
    
    for name, algorithm in algorithms.items():
        print(f"\n  评估 {name}...")
        start_time = time.time()
        metrics = evaluate(algorithm, train, test)
        elapsed = time.time() - start_time
        results[name] = metrics
        
        print(f"    精确率@10: {metrics['precision']:.4f}")
        print(f"    召回率@10: {metrics['recall']:.4f}")
        print(f"    NDCG@10:   {metrics['ndcg']:.4f}")
        print(f"    评估用户: {metrics['users']}")
        print(f"    耗时: {elapsed:.2f}秒")
    
    print("\n" + "=" * 70)
    print(f"ml-1m数据集算法效果评估 (采样{sample_users}用户)")
    print("=" * 70)
    print(f"{'算法':<15} {'精确率@10':<10} {'召回率@10':<10} {'NDCG@10':<10}")
    print("-" * 70)
    
    for name, metrics in results.items():
        print(f"{name:<15} {metrics['precision']:<10.4f} {metrics['recall']:<10.4f} {metrics['ndcg']:<10.4f}")
    
    print("=" * 70)

if __name__ == '__main__':
    main()
