#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
评估所有四个推荐算法的 Python 脚本
包含纯 Python 实现的 User-Based CF, Item-Based CF, Behavior-Based, Hybrid
"""

import os
import random
import math
import json
from collections import defaultdict

def load_movielens_100k(data_path, sample_ratio=1.0):
    """
    加载 ml-100k 数据集
    sample_ratio: 采样比例，默认1.0表示使用完整数据
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
    
    # 采样
    all_users = list(all_user_item.keys())
    random.shuffle(all_users)
    sample_size = max(1, int(len(all_users) * sample_ratio))
    sampled_users = set(all_users[:sample_size])
    
    user_item = {u: all_user_item[u] for u in sampled_users if u in all_user_item}
    
    print(f"数据集加载完成（采样 {sample_ratio*100:.1f}%）：{len(user_item)} 用户，{len(all_items)} 物品，{sum(len(ratings) for ratings in user_item.values())} 条评分")
    return user_item, all_items

def split_train_test(user_item, test_ratio=0.2, seed=42, relevance_threshold=4.0):
    """划分训练集和测试集"""
    random.seed(seed)
    train = {}
    test_relevant = {}
    
    for user_id, ratings in user_item.items():
        train[user_id] = {}
        relevant_items = []
        
        items = list(ratings.keys())
        random.shuffle(items)
        test_size = int(len(items) * test_ratio)
        
        for i, item_id in enumerate(items):
            if i < test_size:
                if ratings[item_id] >= relevance_threshold:
                    relevant_items.append(item_id)
            else:
                train[user_id][item_id] = ratings[item_id]
        
        if relevant_items:
            test_relevant[user_id] = set(relevant_items)
    
    train_count = sum(len(ratings) for ratings in train.values())
    print(f"训练集：{train_count} 条评分，测试用户：{len(test_relevant)}")
    return train, test_relevant

def cosine_similarity(vec1, vec2):
    """余弦相似度"""
    common = set(vec1.keys()) & set(vec2.keys())
    if not common:
        return 0.0
    
    dot_product = sum(vec1[item] * vec2[item] for item in common)
    norm1 = math.sqrt(sum(v*v for v in vec1.values()))
    norm2 = math.sqrt(sum(v*v for v in vec2.values()))
    
    return dot_product / (norm1 * norm2) if norm1 > 0 and norm2 > 0 else 0.0

def build_item_users(train_matrix):
    """构建物品-用户矩阵"""
    item_users = defaultdict(dict)
    for user_id, ratings in train_matrix.items():
        for item_id, rating in ratings.items():
            item_users[item_id][user_id] = rating
    return item_users

def user_based_cf(train_matrix, user_id, top_n=10):
    """User-Based CF"""
    target_ratings = train_matrix.get(user_id, {})
    if not target_ratings:
        return []
    
    # 计算用户相似度
    user_similarities = {}
    for other_user, other_ratings in train_matrix.items():
        if other_user == user_id:
            continue
        sim = cosine_similarity(target_ratings, other_ratings)
        if sim > 0:
            user_similarities[other_user] = sim
    
    # 预测评分
    predictions = defaultdict(float)
    weight_sums = defaultdict(float)
    
    for other_user, sim in user_similarities.items():
        for item_id, rating in train_matrix[other_user].items():
            if item_id not in target_ratings:
                predictions[item_id] += sim * rating
                weight_sums[item_id] += sim
    
    # 归一化
    scores = []
    for item_id, total in predictions.items():
        if weight_sums[item_id] > 0:
            scores.append((item_id, total / weight_sums[item_id]))
    
    scores.sort(key=lambda x: x[1], reverse=True)
    return [item_id for item_id, _ in scores[:top_n]]

def item_based_cf(train_matrix, item_users, user_id, top_n=10):
    """Item-Based CF"""
    target_ratings = train_matrix.get(user_id, {})
    if not target_ratings:
        return []
    
    # 预测评分
    predictions = defaultdict(float)
    weight_sums = defaultdict(float)
    
    for rated_item, rating in target_ratings.items():
        rated_users = item_users.get(rated_item, {})
        
        for other_item, other_users in item_users.items():
            if other_item in target_ratings:
                continue
            
            # 计算物品相似度
            common_users = set(rated_users.keys()) & set(other_users.keys())
            if len(common_users) < 1:
                continue
            
            sim = cosine_similarity(rated_users, other_users)
            if sim > 0:
                predictions[other_item] += sim * rating
                weight_sums[other_item] += sim
    
    scores = []
    for item_id, total in predictions.items():
        if weight_sums[item_id] > 0:
            scores.append((item_id, total / weight_sums[item_id]))
    
    scores.sort(key=lambda x: x[1], reverse=True)
    return [item_id for item_id, _ in scores[:top_n]]

