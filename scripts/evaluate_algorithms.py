#!/usr/bin/env python3
"""
基于MovieLens-100K的推荐算法离线评估
评估UserCF、ItemCF、Hybrid三种算法的Precision@K、Recall@K、NDCG@K指标
"""

import numpy as np
import pandas as pd
from collections import defaultdict
import math

# 数据路径
DATA_PATH = "docs/ml-100k/u.data"
RANDOM_SEED = 42

def load_data():
    """加载MovieLens-100K数据集"""
    print("正在加载数据集...")
    columns = ['user_id', 'item_id', 'rating', 'timestamp']
    df = pd.read_csv(DATA_PATH, sep='\t', names=columns, encoding='latin-1')

    print(f"数据集大小: {len(df)} 条评分记录")
    print(f"用户数: {df['user_id'].nunique()}")
    print(f"物品数: {df['item_id'].nunique()}")
    print(f"评分范围: {df['rating'].min()} - {df['rating'].max()}")
    print(f"平均评分: {df['rating'].mean():.3f}")

    return df

def train_test_split(df, test_ratio=0.2, min_rating_count=5):
    """划分训练集和测试集"""
    np.random.seed(RANDOM_SEED)

    # 筛选评分次数>=min_rating_count的用户和物品
    user_counts = df.groupby('user_id').size()
    item_counts = df.groupby('item_id').size()

    active_users = user_counts[user_counts >= min_rating_count].index
    active_items = item_counts[item_counts >= min_rating_count].index

    df_filtered = df[df['user_id'].isin(active_users) & df['item_id'].isin(active_items)]

    print(f"\n筛选后数据量: {len(df_filtered)} 条")
    print(f"筛选后用户数: {df_filtered['user_id'].nunique()}")
    print(f"筛选后物品数: {df_filtered['item_id'].nunique()}")

    # 构建用户-物品评分字典
    user_items = defaultdict(dict)
    for _, row in df_filtered.iterrows():
        user_items[row['user_id']][row['item_id']] = row['rating']

    # 随机划分训练集和测试集
    train_data = []
    test_data = []

    for user_id, items in user_items.items():
        items_list = list(items.items())
        np.random.shuffle(items_list)

        split_idx = int(len(items_list) * (1 - test_ratio))
        train_data.extend([(user_id, item_id, rating) for item_id, rating in items_list[:split_idx]])
        test_data.extend([(user_id, item_id, rating) for item_id, rating in items_list[split_idx:]])

    # 构建训练集用户-物品字典
    train_user_items = defaultdict(dict)
    for user_id, item_id, rating in train_data:
        train_user_items[user_id][item_id] = rating

    # 构建测试集（只保留rating>=4的相关物品）
    test_user_items = defaultdict(dict)
    for user_id, item_id, rating in test_data:
        if rating >= 4:
            test_user_items[user_id][item_id] = rating

    print(f"\n训练集大小: {len(train_data)}")
    print(f"测试集大小: {len(test_data)}")
    print(f"测试集相关物品数(rating>=4): {sum(len(v) for v in test_user_items.values())}")

    return train_user_items, test_user_items

def cosine_similarity(vec1, vec2):
    """计算余弦相似度"""
    dot = sum(a * b for a, b in zip(vec1, vec2))
    norm1 = math.sqrt(sum(a * a for a in vec1))
    norm2 = math.sqrt(sum(b * b for b in vec2))
    if norm1 == 0 or norm2 == 0:
        return 0
    return dot / (norm1 * norm2)

def pearson_similarity(ratings1, ratings2):
    """计算皮尔逊相关系数"""
    common_items = set(ratings1.keys()) & set(ratings2.keys())
    if len(common_items) < 2:
        return 0

    vec1 = [ratings1[item] for item in common_items]
    vec2 = [ratings2[item] for item in common_items]

    mean1 = sum(vec1) / len(vec1)
    mean2 = sum(vec2) / len(vec2)

    centered1 = [r - mean1 for r in vec1]
    centered2 = [r - mean2 for r in vec2]

    dot = sum(a * b for a, b in zip(centered1, centered2))
    norm1 = math.sqrt(sum(a * a for a in centered1))
    norm2 = math.sqrt(sum(b * b for b in centered2))

    if norm1 == 0 or norm2 == 0:
        return 0
    return dot / (norm1 * norm2)

