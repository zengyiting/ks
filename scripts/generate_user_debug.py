"""
生成逐用户诊断：对评估中命中为 0 的用户（最多 10 个）输出详尽诊断信息
输出目录：reports/offline-eval/debug/

用法：python scripts/generate_user_debug.py
"""

import csv, math, json, os, sys
from collections import defaultdict

MAP_ACTION = {'view':1.6,'click':2.2,'cart':3.8,'favorite':4.5,'fav':4.5,'buy':5.0}
CSV_PATH = 'docs/UserBehavior_cleaned.csv'
OUT_DIR = os.path.join('reports', 'offline-eval', 'debug')
TOP_K = 10
NEIGHBOR_LIMIT = 50
SHRINK_ALPHA = 1
SIM_FILTER_EPS = 1e-6

os.makedirs(OUT_DIR, exist_ok=True)

def safe_int(x):
    try:
        return int(x)
    except:
        return None

def overlap(a,b):
    return len(set(a.keys()) & set(b.keys()))

def cosine(a,b):
    ks = set(a.keys()) & set(b.keys())
    if not ks: return 0.0
    dot = sum(a[k]*b[k] for k in ks)
    na = math.sqrt(sum(v*v for v in a.values()))
    nb = math.sqrt(sum(v*v for v in b.values()))
    if na==0 or nb==0: return 0.0
    return dot/(na*nb)

def pearson(a,b):
    ks = list(set(a.keys()) & set(b.keys()))
    n = len(ks)
    if n<2: return 0.0
    sumA = sum(a[k] for k in ks)
    sumB = sum(b[k] for k in ks)
    sumA2 = sum(a[k]*a[k] for k in ks)
    sumB2 = sum(b[k]*b[k] for k in ks)
    sumAB = sum(a[k]*b[k] for k in ks)
    num = sumAB - (sumA*sumB/n)
    den = math.sqrt((sumA2 - sumA*sumA/n)*(sumB2 - sumB*sumB/n))
    if den<=1e-12: return 0.0
    return num/den

def shrink(sim, ov, alpha):
    if ov<=0: return 0.0
    w = ov/(ov + max(1, alpha))
    return sim * w

# 读取并切分数据（时间切分）
user_items = defaultdict(list)
if not os.path.exists(CSV_PATH):
    print('ERROR: 数据文件不存在:', CSV_PATH, file=sys.stderr)
    sys.exit(2)

with open(CSV_PATH, newline='', encoding='utf-8') as f:
    rdr = csv.reader(f)
    for row in rdr:
        if len(row) < 5:
            continue
        u = safe_int(row[0])
        it = safe_int(row[2])
        act = row[3].strip().lower() if row[3] else ''
        ts = safe_int(row[4])
        if u is None or it is None or ts is None:
            continue
        score = MAP_ACTION.get(act)
        if score is None:
            continue
        user_items[u].append((ts, it, score))

train = {}
test = {}
train_matrix = {}
train_item_ts = {}
all_items = set()

for u, lst in user_items.items():
    lst.sort()
    n = len(lst)
    items = [(t,i,s) for (t,i,s) in lst]
    if n < 2:
        train[u] = [(i,s,t) for (t,i,s) in items]
        test[u] = []
        if train[u]:
            train_matrix[u] = {i:s for (i,s,t) in train[u]}
            train_item_ts[u] = {i:t for (i,s,t) in train[u]}
        continue
    test_count = math.ceil(n * 0.2)
    if test_count >= n:
        test_count = n - 1
    train_count = n - test_count
    train[u] = [(i,s,t) for (t,i,s) in items[:train_count]]
    test[u] = [(i,s,t) for (t,i,s) in items[train_count:]]
    train_matrix[u] = {i:s for (i,s,t) in train[u]}
    train_item_ts[u] = {i:t for (i,s,t) in train[u]}
    for (i,s,t) in train[u]:
        all_items.add(i)

# precompute popularity for fallback
pop_stats = defaultdict(lambda: [0.0,0])
for u, entries in train.items():
    for (it, sc, ts) in entries:
        pop_stats[it][0] += sc
        pop_stats[it][1] += 1
pop_list = [(it, (s/count)*math.log1p(count)) for it,(s,count) in pop_stats.items()]
pop_list.sort(key=lambda x:-x[1])

# iterate用户并生成诊断
os.makedirs(OUT_DIR, exist_ok=True)

evaluable_users = 0
zero_hit_users = 0
debug_count = 0
max_debug = 10
fail_user_ids = []

user_ids = sorted(test.keys())

