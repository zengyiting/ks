#!/usr/bin/env python3
import csv, math, json, statistics
from collections import defaultdict

path = 'docs/UserBehavior_cleaned.csv'
user_items = {}
with open(path, newline='', encoding='utf-8') as f:
    rdr = csv.reader(f)
    for row in rdr:
        if len(row) < 5:
            continue
        try:
            user = int(row[0])
            item = int(row[2])
            ts = int(row[4])
        except:
            continue
        user_items.setdefault(user, []).append((ts, item))

# sort by timestamp
for u in list(user_items.keys()):
    user_items[u].sort()

# split train/test
train = {}
test = {}
test_ratio = 0.2
for u, lst in user_items.items():
    n = len(lst)
    items = [it for _, it in lst]
    if n < 2:
        train[u] = set(items)
        test[u] = set()
        continue
    test_count = math.ceil(n * test_ratio)
    if test_count >= n:
        test_count = n - 1
    train_count = n - test_count
    train[u] = set(items[:train_count])
    test[u] = set(items[train_count:])

# stats
total_users = len(user_items)
train_counts = [len(train[u]) for u in user_items]
avg_train = sum(train_counts)/total_users
median_train = statistics.median(train_counts)
frac_small_train = sum(1 for c in train_counts if c < 2)/total_users

# item->users map
item_users = defaultdict(set)
for u, items in train.items():
    for it in items:
        item_users[it].add(u)

# neighbor count with overlap>=1
neighbor_count1 = {}
for u, items in train.items():
    neigh = set()
    for it in items:
        neigh |= item_users.get(it, set())
    neigh.discard(u)
    neighbor_count1[u] = len(neigh)

# co-occurrence counts for pairs
co = defaultdict(int)
for it, users in item_users.items():
    users = list(users)
    L = len(users)
    for i in range(L):
        a = users[i]
        for j in range(i+1, L):
            b = users[j]
            if a < b:
                co[(a,b)] += 1
            else:
                co[(b,a)] += 1

# neighbor count with overlap>=2
neighbor_count2 = {u:0 for u in train}
for (a,b), cnt in co.items():
    if cnt >= 2:
        neighbor_count2[a] += 1
        neighbor_count2[b] += 1

frac_neigh1 = sum(1 for u in user_items if neighbor_count1.get(u,0) > 0) / total_users
frac_neigh2 = sum(1 for u in user_items if neighbor_count2.get(u,0) > 0) / total_users
avg_neighbor1 = sum(neighbor_count1.values()) / total_users
avg_neighbor2 = sum(neighbor_count2.values()) / total_users

summary = {
    'total_users': total_users,
    'avg_train_count': avg_train,
    'median_train_count': median_train,
    'frac_users_train_lt_2': frac_small_train,
    'frac_users_with_overlap_ge_1': frac_neigh1,
    'frac_users_with_overlap_ge_2': frac_neigh2,
    'avg_neighbor_count_overlap_ge_1': avg_neighbor1,
    'avg_neighbor_count_overlap_ge_2': avg_neighbor2,
    'train_size': sum(train_counts),
}

# 额外：测试集物品在训练集的覆盖情况
all_train_items = set()
for s in train.values():
    all_train_items |= set(s)

unique_test_items = set()
for s in test.values():
    unique_test_items |= set(s)

seen_in_train = sum(1 for it in unique_test_items if it in all_train_items)
total_test_unique = len(unique_test_items)
frac_test_seen = (seen_in_train / total_test_unique) if total_test_unique > 0 else 0.0

summary.update({
    'unique_test_items': total_test_unique,
    'unique_test_items_seen_in_train': seen_in_train,
    'frac_test_items_seen_in_train': frac_test_seen
})

print(json.dumps(summary, indent=2))
