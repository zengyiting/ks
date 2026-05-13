#!/usr/bin/env python3
"""
使用ml-1m数据集快速评估四个推荐算法的效果
采用采样策略加速评估过程
"""
import os
import math
import time
import random
from collections import defaultdict

def load_ratings(data_dir):
    """加载评分数据"""
    ratings = defaultdict(dict)
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
                ratings[user_id][movie_id] = score
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

def cosine_similarity(a, b):
    """快速余弦相似度计算"""
    dot = 0.0
    norm_a = 0.0
    norm_b = 0.0
    
    for key in a:
        if key in b:
            dot += a[key] * b[key]
            norm_a += a[key] * a[key]
            norm_b += b[key] * b[key]
    
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return dot / (math.sqrt(norm_a) * math.sqrt(norm_b))

def pearson_similarity(a, b):
    """快速皮尔逊相关系数计算"""
    common = []
    for key in a:
        if key in b:
            common.append((a[key], b[key]))
    
    if len(common) < 2:
        return 0.0
    
    sum_a = sum(p[0] for p in common)
    sum_b = sum(p[1] for p in common)
    n = len(common)
    mean_a = sum_a / n
    mean_b = sum_b / n
    
    cov = 0.0
    var_a = 0.0
    var_b = 0.0
    
    for va, vb in common:
        da = va - mean_a
        db = vb - mean_b
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
            
            common = set(target.keys()) & set(other.keys())
            if len(common) < 2:
                continue
            
            sim = pearson_similarity(target, other)
            if sim <= 0.1:
                continue
            neighbors.append((other_id, sim))
        
        if not neighbors:
            return []
        
        neighbors.sort(key=lambda x: -x[1])
        neighbors = neighbors[:30]
        
        scores = defaultdict(float)
        sim_sums = defaultdict(float)
        
        for other_id, sim in neighbors:
            other = user_item[other_id]
            other_mean = sum(other.values()) / len(other)
            for item_id, score in other.items():
                if item_id in target:
                    continue
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
    """基于物品的协同过滤"""
    def recommend(self, user_item, user_id, top_n):
        if user_id not in user_item:
            return []
        target = user_item[user_id]
        if not target:
            return []
        
        # 构建物品-用户矩阵
        item_users = defaultdict(dict)
        for uid, ratings in user_item.items():
            for item_id, score in ratings.items():
                item_users[item_id][uid] = score
        
        candidates = set(item_users.keys()) - set(target.keys())
        if not candidates:
            return []
        
        item_means = {item: sum(users.values())/len(users) for item, users in item_users.items()}
        scores = defaultdict(float)
        sim_sums = defaultdict(float)
        
        for item_i, score_i in target.items():
            users_i = item_users.get(item_i, {})
            mean_i = item_means.get(item_i, 0.0)
            
            for item_j in candidates:
                if item_i == item_j:
                    continue
                users_j = item_users.get(item_j, {})
                
                common = set(users_i.keys()) & set(users_j.keys())
                if len(common) < 2:
                    continue
                
                sim = pearson_similarity(users_i, users_j)
                if sim <= 0:
                    continue
                
                scores[item_j] += sim * (score_i - mean_i)
                sim_sums[item_j] += abs(sim)
        
        results = []
        for item_j, score_sum in scores.items():
            if sim_sums[item_j] > 0:
                pred = item_means.get(item_j, 0.0) + score_sum / sim_sums[item_j]
                results.append((item_j, pred))
        
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
                strength = 0.2 + 0.8 * (score / 5.0)
                row[item_id] = strength
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
        pool_size = max(top_n * 3, 15)
        
        user_recs = self.user_cf.recommend(user_item, user_id, pool_size)
        item_recs = self.item_cf.recommend(user_item, user_id, pool_size)
        
        user_rank = {item_id: 1.0 / (1.0 + i) for i, (item_id, _) in enumerate(user_recs)}
        item_rank = {item_id: 1.0 / (1.0 + i) for i, (item_id, _) in enumerate(item_recs)}
        
        # 热门得分
        popularity = defaultdict(lambda: [0.0, 0])
        for ratings in user_item.values():
            for item_id, score in ratings.items():
                popularity[item_id][0] += score
                popularity[item_id][1] += 1
        
        pop_scores = {}
        max_pop = 1.0
        for item_id, (total, count) in popularity.items():
            if count > 0:
                avg = total / count
                pop_scores[item_id] = avg * math.log1p(count)
                max_pop = max(max_pop, pop_scores[item_id])
        
        for item_id in pop_scores:
            pop_scores[item_id] /= max_pop
        
        rated_count = len(target)
        if rated_count < 6:
            w_item, w_user, w_pop = 0.25, 0.10, 0.20
        elif rated_count < 18:
            w_item, w_user, w_pop = 0.35, 0.20, 0.15
        else:
            w_item, w_user, w_pop = 0.42, 0.23, 0.15
        
        candidates = set(item_rank.keys()) | set(user_rank.keys()) | set(pop_scores.keys())
        candidates -= set(target.keys())
        
        merged = []
        for item_id in candidates:
            score = w_item * item_rank.get(item_id, 0.0) + w_user * user_rank.get(item_id, 0.0) + w_pop * pop_scores.get(item_id, 0.0)
            if score > 0:
                merged.append((item_id, score))
        
        merged.sort(key=lambda x: -x[1])
        return merged[:top_n]