class UserBasedCF:
    """用户协同过滤算法"""

    def __init__(self, min_similarity=0.01, max_neighbors=50):
        self.min_similarity = min_similarity
        self.max_neighbors = max_neighbors
        self.user_similarity = {}

    def fit(self, user_items):
        """训练：计算用户相似度"""
        print("\n[UserCF] 正在计算用户相似度...")
        self.user_items = user_items
        users = list(user_items.keys())
        n_users = len(users)

        # 初始化所有用户的相似度字典
        for u in users:
            self.user_similarity[u] = {}

        for i, u in enumerate(users):
            if i % 100 == 0:
                print(f"  进度: {i}/{n_users}")
            for j, v in enumerate(users):
                if i >= j:
                    continue
                # 计算共同评分物品数
                common_items = set(user_items[u].keys()) & set(user_items[v].keys())
                if len(common_items) >= 2:
                    sim = pearson_similarity(user_items[u], user_items[v])
                    if sim > self.min_similarity:
                        self.user_similarity[u][v] = sim
                        self.user_similarity[v][u] = sim

        print(f"  完成! 计算了 {len(self.user_similarity)} 个用户的相似度")

    def recommend(self, user_id, N=10):
        """为用户生成Top-N推荐"""
        if user_id not in self.user_items:
            return []

        # 获取邻居用户
        neighbors = sorted(self.user_similarity.get(user_id, {}).items(),
                         key=lambda x: x[1], reverse=True)[:self.max_neighbors]

        # 预测评分
        item_scores = defaultdict(float)
        item_sim_sums = defaultdict(float)

        for neighbor_id, sim in neighbors:
            for item_id, rating in self.user_items[neighbor_id].items():
                if item_id not in self.user_items[user_id]:  # 只预测用户未评分的物品
                    item_scores[item_id] += sim * rating
                    item_sim_sums[item_id] += abs(sim)

        # 归一化
        recommendations = []
        for item_id, score in item_scores.items():
            if item_sim_sums[item_id] > 0:
                recommendations.append((item_id, score / item_sim_sums[item_id]))

        # 排序返回Top-N
        recommendations.sort(key=lambda x: x[1], reverse=True)
        return recommendations[:N]

class ItemBasedCF:
    """物品协同过滤算法"""

    def __init__(self, min_similarity=0.01, max_similar_items=80, shrinkage=50):
        self.min_similarity = min_similarity
        self.max_similar_items = max_similar_items
        self.shrinkage = shrinkage

    def fit(self, user_items):
        """训练：计算物品相似度"""
        print("\n[ItemCF] 正在计算物品相似度...")

        # 构建物品-用户字典
        item_users = defaultdict(dict)
        for user_id, items in user_items.items():
            for item_id, rating in items.items():
                item_users[item_id][user_id] = rating

        self.item_users = item_users
        self.user_items = user_items

        items = list(item_users.keys())
        n_items = len(items)

        # 初始化所有物品的相似度字典
        self.item_similarity = {item: {} for item in items}

        # 计算物品相似度
        for i, item_i in enumerate(items):
            if i % 200 == 0:
                print(f"  进度: {i}/{n_items}")
            for j, item_j in enumerate(items):
                if i >= j:
                    continue

                # 计算共同评分用户数
                common_users = set(item_users[item_i].keys()) & set(item_users[item_j].keys())
                overlap = len(common_users)

                if overlap >= 2:
                    # 余弦相似度
                    dot = sum(item_users[item_i][u] * item_users[item_j][u] for u in common_users)
                    norm_i = math.sqrt(sum(item_users[item_i][u]**2 for u in common_users))
                    norm_j = math.sqrt(sum(item_users[item_j][u]**2 for u in common_users))

                    if norm_i > 0 and norm_j > 0:
                        cos_sim = dot / (norm_i * norm_j)
                        # Shrinkage处理
                        sim = cos_sim * overlap / (overlap + self.shrinkage)
                        if sim > self.min_similarity:
                            self.item_similarity[item_i][item_j] = sim
                            self.item_similarity[item_j][item_i] = sim

        print(f"  完成! 计算了 {len(self.item_similarity)} 个物品的相似度")

    def recommend(self, user_id, N=10):
        """为用户生成Top-N推荐"""
        if user_id not in self.user_items:
            return []

        user_ratings = self.user_items[user_id]

        # 预测评分
        item_scores = defaultdict(float)
        item_sim_sums = defaultdict(float)

        for rated_item, rating in user_ratings.items():
            similar_items = sorted(self.item_similarity.get(rated_item, {}).items(),
                                  key=lambda x: x[1], reverse=True)[:self.max_similar_items]

            for similar_item, sim in similar_items:
                if similar_item not in user_ratings:
                    item_scores[similar_item] += sim * rating
                    item_sim_sums[similar_item] += abs(sim)

        # 归一化
        recommendations = []
        for item_id, score in item_scores.items():
            if item_sim_sums[item_id] > 0:
                recommendations.append((item_id, score / item_sim_sums[item_id]))

        # 排序返回Top-N
        recommendations.sort(key=lambda x: x[1], reverse=True)
        return recommendations[:N]

