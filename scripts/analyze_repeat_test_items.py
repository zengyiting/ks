import csv, math, json, os
from collections import defaultdict

CSV_PATH = 'docs/UserBehavior_cleaned.csv'
OUT_DIR = os.path.join('reports','offline-eval','debug')
if not os.path.exists(OUT_DIR): os.makedirs(OUT_DIR, exist_ok=True)

MAP_ACTION = {'view':1.6,'click':2.2,'cart':3.8,'favorite':4.5,'fav':4.5,'buy':5.0}

user_items = defaultdict(list)
with open(CSV_PATH, newline='', encoding='utf-8') as f:
    rdr = csv.reader(f)
    for row in rdr:
        if len(row) < 5:
            continue
        try:
            u = int(row[0])
            it = int(row[2])
            act = row[3].strip().lower() if row[3] else ''
            ts = int(row[4])
        except:
            continue
        score = MAP_ACTION.get(act)
        if score is None:
            continue
        user_items[u].append((ts, it, score))

total_test_relevant = 0
repeated_test_relevant = 0
users_all_repeated = 0
users_none_repeated = 0
users_with_relevant = 0

for u, lst in user_items.items():
    lst.sort()
    n = len(lst)
    if n < 2:
        continue
    test_count = math.ceil(n * 0.2)
    if test_count >= n: test_count = n-1
    train_count = n - test_count
    train = [it for (t,it,s) in lst[:train_count]]
    test = [(it,s) for (t,it,s) in lst[train_count:]]
    relevant = [it for (it,s) in test if s >= 1.5]
    if not relevant:
        continue
    users_with_relevant += 1
    total_test_relevant += len(relevant)
    rep = sum(1 for it in relevant if it in train)
    repeated_test_relevant += rep
    if rep == len(relevant):
        users_all_repeated += 1
    if rep == 0:
        users_none_repeated += 1

summary = {
    'users_with_relevant': users_with_relevant,
    'total_test_relevant_items': total_test_relevant,
    'repeated_test_relevant_items': repeated_test_relevant,
    'frac_repeated_test_relevant_items': (repeated_test_relevant/total_test_relevant) if total_test_relevant>0 else 0.0,
    'users_all_repeated': users_all_repeated,
    'users_none_repeated': users_none_repeated
}
print(json.dumps(summary, indent=2))
with open(os.path.join(OUT_DIR,'repeat_summary.json'),'w',encoding='utf-8') as fo:
    json.dump(summary, fo, ensure_ascii=False, indent=2)