def evaluate_algorithm(algorithm, train, test, sample_users=None):
    """评估算法性能"""
    top_n = 10
    threshold = 4.0
    
    users = list(test.keys())
    if sample_users and len(users) > sample_users:
        users = random.sample(users, sample_users)
    
    precision_sum = 0.0
    recall_sum = 0.0
    ndcg_sum = 0.0
    covered_items = set()
    users_evaluated = 0
    
    for user_id in users:
        if user_id not in train:
            continue
        
        test_ratings = test[user_id]
        relevant = set(item_id for item_id, score in test_ratings.items() if score >= threshold)
        if not relevant:
            continue
        
        recs = algorithm.recommend(train, user_id, top_n)
        rec_items = [item_id for item_id, score in recs]
        covered_items.update(rec_items)
        
        hits = sum(1 for item_id in rec_items if item_id in relevant)
        
        precision_sum += hits / top_n
        recall_sum += hits / len(relevant)
        
        dcg = sum(1.0 / math.log2(i + 2) for i, item_id in enumerate(rec_items) if item_id in relevant)
        ideal_hits = min(top_n, len(relevant))
        idcg = sum(1.0 / math.log2(i + 2) for i in range(ideal_hits))
        ndcg_sum += dcg / idcg if idcg > 0 else 0.0
        
        users_evaluated += 1
    
    if users_evaluated == 0:
        return {'precision': 0.0, 'recall': 0.0, 'ndcg': 0.0, 'coverage': 0.0, 'users': 0}
    
    all_items = set()
    for ratings in train.values():
        all_items.update(ratings.keys())
    coverage = len(covered_items) / len(all_items) if all_items else 0.0
    
    return {
        'precision': precision_sum / users_evaluated,
        'recall': recall_sum / users_evaluated,
        'ndcg': ndcg_sum / users_evaluated,
        'coverage': coverage,
        'users': users_evaluated
    }

def main():
    data_dir = 'docs/ml-1m'
    sample_size = 500  # 采样用户数量
    
    print(f"加载ml-1m数据集...")
    start_time = time.time()
    ratings = load_ratings(data_dir)
    print(f"  用户数: {len(ratings)}")
    print(f"  评分数: {sum(len(r) for r in ratings.values())}")
    print(f"  耗时: {time.time() - start_time:.2f}秒")
    
    print(f"\n划分训练集和测试集...")
    start_time = time.time()
    train, test = split_train_test(ratings, test_ratio=0.2)
    train_size = sum(len(r) for r in train.values())
    test_size = sum(len(r) for r in test.values())
    print(f"  训练集: {train_size}")
    print(f"  测试集: {test_size}")
    print(f"  耗时: {time.time() - start_time:.2f}秒")
    
    algorithms = {
        'USER_BASED': UserBasedCF(),
        'ITEM_BASED': ItemBasedCF(),
        'BEHAVIOR_BASED': BehaviorBasedCF(),
        'HYBRID': HybridCF()
    }
    
    print(f"\n评估各算法 (采样{sample_size}用户)...")
    results = {}
    
    for name, algorithm in algorithms.items():
        print(f"\n  评估 {name}...")
        start_time = time.time()
        metrics = evaluate_algorithm(algorithm, train, test, sample_size)
        elapsed = time.time() - start_time
        results[name] = metrics
        
        print(f"    精确率@10: {metrics['precision']:.4f}")
        print(f"    召回率@10: {metrics['recall']:.4f}")
        print(f"    NDCG@10:   {metrics['ndcg']:.4f}")
        print(f"    覆盖率:   {metrics['coverage']:.4f}")
        print(f"    评估用户: {metrics['users']}")
        print(f"    耗时: {elapsed:.2f}秒")
    
    print("\n" + "=" * 75)
    print(f"ml-1m数据集算法效果评估汇总 (采样{sample_size}用户)")
    print("=" * 75)
    print(f"{'算法':<15} {'精确率@10':<10} {'召回率@10':<10} {'NDCG@10':<10} {'覆盖率':<10}")
    print("-" * 75)
    
    for name, metrics in results.items():
        print(f"{name:<15} {metrics['precision']:<10.4f} {metrics['recall']:<10.4f} {metrics['ndcg']:<10.4f} {metrics['coverage']:<10.4f}")
    
    print("=" * 75)

if __name__ == '__main__':
    main()
