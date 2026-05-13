#!/usr/bin/env python3
"""
使用完整ml-1m数据集(100万评分)进行离线评估
包含进度显示和结果保存
"""
import os
import sys
import math
import time
import json
import random
from collections import defaultdict
from datetime import datetime

def load_all_ratings(data_dir):
    """加载完整评分数据"""
    ratings = defaultdict(dict)
    filepath = os.path.join(data_dir, 'ratings.dat')

    print("加载评分数据...")
    with open(filepath, 'r', encoding='latin-1') as f:
        for i, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            parts = line.split('::')
            if len(parts) >= 3:
                user_id = int(parts[0])
                movie_id = int(parts[1])
                score = float(parts[2])
                ratings[user_id][movie_id] = score

            if i % 200000 == 0:
                print(f"  已加载 {i} 条评分...")
                sys.stdout.flush()

    return ratings

def split_train_test(ratings, test_ratio=0.2):
    """划分训练集和测试集"""
    train = {}
    test = {}

    total_users = len(ratings)
    print(f"\n划分数据集 ({total_users}用户)...")

    for idx, (user_id, user_ratings) in enumerate(ratings.items(), 1):
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

        if idx % 1000 == 0:
            print(f"  已处理 {idx}/{total_users} 用户...")
            sys.stdout.flush()

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
        neighbors = neighbors[:50]

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
    """基于物品的协同过滤 - 预计算物品相似度"""
    def __init__(self):
        self.item_sim = {}
        self.item_users = {}

    def precompute(self, user_item):
        """预计算物品相似度矩阵"""
        print("  预计算物品相似度...")

        # 构建物品-用户矩阵
        for uid, ratings in user_item.items():
            for item_id, score in ratings.items():
                if item_id not in self.item_users:
                    self.item_users[item_id] = {}
                self.item_users[item_id][uid] = score

        items = list(self.item_users.keys())
        total = len(items)
        print(f"  物品数量: {total}")

        # 计算物品相似度
        for i, item_i in enumerate(items):
            if i % 500 == 0:
                print(f"    计算进度: {i}/{total} ({i*100//total}%)")
                sys.stdout.flush()

            for j in range(i + 1, min(i + 200, total)):
                item_j = items[j]
                users_i = self.item_users[item_i]
                users_j = self.item_users[item_j]

                common = set(users_i.keys()) & set(users_j.keys())
                if len(common) < 3:
                    continue

                sim = pearson_similarity(users_i, users_j)
                if sim > 0.05:
                    if item_i not in self.item_sim:
                        self.item_sim[item_i] = {}
                    if item_j not in self.item_sim:
                        self.item_sim[item_j] = {}
                    self.item_sim[item_i][item_j] = sim
                    self.item_sim[item_j][item_i] = sim

        print("  预计算完成")

    def recommend(self, user_item, user_id, top_n):
        if user_id not in user_item or not user_item[user_id]:
            return []

        target = user_item[user_id]
        scores = defaultdict(float)
        sim_sums = defaultdict(float)

        for item_i, score_i in target.items():
            if item_i not in self.item_sim:
                continue

            for item_j, sim in self.item_sim[item_i].items():
                if item_j in target:
                    continue
                scores[item_j] += sim * score_i
                sim_sums[item_j] += sim

        results = []
        for item_id, score_sum in scores.items():
            if sim_sums[item_id] > 0:
                pred = score_sum / sim_sums[item_id]
                results.append((item_id, pred))

        results.sort(key=lambda x: -x[1])
        return results[:top_n]

class BehaviorBasedCF:
    """基于行为的推荐"""
    def __init__(self):
        self.item_cf = ItemBasedCF()

    def precompute(self, user_item):
        """预计算"""
        implicit = {}
        for uid, ratings in user_item.items():
            row = {}
            for item_id, score in ratings.items():
                row[item_id] = 0.2 + 0.8 * (score / 5.0)
            implicit[uid] = row
        self.item_cf.precompute(implicit)

    def recommend(self, user_item, user_id, top_n):
        if user_id not in user_item or not user_item[user_id]:
            return []

        implicit = {}
        for uid, ratings in user_item.items():
            row = {}
            for item_id, score in ratings.items():
                row[item_id] = 0.2 + 0.8 * (score / 5.0)
            implicit[uid] = row

        return self.item_cf.recommend(implicit, user_id, top_n)

class HybridCF:
    """混合推荐"""
    def __init__(self):
        self.user_cf = UserBasedCF()
        self.item_cf = ItemBasedCF()

    def precompute(self, user_item):
        """预计算"""
        self.item_cf.precompute(user_item)

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
        elif rated_count < 18:
            w_item, w_user, w_pop = 0.35, 0.20, 0.15
        else:
            w_item, w_user, w_pop = 0.42, 0.23, 0.15

        merged = []
        for item_id in candidates:
            score = w_item * item_rank.get(item_id, 0.0) + w_user * user_rank.get(item_id, 0.0) + w_pop * pop_scores.get(item_id, 0.0)
            if score > 0:
                merged.append((item_id, score))

        merged.sort(key=lambda x: -x[1])
        return merged[:top_n]

