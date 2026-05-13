import csv, math, json
from collections import defaultdict

# mapping
map_action = {'view':1.6,'click':2.2,'cart':3.8,'favorite':4.5,'fav':4.5,'buy':5.0}

path = 'docs/UserBehavior_cleaned.csv'
user_items = defaultdict(list)
with open(path, newline='', encoding='utf-8') as f:
    rdr = csv.reader(f)
    for row in rdr:
        if len(row) < 5:
            continue
        try:
            user = int(row[0])
            item = int(row[2])
            act = row[3].strip().lower()
            ts = int(row[4])
        except:
            continue
        score = map_action.get(act, None)
        if score is None:
            # unknown action, skip
            continue
        user_items[user].append((ts, item, score))

# split
train = {}
test = {}
train_matrix = {}
for u, lst in user_items.items():
    lst.sort()
    n = len(lst)
    items = [(t,i,s) for (t,i,s) in lst]
    if n < 2:
        train[u] = [(i,s) for (_,i,s) in items]
        test[u] = []
        continue
    test_count = math.ceil(n * 0.2)
    if test_count >= n: test_count = n-1
    train_count = n - test_count
    train[u] = [(i,s) for (_,i,s) in items[:train_count]]
    test[u] = [(i,s) for (_,i,s) in items[train_count:]]
    train_matrix[u] = {i:s for (i,s) in train[u]}

# build item->users
item_users = defaultdict(set)
for u, entries in train.items():
    for (i,s) in entries:
        item_users[i].add(u)

# similarity functions
import math

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

def shrink(sim, overlap, alpha):
    if overlap<=0: return 0.0
    w = overlap/(overlap + max(1,alpha))
    return sim * w

# evaluate userCF like Java
users = 0
prec_sum = 0.0
rec_sum = 0.0
ndcg_sum = 0.0
covered_items = set()

for u, test_entries in test.items():
    relevant = set(i for (i,s) in test_entries if s >= 1.5)
    if not relevant:
        continue
    users += 1
    target = train_matrix.get(u, {})
    if not target:
        # no train
        continue
    target_mean = sum(target.values())/len(target)
    neighbor_sims = []
    for v, other in train_matrix.items():
        if v==u: continue
        ov = overlap(target, other)
        if ov<=0: continue
        sim = pearson(target, other)
        if abs(sim) <= 1e-12:
            sim = cosine(target, other)
        sim = shrink(sim, ov, 1)
        if sim <= 1e-6: continue
        neighbor_sims.append((v, sim))
    if not neighbor_sims:
        recs = []
    else:
        neighbor_sims.sort(key=lambda x:-x[1])
        neighbor_sims = neighbor_sims[:50]
        scores = {}
        simSums = {}
        for (v,sim) in neighbor_sims:
            other = train_matrix.get(v, {})
            other_mean = sum(other.values())/len(other) if other else 0.0
            for item, r in other.items():
                if item in target: continue
                scores[item] = scores.get(item,0.0) + (r - other_mean) * sim
                simSums[item] = simSums.get(item,0.0) + abs(sim)
        recs = []
        for item, val in scores.items():
            den = simSums.get(item,0.0)
            if den <= 1e-12: continue
            score = target_mean + val/den
            recs.append((item, score))
        recs.sort(key=lambda x:-x[1])
        recs = [it for (it,sc) in recs[:10]]
    # if recs empty, fill with popular
    if not recs:
        # compute popular from train
        stats = defaultdict(lambda: [0.0,0])
        for uu, entries in train.items():
            for (it,sc) in entries:
                stats[it][0] += sc
                stats[it][1] += 1
        pop = [(it, (s/count)*math.log1p(count)) for it,(s,count) in stats.items() if it not in target]
        pop.sort(key=lambda x:-x[1])
        recs = [it for (it,_) in pop[:10]]
    covered_items |= set(recs)
    hit = sum(1 for it in recs if it in relevant)
    prec_sum += hit/10.0
    rec_sum += (hit / len(relevant))
    # ndcg
    def log2(x): return math.log(x)/math.log(2)
    dcg = 0.0
    for i, it in enumerate(recs[:10]):
        if it in relevant:
            dcg += 1.0 / log2(i+2)
    idcg = sum(1.0/log2(i+2) for i in range(min(10, len(relevant))))
    ndcg_sum += (dcg/idcg) if idcg>1e-12 else 0.0

if users==0:
    print('no evaluable users')
else:
    print(json.dumps({
        'users': users,
        'precision': prec_sum/users,
        'recall': rec_sum/users,
        'ndcg': ndcg_sum/users,
        'coverage': len(covered_items)/len(item_users)
    }, indent=2))