def behavior_based(train_matrix, item_users, user_id, top_n=10):
    """Behavior-Based 推荐"""
    target_ratings = train_matrix.get(user_id, {})
    if not target_ratings:
        return []
    
    item_scores = defaultdict(float)
    
    # 计算物品流行度
    item_popularity = {}
    for item_id, users in item_users.items():
        count = len(users)
        avg_rating = sum(users.values()) / count if count > 0 else 3.0
        item_popularity[item_id] = (avg_rating / 5.0) * math.log(1 + count)
    
    for rated_item, rating in target_ratings.items():
        rated_users = item_users.get(rated_item, {})
        if not rated_users:
            continue
        
        behavior_intensity = 0.2 + 0.8 * (rating / 5.0)
        
        for other_item, other_users in item_users.items():
            if other_item in target_ratings:
                continue
            
            common = set(rated_users.keys()) & set(other_users.keys())
            if not common:
                continue
            
            sim = len(common) / math.sqrt(len(rated_users) * len(other_users))
            pop = item_popularity.get(other_item, 0.0)
            score = behavior_intensity * sim * (0.6 + 0.4 * pop)
            item_scores[other_item] += score
    
    sorted_items = sorted(item_scores.items(), key=lambda x: x[1], reverse=True)
    return [item_id for item_id, _ in sorted_items[:top_n]]

def hybrid(train_matrix, item_users, user_id, top_n=10):
    """Hybrid 混合推荐"""
    target_ratings = train_matrix.get(user_id, {})
    if not target_ratings:
        return []
    
    # 获取各策略推荐
    user_rec = user_based_cf(train_matrix, user_id, top_n * 3)
    item_rec = item_based_cf(train_matrix, item_users, user_id, top_n * 3)
    
    # 计算排名分数
    user_rank = {item: 1.0/(i+1) for i, item in enumerate(user_rec)}
    item_rank = {item: 1.0/(i+1) for i, item in enumerate(item_rec)}
    
    # 计算流行度分数
    item_popularity = {}
    for item_id, users in item_users.items():
        count = len(users)
        avg_rating = sum(users.values()) / count if count > 0 else 3.0
        item_popularity[item_id] = (avg_rating / 5.0) * math.log(1 + count)
    max_pop = max(item_popularity.values()) if item_popularity else 1.0
    
    # 混合
    all_items = set(user_rank.keys()) | set(item_rank.keys()) | set(item_popularity.keys())
    all_items -= set(target_ratings.keys())
    
    scores = {}
    for item_id in all_items:
        score = (0.35 * item_rank.get(item_id, 0.0) +
                 0.25 * user_rank.get(item_id, 0.0) +
                 0.20 * (item_popularity.get(item_id, 0.0) / max_pop) +
                 0.20 * 0.0)
        if score > 0:
            scores[item_id] = score
    
    sorted_items = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    return [item_id for item_id, _ in sorted_items[:top_n]]

def precision_at_k(recommended, relevant, k):
    """计算 Precision@K"""
    recommended_k = recommended[:k]
    if len(recommended_k) == 0:
        return 0.0
    hits = len(set(recommended_k) & relevant)
    return hits / len(recommended_k)

def recall_at_k(recommended, relevant, k):
    """计算 Recall@K"""
    if not relevant:
        return 0.0
    recommended_k = recommended[:k]
    hits = len(set(recommended_k) & relevant)
    return hits / len(relevant)

def ndcg_at_k(recommended, relevant, k):
    """计算 NDCG@K"""
    recommended_k = recommended[:k]
    if len(recommended_k) == 0:
        return 0.0
    
    dcg = 0.0
    for i, item_id in enumerate(recommended_k):
        if item_id in relevant:
            dcg += 1.0 / math.log2(i + 2)
    
    idcg = 0.0
    ideal_size = min(k, len(relevant))
    for i in range(ideal_size):
        idcg += 1.0 / math.log2(i + 2)
    
    return dcg / idcg if idcg > 0 else 0.0