class HybridRecommender:
    """混合推荐策略"""

    def __init__(self, user_cf, item_cf, alpha=0.4):
        self.user_cf = user_cf
        self.item_cf = item_cf
        self.alpha = alpha  # UserCF权重

    def recommend(self, user_id, N=10):
        """混合推荐"""
        user_recs = {item_id: score for item_id, score in self.user_cf.recommend(user_id, N * 2)}
        item_recs = {item_id: score for item_id, score in self.item_cf.recommend(user_id, N * 2)}

        # 加权融合
        hybrid_scores = defaultdict(float)
        all_items = set(user_recs.keys()) | set(item_recs.keys())

        for item_id in all_items:
            user_score = user_recs.get(item_id, 0)
            item_score = item_recs.get(item_id, 0)
            hybrid_scores[item_id] = self.alpha * user_score + (1 - self.alpha) * item_score

        recommendations = sorted(hybrid_scores.items(), key=lambda x: x[1], reverse=True)
        return recommendations[:N]

def dcg_at_k(relevances, k):
    """计算DCG@K"""
    dcg = 0
    for i, rel in enumerate(relevances[:k]):
        dcg += rel / math.log2(i + 2)
    return dcg

def ndcg_at_k(recommended_items, relevant_items, k):
    """计算NDCG@K"""
    relevances = [1 if item in relevant_items else 0 for item in recommended_items]
    dcg = dcg_at_k(relevances, k)

    # 计算理想DCG
    ideal_relevances = [1] * min(len(relevant_items), k)
    idcg = dcg_at_k(ideal_relevances, k)

    if idcg == 0:
        return 0
    return dcg / idcg

def evaluate(recommender, train_user_items, test_user_items, K=10):
    """评估推荐算法"""
    precision_sum = 0
    recall_sum = 0
    ndcg_sum = 0
    n_users = 0

    for user_id in test_user_items.keys():
        if user_id not in train_user_items:
            continue

        relevant_items = set(test_user_items[user_id].keys())
        if len(relevant_items) == 0:
            continue

        recommendations = recommender.recommend(user_id, K)
        recommended_items = [item_id for item_id, _ in recommendations]

        # Precision@K
        hits = len(set(recommended_items) & relevant_items)
        precision = hits / K
        precision_sum += precision

        # Recall@K
        recall = hits / len(relevant_items)
        recall_sum += recall

        # NDCG@K
        ndcg = ndcg_at_k(recommended_items, relevant_items, K)
        ndcg_sum += ndcg

        n_users += 1

    avg_precision = precision_sum / n_users if n_users > 0 else 0
    avg_recall = recall_sum / n_users if n_users > 0 else 0
    avg_ndcg = ndcg_sum / n_users if n_users > 0 else 0

    return avg_precision, avg_recall, avg_ndcg, n_users

def main():
    print("=" * 60)
    print("基于MovieLens-100K的推荐算法离线评估")
    print("=" * 60)

    # 加载数据
    df = load_data()

    # 划分数据集
    train_user_items, test_user_items = train_test_split(df, test_ratio=0.2)

    # 创建评估器
    user_cf = UserBasedCF(min_similarity=0.01, max_neighbors=50)
    item_cf = ItemBasedCF(min_similarity=0.01, max_similar_items=80, shrinkage=50)
    hybrid = HybridRecommender(user_cf, item_cf, alpha=0.4)

    # 训练
    print("\n" + "=" * 60)
    print("开始训练模型...")
    print("=" * 60)
    user_cf.fit(train_user_items)
    item_cf.fit(train_user_items)

    # 评估
    print("\n" + "=" * 60)
    print("开始评估...")
    print("=" * 60)

    K = 10
    relevance_threshold = 4.0

    print(f"\n评估设置:")
    print(f"  - K值: {K}")
    print(f"  - 相关性阈值: rating >= {relevance_threshold}")
    print(f"  - 测试用户数: {len(test_user_items)}")

    print(f"\n{'算法':<20} {'Precision@K':<15} {'Recall@K':<15} {'NDCG@K':<15}")
    print("-" * 65)

    # UserCF
    p, r, n, n_users = evaluate(user_cf, train_user_items, test_user_items, K)
    print(f"{'User-Based CF':<20} {p:<15.3f} {r:<15.3f} {n:<15.3f}")

    # ItemCF
    p, r, n, n_users = evaluate(item_cf, train_user_items, test_user_items, K)
    print(f"{'Item-Based CF':<20} {p:<15.3f} {r:<15.3f} {n:<15.3f}")

    # Hybrid
    p, r, n, n_users = evaluate(hybrid, train_user_items, test_user_items, K)
    print(f"{'Hybrid':<20} {p:<15.3f} {r:<15.3f} {n:<15.3f}")

    print("\n" + "=" * 60)
    print("评估完成!")
    print("=" * 60)

if __name__ == "__main__":
    main()
