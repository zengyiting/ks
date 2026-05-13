# 推荐系统演示介绍与答辩文档（含算法细节）

## 1. 项目概述
本项目是一个基于 Spring Boot 的推荐系统工程，支持评分驱动与行为驱动两类推荐，并提供：

1. 在线推荐接口（`user/item/behavior/hybrid`）。
2. 行为采集与后台 CRUD 管理。
3. 离线评估（Precision@K、Recall@K、NDCG@K、Coverage）。
4. 报告导出（CSV/HTML/LaTeX/Summary）。
5. Redis 缓存与版本化失效。

目标不是“只做算法 demo”，而是“算法 + 工程可运行 + 可评估 + 可答辩”。

## 2. 系统架构
1. 数据层：`users / items / ratings`。
2. 算法层：`UserBasedCF`、`ItemBasedCF`、`SimilarityMetrics`。
3. 服务层：`RecommendationService`（融合、兜底、多样性、解释）。
4. 接口层：推荐、行为、管理、评估四类 API。
5. 工程层：Redis 缓存、定时预计算、自动化脚本与测试。

## 3. 推荐算法详解

### 3.1 相似度与基础工具（`SimilarityMetrics`）
1. 重叠数：`overlapCount(a,b)`，统计共同评分项目数量。
2. 收缩：`shrinkByOverlap(sim, overlap, alpha)`，公式：  
`sim' = sim * overlap / (overlap + alpha)`  
用于抑制重叠样本太少导致的虚高相似度。
3. Pearson：在共同评分维度上计算皮尔逊相关系数。
4. Cosine：在共同评分维度上计算余弦相似度（当前主流程主要用 Pearson）。

### 3.2 User-Based CF（`UserBasedCF`）
核心思想：找相似用户，用他们对候选物品的评分偏差预测目标用户评分。

步骤：
1. 对目标用户 `u` 与其他用户 `v` 计算相似度 `sim(u,v)`（Pearson + overlap 收缩）。
2. 仅保留 `sim(u,v) > 0` 且重叠评分数 `>= 2` 的邻居。
3. 对每个候选物品 `i` 累加：
`num(i) += sim(u,v) * (r(v,i) - mean(v))`
`den(i) += |sim(u,v)|`
4. 预测：
`pred(u,i) = mean(u) + num(i) / den(i)`
5. 按 `pred(u,i)` 降序取 Top-N。

### 3.3 Item-Based CF（`ItemBasedCF`）
核心思想：看用户已评分物品与候选物品是否相似。

步骤：
1. 构建 `item -> (user -> rating)` 反向矩阵。
2. 对用户已评分物品 `j` 与候选物品 `i` 计算 `sim(i,j)`（Pearson + overlap 收缩）。
3. 对候选物品 `i` 累加：
`num(i) += sim(i,j) * (r(u,j) - mean(j))`
`den(i) += |sim(i,j)|`
4. 预测：
`pred(u,i) = mean(i) + num(i) / den(i)`
5. 按预测分排序取 Top-N。

### 3.4 Behavior-Based（`behaviorBasedRecommendations`）
它本质是“先把显式评分转成隐式强度，再走 ItemCF”。

隐式强度构造（用户 `u` 对物品 `i`）：
`strength(u,i) = (0.4 + 0.6 * score(u,i)/5.0) * (0.85 + 0.15 * decay(u,i))`

其中：
1. `score(u,i)`：评分归一到 `[0,5]`。
2. `decay(u,i)`：时间衰减，见下节。
3. 再把 `strength` 矩阵送入 ItemCF 得到行为推荐结果。

### 3.5 时间衰减（`decayWeight`）
对于评分时间 `ratedAt` 与当前时间 `now`：
`days = max(0, days_between(ratedAt, now))`
`decay = 0.5 ^ (days / 30)`

含义：每 30 天信号强度衰减一半。

## 4. Hybrid 混合算法详解（重点）
Hybrid 在 `blendHybridRecommendations` 中由多个子信号融合组成。

### 4.1 候选池构建
1. `itemRecs`：ItemCF 候选（`poolSize = max(topN*5, 20)`）。
2. `userRecs`：UserCF 候选。
3. `popScore`：热门候选。
4. `associationScore`：预计算 item-item 关联候选。
5. 去除用户已评分物品。

### 4.2 各子信号如何计算

1. `itemRankScore`（来自 ItemCF 排名）  
按名次转分：  
`rankScore = 1 / (1 + rankIndex)`  

2. `userRankScore`（来自 UserCF 排名）  
同上，`1/(1+rankIndex)`。

3. `popScore`（热门度归一化）  
先由热门列表得到分值，再除以该批次最大值做 `0~1` 归一化。

4. `associationScore`（共现关联）  
对用户历史物品 `r` 的每个邻居 `c`：
`raw(c) += sim_assoc(r,c) * (rating(r)/5.0) * decay(r)`  
最终 `raw` 按最大值归一并截断到限制数量。

5. `contentSimilarityScore`（类目偏好匹配）  
先聚合用户在各 category 的偏好强度：
`pref(cat) += rating(item) * decay(item)`  
再对候选物品按所属类目取 `pref(cat)`，并除以 `maxPref` 做归一化。