def evaluate_algorithm(algorithm, train_matrix, test_relevant, all_items, top_n=10):
    """评估算法"""
    total_precision = 0.0
    total_recall = 0.0
    total_ndcg = 0.0
    covered_items = set()
    evaluable_users = 0
    
    item_users = build_item_users(train_matrix)
    
    for user_id, relevant in test_relevant.items():
        # 调用算法
        if algorithm == 'user':
            recommended = user_based_cf(train_matrix, user_id, top_n)
        elif algorithm == 'item':
            recommended = item_based_cf(train_matrix, item_users, user_id, top_n)
        elif algorithm == 'behavior':
            recommended = behavior_based(train_matrix, item_users, user_id, top_n)
        elif algorithm == 'hybrid':
            recommended = hybrid(train_matrix, item_users, user_id, top_n)
        else:
            recommended = []
        
        if not recommended:
            continue
        
        # 去除训练集中已有物品
        train_items = set(train_matrix.get(user_id, {}).keys())
        recommended = [item for item in recommended if item not in train_items]
        
        if not recommended:
            continue
        
        total_precision += precision_at_k(recommended, relevant, top_n)
        total_recall += recall_at_k(recommended, relevant, top_n)
        total_ndcg += ndcg_at_k(recommended, relevant, top_n)
        covered_items.update(recommended)
        evaluable_users += 1
    
    avg_precision = total_precision / evaluable_users if evaluable_users > 0 else 0.0
    avg_recall = total_recall / evaluable_users if evaluable_users > 0 else 0.0
    avg_ndcg = total_ndcg / evaluable_users if evaluable_users > 0 else 0.0
    coverage = len(covered_items) / len(all_items) if len(all_items) > 0 else 0.0
    
    return {
        'name': algorithm,
        'precision': avg_precision,
        'recall': avg_recall,
        'ndcg': avg_ndcg,
        'coverage': coverage,
        'users': evaluable_users
    }