for idx, u in enumerate(user_ids, start=1):
    test_entries = test[u]
    relevant = [it for (it,sc,t) in test_entries if sc >= 1.5]
    if not relevant:
        continue
    evaluable_users += 1
    target = train_matrix.get(u, {})
    if not target:
        # no train
        continue
    target_mean = sum(target.values())/len(target)

    neighbor_sims = []
    for v, other in train_matrix.items():
        if v == u: continue
        ov = overlap(target, other)
        if ov <= 0: continue
        sim = pearson(target, other)
        if abs(sim) <= 1e-12:
            sim = cosine(target, other)
        sim = shrink(sim, ov, SHRINK_ALPHA)
        if sim <= SIM_FILTER_EPS:
            continue
        neighbor_sims.append((v, sim, ov))

    recs = []
    if neighbor_sims:
        neighbor_sims.sort(key=lambda x:-x[1])
        neighbor_sims = neighbor_sims[:NEIGHBOR_LIMIT]
        scores = {}
        simSums = {}
        contributions = defaultdict(list)  # item -> list of (neighbor, contrib)
        for (v, sim, ov) in neighbor_sims:
            other = train_matrix.get(v, {})
            other_mean = sum(other.values())/len(other) if other else 0.0
            for item, r in other.items():
                if item in target: continue
                contrib = (r - other_mean) * sim
                scores[item] = scores.get(item, 0.0) + contrib
                simSums[item] = simSums.get(item, 0.0) + abs(sim)
                contributions[item].append((v, round(contrib,6)))
        for item, val in scores.items():
            den = simSums.get(item, 0.0)
            if den <= 1e-12: continue
            score = target_mean + val/den
            recs.append((item, score, den))
        recs.sort(key=lambda x:-x[1])
        recs = recs[:TOP_K]
        rec_items = [it for it,_,_ in recs]
    else:
        rec_items = []

    if not rec_items:
        # fallback to popularity (exclude user's train items)
        rec_items = [it for (it,_) in pop_list if it not in target][:TOP_K]

    hits = [it for it in rec_items if it in relevant]
    if len(hits) == 0:
        zero_hit_users += 1
        if debug_count < max_debug:
            # build detailed debug
            dbg = {
                'user': u,
                'train_items': [{'item':it,'score':sc,'ts':ts} for (it,sc,ts) in train.get(u, [])],
                'test_items': [{'item':it,'score':sc,'ts':ts,'relevant': (sc>=1.5)} for (it,sc,ts) in test.get(u, [])],
                'relevant_items': relevant,
                'neighbors_count': len(neighbor_sims),
                'top_neighbors': [],
                'recommendations': [],
                'popular_fallback_used': False
            }
            # top neighbors
            for (v, sim, ov) in (neighbor_sims[:10] if neighbor_sims else []):
                common = list(set(target.keys()) & set(train_matrix.get(v,{}).keys()))
                dbg['top_neighbors'].append({'neighbor': v, 'sim': sim, 'overlap': ov, 'common_items': common[:10]})
            # recommendations with contributors
            if neighbor_sims:
                for (item, score, den) in recs:
                    contribs = contributions.get(item, [])
                    contribs.sort(key=lambda x:-abs(x[1]))
                    dbg['recommendations'].append({'item': item, 'predicted_score': score, 'simSum': den, 'top_contributors': contribs[:5]})
            else:
                dbg['popular_fallback_used'] = True
                dbg['recommendations'] = [{'item': it} for it in rec_items]

            fname = os.path.join(OUT_DIR, f'user_{u}.json')
            with open(fname, 'w', encoding='utf-8') as fo:
                json.dump(dbg, fo, ensure_ascii=False, indent=2)
            debug_count += 1
            fail_user_ids.append(u)

    # progress logging for long runs
    if evaluable_users % 500 == 0:
        print(f'Processed evaluable users: {evaluable_users}, zero-hit so far: {zero_hit_users}', flush=True)

# 写 summary
summary = {
    'evaluable_users': evaluable_users,
    'zero_hit_users': zero_hit_users,
    'zero_hit_fraction': (zero_hit_users/evaluable_users) if evaluable_users>0 else 0.0,
    'debug_written': debug_count,
    'sample_failed_user_ids': fail_user_ids[:max_debug]
}
with open(os.path.join(OUT_DIR, 'summary.json'), 'w', encoding='utf-8') as fo:
    json.dump(summary, fo, ensure_ascii=False, indent=2)

print(json.dumps(summary, indent=2, ensure_ascii=False))
print('详细文件写入：', OUT_DIR)
print('完成。')