def evaluate(algorithm, train, test, top_n=10, threshold=4.0, algorithm_name=""):
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

    total_users = len(test)
    print(f"\n  评估 {algorithm_name} ({total_users}用户)...")

    for idx, (user_id, test_ratings) in enumerate(test.items(), 1):
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

        if idx % 500 == 0:
            print(f"    进度: {idx}/{total_users} ({idx*100//total_users}%)")
            sys.stdout.flush()

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

def save_results(results, output_dir):
    """保存评估结果"""
    os.makedirs(output_dir, exist_ok=True)

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"eval_full_{timestamp}.json"
    filepath = os.path.join(output_dir, filename)

    output = {
        'timestamp': datetime.now().isoformat(),
        'dataset': 'ml-1m',
        'total_users': results['total_users'],
        'total_ratings': results['total_ratings'],
        'train_size': results['train_size'],
        'test_size': results['test_size'],
        'test_ratio': 0.2,
        'top_n': 10,
        'relevance_threshold': 4.0,
        'algorithms': results['algorithms']
    }

    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(output, f, indent=2, ensure_ascii=False)

    print(f"\n结果已保存: {filepath}")
    return filepath

def main():
    data_dir = 'docs/ml-1m'
    output_dir = 'reports/offline-eval'

    print("=" * 70)
    print("ml-1m完整数据集离线评估")
    print("=" * 70)

    start_total = time.time()

    # 加载数据
    ratings = load_all_ratings(data_dir)
    total_users = len(ratings)
    total_ratings = sum(len(r) for r in ratings.values())
    print(f"\n数据集统计:")
    print(f"  用户数: {total_users}")
    print(f"  评分数: {total_ratings}")

    # 划分数据集
    train, test = split_train_test(ratings)
    train_size = sum(len(r) for r in train.values())
    test_size = sum(len(r) for r in test.values())
    print(f"\n数据划分:")
    print(f"  训练集: {train_size}")
    print(f"  测试集: {test_size}")

    # 初始化算法
    algorithms = {
        'USER_BASED': UserBasedCF(),
        'ITEM_BASED': ItemBasedCF(),
        'BEHAVIOR_BASED': BehaviorBasedCF(),
        'HYBRID': HybridCF()
    }

    # 预计算
    print("\n预计算阶段...")
    for name, algorithm in algorithms.items():
        if hasattr(algorithm, 'precompute'):
            print(f"  {name} 预计算...")
            start = time.time()
            algorithm.precompute(train)
            print(f"    耗时: {time.time() - start:.2f}秒")

    # 评估
    print("\n评估阶段...")
    eval_results = {}

    for name, algorithm in algorithms.items():
        print(f"\n{'='*50}")
        print(f"评估 {name}")
        print('='*50)
        start = time.time()
        metrics = evaluate(algorithm, train, test, algorithm_name=name)
        elapsed = time.time() - start
        eval_results[name] = {
            'precisionAtK': round(metrics['precision'], 6),
            'recallAtK': round(metrics['recall'], 6),
            'ndcgAtK': round(metrics['ndcg'], 6),
            'coverage': round(metrics['coverage'], 6),
            'evaluatedUsers': metrics['users'],
            'elapsedSeconds': round(elapsed, 2)
        }
        print(f"\n  结果:")
        print(f"    精确率@10: {metrics['precision']:.6f}")
        print(f"    召回率@10: {metrics['recall']:.6f}")
        print(f"    NDCG@10:   {metrics['ndcg']:.6f}")
        print(f"    覆盖率:    {metrics['coverage']:.6f}")
        print(f"    评估用户:  {metrics['users']}")
        print(f"    耗时:      {elapsed:.2f}秒")

    # 汇总
    print("\n" + "=" * 70)
    print("ml-1m完整数据集评估结果汇总")
    print("=" * 70)
    print(f"{'算法':<15} {'精确率@10':<12} {'召回率@10':<12} {'NDCG@10':<12} {'覆盖率':<12}")
    print("-" * 70)

    for name, metrics in eval_results.items():
        print(f"{name:<15} {metrics['precisionAtK']:<12.6f} {metrics['recallAtK']:<12.6f} {metrics['ndcgAtK']:<12.6f} {metrics['coverage']:<12.6f}")

    print("=" * 70)

    # 保存结果
    results = {
        'total_users': total_users,
        'total_ratings': total_ratings,
        'train_size': train_size,
        'test_size': test_size,
        'algorithms': eval_results
    }

    save_results(results, output_dir)

    total_elapsed = time.time() - start_total
    print(f"\n总耗时: {total_elapsed:.2f}秒 ({total_elapsed/60:.2f}分钟)")

if __name__ == '__main__':
    main()