def generate_html_report(results, output_path):
    """生成 HTML 评估报告"""
    html = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>推荐系统算法评估报告</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Microsoft YaHei', sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; padding: 40px 20px; }
        .container { max-width: 1200px; margin: 0 auto; }
        .header { text-align: center; color: white; margin-bottom: 40px; }
        .header h1 { font-size: 36px; margin-bottom: 10px; text-shadow: 2px 2px 4px rgba(0,0,0,0.3); }
        .card { background: white; border-radius: 16px; box-shadow: 0 10px 40px rgba(0,0,0,0.15); padding: 30px; margin-bottom: 30px; }
        .card-title { font-size: 24px; color: #333; margin-bottom: 20px; padding-bottom: 15px; border-bottom: 2px solid #eee; }
        .table-container { overflow-x: auto; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th, td { padding: 14px; text-align: center; border-bottom: 1px solid #eee; }
        th { background: #f8f9fa; font-weight: 600; color: #333; }
        tr:hover { background: #f8f9fa; }
        .highlight { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }
        .chart-container { height: 400px; margin: 20px 0; }
        .bar-chart { display: flex; align-items: flex-end; justify-content: space-around; height: 100%; padding: 20px; }
        .bar-group { display: flex; gap: 8px; align-items: flex-end; }
        .bar { width: 35px; border-radius: 6px 6px 0 0; transition: height 0.5s ease; position: relative; }
        .bar.precision { background: #667eea; }
        .bar.recall { background: #f093fb; }
        .bar.ndcg { background: #4facfe; }
        .bar.coverage { background: #43e97b; }
        .bar-label { position: absolute; top: -25px; left: 50%; transform: translateX(-50%); font-size: 10px; white-space: nowrap; color: #333; }
        .algo-label { text-align: center; margin-top: 10px; font-size: 13px; color: #666; font-weight: 500; }
        .legend { display: flex; justify-content: center; gap: 30px; margin-top: 20px; }
        .legend-item { display: flex; align-items: center; gap: 8px; }
        .legend-color { width: 20px; height: 20px; border-radius: 4px; }
        .footer { text-align: center; color: white; margin-top: 40px; opacity: 0.8; font-size: 14px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📊 推荐系统算法评估报告</h1>
            <p>基于MovieLens 100K数据集（完整数据）</p>
        </div>
    """
    
    html += """<div class="card"><h2 class="card-title">📈 评估结果对比</h2><div class="table-container"><table><thead><tr><th>算法名称</th><th>精确率@10</th><th>召回率@10</th><th>NDCG@10</th><th>覆盖率</th><th>评估用户数</th></tr></thead><tbody>"""
    
    max_precision = max(r['precision'] for r in results)
    
    for r in results:
        is_best = r['precision'] == max_precision
        html += f"<tr{' class=\"highlight\"' if is_best else ''}>"
        html += f"<td><strong>{r['name'].upper()}</strong></td>"
        html += f"<td>{r['precision']:.4f}</td>"
        html += f"<td>{r['recall']:.4f}</td>"
        html += f"<td>{r['ndcg']:.4f}</td>"
        html += f"<td>{r['coverage']:.4f}</td>"
        html += f"<td>{r['users']}</td>"
        html += "</tr>"
    
    html += "</tbody></table></div></div>"
    
    html += """<div class="card"><h2 class="card-title">📊 可视化对比</h2><div class="chart-container"><div class="bar-chart">"""
    
    max_precision = max(r['precision'] for r in results)
    max_recall = max(r['recall'] for r in results)
    max_ndcg = max(r['ndcg'] for r in results)
    max_coverage = max(r['coverage'] for r in results)
    
    for r in results:
        html += '<div style="display: flex; flex-direction: column; align-items: center; flex: 1;"><div class="bar-group">'
        
        p_height = (r['precision'] / max_precision) * 300
        r_height = (r['recall'] / max_recall) * 300
        n_height = (r['ndcg'] / max_ndcg) * 300
        c_height = (r['coverage'] / max_coverage) * 300
        
        html += f'<div class="bar precision" style="height: {p_height:.1f}px"><span class="bar-label">{r["precision"]:.3f}</span></div>'
        html += f'<div class="bar recall" style="height: {r_height:.1f}px"><span class="bar-label">{r["recall"]:.3f}</span></div>'
        html += f'<div class="bar ndcg" style="height: {n_height:.1f}px"><span class="bar-label">{r["ndcg"]:.3f}</span></div>'
        html += f'<div class="bar coverage" style="height: {c_height:.1f}px"><span class="bar-label">{r["coverage"]:.3f}</span></div>'
        
        html += f'</div><div class="algo-label">{r["name"].upper()}</div></div>'
    
    html += """</div></div><div class="legend"><div class="legend-item"><div class="legend-color" style="background: #667eea;"></div><span>精确率</span></div><div class="legend-item"><div class="legend-color" style="background: #f093fb;"></div><span>召回率</span></div><div class="legend-item"><div class="legend-color" style="background: #4facfe;"></div><span>NDCG</span></div><div class="legend-item"><div class="legend-color" style="background: #43e97b;"></div><span>覆盖率</span></div></div></div>"""
    
    import datetime
    html += f"""
        <div class="footer">
            <p>📅 生成时间：{datetime.datetime.now()} | 📁 项目：基于协同过滤的推荐系统</p>
        </div>
    </div>
    </body>
    </html>
    """
    
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(html)
    
    return output_path

def main():
    # 数据路径
    data_path = os.path.join(os.path.dirname(__file__), '..', 'docs', 'ml-100k', 'u.data')
    if not os.path.exists(data_path):
        print(f"错误：找不到数据文件 {data_path}")
        return
    
    # 加载完整数据
    user_item, all_items = load_movielens_100k(data_path, sample_ratio=1.0)
    
    # 划分训练集和测试集
    train_matrix, test_relevant = split_train_test(user_item, test_ratio=0.2)
    
    # 评估所有算法
    algorithms = ['user', 'item', 'behavior', 'hybrid']
    results = []
    
    for algo in algorithms:
        print(f"\n正在评估 {algo.upper()} 算法...")
        result = evaluate_algorithm(algo, train_matrix, test_relevant, all_items, top_n=10)
        results.append(result)
        print(f"  Precision@10: {result['precision']:.4f}")
        print(f"  Recall@10: {result['recall']:.4f}")
        print(f"  NDCG@10: {result['ndcg']:.4f}")
        print(f"  Coverage: {result['coverage']:.4f}")
        print(f"  评估用户: {result['users']}")
    
    # 输出汇总结果
    print("\n" + "="*70)
    print("算法评估结果汇总")
    print("="*70)
    print(f"{'算法':<12} {'Precision@10':<12} {'Recall@10':<10} {'NDCG@10':<10} {'Coverage':<10}")
    print("-"*70)
    for result in results:
        print(f"{result['name'].upper():<12} "
              f"{result['precision']:.4f}          "
              f"{result['recall']:.4f}        "
              f"{result['ndcg']:.4f}        "
              f"{result['coverage']:.4f}")
    print("="*70)
    
    # 保存结果
    output_dir = os.path.join(os.path.dirname(__file__), '..', 'reports', 'offline-eval')
    os.makedirs(output_dir, exist_ok=True)
    
    json_path = os.path.join(output_dir, 'evaluation_results.json')
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, indent=2)
    print(f"\nJSON结果已保存到: {json_path}")
    
    html_path = os.path.join(output_dir, 'movielens-evaluation-report.html')
    generate_html_report(results, html_path)
    print(f"HTML报告已保存到: {html_path}")
    
    return results

if __name__ == '__main__':
    main()
