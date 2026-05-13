#!/usr/bin/env python3
"""
使用小样本数据集快速评估四个推荐算法
"""
import os
import math
import time
import random
from collections import defaultdict

def load_small_sample(data_dir, user_sample=100, min_ratings=20):
    """加载小样本数据集"""
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
    
    # 筛选有足够评分的用户
    qualified_users = [uid for uid, ratings in all_ratings.items() if len(ratings) >= min_ratings]
    sample_size = min(user_sample, len(qualified_users))
    sampled_users = random.sample(qualified_users, sample_size)
    
    ratings = {uid: all_ratings[uid] for uid in sampled_users}
    
    # 获取这些用户评分过的物品
    all_items = set()
    for user_ratings in ratings.values():
        all_items.update(user_ratings.keys())
    
    print(f"采样结果: {len(ratings)}用户, {len(all_items)}物品, {sum(len(r) for r in ratings.values())}评分")
    return ratings

def split_train_test(ratings, test_ratio=0.2):
    """划分训练集和测试集"""
    train = {}
    test = {}
    
    for user_id, user_ratings in ratings.items():
        items = list(user_ratings.keys())
        n = len(items)
        if n < 5:
            train[user_id] = user_ratings.copy()
            continue
        
        random.shuffle(items)
        test_count = max(2, int(math.ceil(n * test_ratio)))
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
        if user_id not in user_item or not user_item[user_id]:
            return []
        
        target = user_item[user_id]
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
        neighbors = neighbors[:15]
        
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
    """基于物品的协同过滤"""
    def recommend(self, user_item, user_id, top_n):
        if user_id not in user_item or not user_item[user_id]:
            return []
        
        target = user_item[user_id]
        
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
                users_j = item_users.get(item_j, {})
                common = set(users_i.keys()) & set(users_j.keys())
                if len(common) < 2:
                    continue
                
                sim = pearson_similarity(users_i, users_j)
                if sim > 0:
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
                row[item_id] = 0.2 + 0.8 * (score / 5.0)
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
        pool_size = top_n * 2
        
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
        
        candidates = set(user_rank.keys()) | set(item_rank.keys()) | set(pop_scores.keys())
        candidates -= set(target.keys())
        
        rated_count = len(target)
        if rated_count < 6:
            w_item, w_user, w_pop = 0.25, 0.10, 0.20
        else:
            w_item, w_user, w_pop = 0.42, 0.23, 0.15
        
        merged = []
        for item_id in candidates:
            score = w_item * item_rank.get(item_id, 0.0) + w_user * user_rank.get(item_id, 0.0) + w_pop * pop_scores.get(item_id, 0.0)
            if score > 0:
                merged.append((item_id, score))
        
        merged.sort(key=lambda x: -x[1])
        return merged[:top_n]

def evaluate(algorithm, train, test, top_n=10, threshold=4.0):
    """评估算法"""
    precision = 0.0
    recall = 0.0
    ndcg = 0.0
    coverage_items = set()
    users = 0
    
    all_items = set()
    for ratings in train.values():
        all_items.update(ratings.keys())
    for ratings in test.values():
        all_items.update(ratings.keys())
    
    for user_id, test_ratings in test.items():
        if user_id not in train:
            continue
        
        relevant = set(item_id for item_id, score in test_ratings.items() if score >= threshold)
        if not relevant:
            continue
        
        recs = algorithm.recommend(train, user_id, top_n)
        rec_items = [item_id for item_id, score in recs]
        coverage_items.update(rec_items)
        
        hits = sum(1 for item_id in rec_items if item_id in relevant)
        
        precision += hits / top_n
        recall += hits / len(relevant)
        
        dcg = sum(1.0 / math.log2(i + 2) for i, item_id in enumerate(rec_items) if item_id in relevant)
        ideal_hits = min(top_n, len(relevant))
        idcg = sum(1.0 / math.log2(i + 2) for i in range(ideal_hits))
        ndcg += dcg / idcg if idcg > 0 else 0.0
        
        users += 1
    
    if users == 0:
        return {'precision': 0.0, 'recall': 0.0, 'ndcg': 0.0, 'coverage': 0.0, 'users': 0}
    
    coverage = len(coverage_items) / len(all_items) if all_items else 0.0
    
    return {
        'precision': precision / users,
        'recall': recall / users,
        'ndcg': ndcg / users,
        'coverage': coverage,
        'users': users
    }

def main():
    data_dir = 'docs/ml-1m'
    user_sample = 100
    
    print("=" * 70)
    print(f"使用ml-1m小样本数据集评估推荐算法")
    print("=" * 70)
    
    print(f"\n加载数据 (采样{user_sample}用户)...")
    start_time = time.time()
    ratings = load_small_sample(data_dir, user_sample)
    print(f"  耗时: {time.time() - start_time:.2f}秒")
    
    print(f"\n划分训练集和测试集...")
    train, test = split_train_test(ratings)
    print(f"  训练集: {sum(len(r) for r in train.values())}评分")
    print(f"  测试集: {sum(len(r) for r in test.values())}评分")
    
    algorithms = {
        'USER_BASED': UserBasedCF(),
        'ITEM_BASED': ItemBasedCF(),
        'BEHAVIOR_BASED': BehaviorBasedCF(),
        'HYBRID': HybridCF()
    }
    
    print(f"\n评估各算法 (topN=10, 相关性阈值=4.0)...")
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
        print(f"    覆盖率:   {metrics['coverage']:.4f}")
        print(f"    评估用户: {metrics['users']}")
        print(f"    耗时:     {elapsed:.2f}秒")
    
    print("\n" + "=" * 70)
    print(f"ml-1m小样本评估结果汇总")
    print("=" * 70)
    print(f"{'算法':<15} {'精确率@10':<10} {'召回率@10':<10} {'NDCG@10':<10} {'覆盖率':<10}")
    print("-" * 70)
    
    for name, metrics in results.items():
        print(f"{name:<15} {metrics['precision']:<10.4f} {metrics['recall']:<10.4f} {metrics['ndcg']:<10.4f} {metrics['coverage']:<10.4f}")
    
    print("=" * 70)
    
    # 分析结果
    print("\n分析结论:")
    best_precision = max(results.items(), key=lambda x: x[1]['precision'])
    best_recall = max(results.items(), key=lambda x: x[1]['recall'])
    best_ndcg = max(results.items(), key=lambda x: x[1]['ndcg'])
    best_coverage = max(results.items(), key=lambda x: x[1]['coverage'])
    
    print(f"- 精确率最优: {best_precision[0]} ({best_precision[1]['precision']:.4f})")
    print(f"- 召回率最优: {best_recall[0]} ({best_recall[1]['recall']:.4f})")
    print(f"- NDCG最优:   {best_ndcg[0]} ({best_ndcg[1]['ndcg']:.4f})")
    print(f"- 覆盖率最优: {best_coverage[0]} ({best_coverage[1]['coverage']:.4f})")
    
    print("\n建议:")
    print("- 如果追求推荐准确性，优先选择", best_precision[0], "或", best_ndcg[0])
    print("- 如果追求推荐多样性和覆盖范围，优先选择", best_coverage[0])
    print("- 混合算法通常在各项指标上表现均衡")

if __name__ == '__main__':
    main()