6. `preferredCategoryBoost`（强偏好类目加成）  
按类目统计用户“加权平均分 + 交互量”，只保留：
`avg >= 4.0 且 count >= 2.0` 的类目强偏好。  
得到 `boost in [0,1]`。

### 4.3 动态权重（`dynamicWeights`）
按用户评分数量分段：

1. `ratedCount < 6`：`(item=0.25, user=0.10, pop=0.20, assoc=0.10, content=0.35)`
2. `6 <= ratedCount < 18`：`(0.35, 0.20, 0.15, 0.15, 0.15)`
3. `ratedCount >= 18`：`(0.42, 0.23, 0.15, 0.12, 0.08)`

### 4.4 最终融合公式
对候选物品 `x`：

`base(x) = w_item*itemRankScore(x) + w_user*userRankScore(x) + w_pop*popScore(x) + w_assoc*associationScore(x) + w_content*contentSimilarityScore(x)`

`final(x) = base(x) * (1 + 0.25 * preferredCategoryBoost(x))`

保留 `final(x) > 0`，按分数排序取 Top-N。

### 4.5 兜底与多样性
1. 兜底一：热门兜底（`popularFallback`）。
2. 兜底二：目录兜底（`catalogFallback`），考虑类目偏好并给轻量分。
3. 多样性重排：  
`adjusted = score / (1 + categoryCount * 0.35)`  
减少单一类目过度堆积。

## 5. 缓存机制说明（现状）

### 5.1 缓存位置与入口
1. 缓存入口：`RecommendationService.recommendForUserWithReason` 使用 `@Cacheable`。
2. 缓存名：`recommendationResults`。
3. Key 生成器：`recommendationCacheKeyGenerator`。

### 5.2 版本化缓存失效
Key 结构：
`userId:ALGO:topN:g{globalVersion}:u{userVersion}`

失效策略：
1. 用户行为更新（单条/批量） -> `invalidateUser(s)`，提升用户版本。
2. 管理端改动（用户/商品 CRUD） -> `invalidateAll()`，提升全局版本。

优点：避免高频 `DEL` 大量 Redis 键，使用逻辑版本失效。

## 6. 本次缓存优化（已落地）

### 6.1 已做优化
1. Redis 配置细化（`RedisCacheConfig`）  
   - 禁止缓存空值：`disableCachingNullValues()`。  
   - 统一 key 前缀：`recommend:{cacheName}:`。  
   - 推荐结果缓存单独 TTL：5 分钟（默认仍为 10 分钟）。  
   - 开启 `transactionAware()`，提升事务一致性。

2. 版本映射内存治理（`RecommendationCacheService`）  
   - `invalidateAll()` 时清空用户版本映射，避免长期堆积。  
   - 用户版本表超过阈值时自动裁剪（100000 -> 50000），避免极端增长。

### 6.2 为什么这是有效优化
1. 推荐结果更新频率通常高于静态缓存，单独 TTL 更合理。
2. 逻辑失效模型若不清理用户版本表，长时间运行会累积内存。
3. 优化不改变接口语义，也不影响现有调用方。

## 7. 演示流程（8~10 分钟）

### 7.1 启动服务
```bash
mvn spring-boot:run
```

若 8080 被占用：
```powershell
$env:SERVER_PORT='18080'; mvn spring-boot:run
```

### 7.2 页面入口
1. `/` 推荐主页
2. `/behavior.html` 行为采集
3. `/users.html` 用户管理
4. `/items.html` 商品管理

### 7.3 演示顺序
1. 先在推荐页对比四种算法结果差异。
2. 到行为页记录 `click/favorite/cart`，回推荐页看结果变化。
3. 在管理页增删改商品/用户，验证系统稳定性。
4. 调离线评估并展示导出报告。

## 8. 离线评估讲解模板
接口：
1. `GET /api/evaluations/offline?k=10&testRatio=0.2&relevance=4.0`
2. `GET /api/evaluations/offline/csv?k=10&testRatio=0.2&relevance=4.0`

脚本：
```bash
.\scripts\run-export-eval.cmd -BaseUrl http://localhost:8080 -Ks 5,10,20,30
```

重点解释：
1. Precision@K：前 K 个推荐里“命中相关项”的比例。
2. Recall@K：用户相关项里“被前 K 推荐覆盖”的比例。
3. NDCG@K：命中位置越靠前得分越高。
4. Coverage：被推荐系统覆盖到的物品占比。

## 9. 本次验证结果（交付状态）
1. 单元/集成测试：`mvn test` 通过。
2. 实际启动：应用已成功启动（`local` profile，Tomcat 18080）。
3. 缓存优化代码已落地并通过编译测试。

## 10. 答辩高频问题（建议）
1. 为什么 Hybrid 比单算法更稳？  
因为融合了多视角信号，能在稀疏场景降低单一路径失效概率。

2. 为什么还要兜底？  
现实数据稀疏时，CF 候选不足常见，兜底保证“有结果可用”。

3. 为什么做动态权重？  
用户历史少时更依赖内容/热门，历史多时更依赖协同信号。

4. 缓存为何不用直接删 key？  
高频写入场景下，版本化失效更稳、成本更低。

5. 下一步可优化什么？  
在线 A/B、分层召回与重排、特征工程、自动调参、解释性可视化。
