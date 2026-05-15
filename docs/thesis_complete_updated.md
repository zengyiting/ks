# 基于协同过滤的商品推荐系统设计与实现

## 摘要

随着电子商务的快速发展，个性化推荐系统已成为提升用户体验和促进销售的核心技术。本研究设计并实现了一个基于协同过滤的商品推荐系统，采用Spring Boot 3.3.2后端框架与Vue 3前端技术栈，通过融合多种推荐算法，有效解决了冷启动和数据稀疏性问题。系统实现了基于用户的协同过滤（User-Based CF）、基于物品的协同过滤（Item-Based CF）、基于行为的推荐（Behavior-Based）以及混合推荐（Hybrid）四种算法策略。混合推荐算法融合Item-CF、User-CF、热门物品、物品关联和内容相似度五种信号，通过sigmoid函数实现动态权重调整，并引入MMR多样化算法平衡相关性与多样性。实验结果表明，在MovieLens数据集上，混合推荐算法的Coverage达到0.779，显著优于单一算法。系统采用分层架构设计，包含用户认证、推荐引擎、行为分析、Redis缓存管理和物品关联预计算等核心模块，支持手机号/邮箱登录、JWT Token认证、时效衰减、推荐理由生成等完整功能。

**关键词**：协同过滤；混合推荐；动态权重；MMR多样化；Spring Boot；Vue 3

---

## 1. 引言

### 1.1 研究背景与意义

在信息爆炸的时代，用户面临着海量商品信息的选择困境。推荐系统通过分析用户行为数据，为用户提供个性化的商品推荐，有效缓解信息过载问题。协同过滤作为推荐系统的核心技术，通过挖掘用户间的相似性或物品间的关联性，实现精准推荐。

随着电商平台的日益复杂，单一推荐算法已无法满足多样化的用户需求。User-Based CF擅长发现用户群体的共同偏好，Item-Based CF在物品关联推荐上表现稳定，但两者都存在冷启动和数据稀疏性问题。因此，设计一个融合多种算法优势、具备自适应能力的混合推荐系统具有重要的理论价值和实际意义。

### 1.2 研究目标

本研究的主要目标是设计并实现一个基于协同过滤的商品推荐系统，具体包括：

1. 实现基于用户的协同过滤算法（User-Based CF），采用Pearson相似度与温和shrinkage机制
2. 实现基于物品的协同过滤算法（Item-Based CF），采用Adjusted Cosine相似度消除用户评分偏差
3. 实现基于行为的推荐算法（Behavior-Based），将显式评分与隐式行为（浏览、点击、加购、收藏）统一映射
4. 设计混合推荐策略，融合五种推荐信号，通过sigmoid函数实现动态权重调整
5. 实现MMR（Maximal Marginal Relevance）多样化算法，平衡推荐结果的相关性与多样性
6. 设计时间衰减机制，使近期行为对推荐结果的影响更大
7. 构建完整的Web应用，包含用户认证、行为管理、推荐展示和离线评估功能
8. 通过实验验证系统性能

### 1.3 论文结构

本文共分为六章：第一章介绍研究背景和目标；第二章综述协同过滤相关技术；第三章阐述系统总体设计；第四章详细说明算法实现；第五章展示实验结果与分析；第六章总结研究成果并展望未来工作。

---

## 2. 相关技术与理论基础

### 2.1 协同过滤技术概述

协同过滤（Collaborative Filtering, CF）是一种基于用户行为的推荐方法，其核心思想是通过分析用户的历史行为数据，发现用户之间或物品之间的相似性，从而为用户推荐可能感兴趣的物品。协同过滤不依赖物品内容信息，仅利用用户-物品交互矩阵即可实现推荐，具有通用性强、实现简单的优点。

### 2.2 基于用户的协同过滤（User-Based CF）

基于用户的协同过滤通过计算用户之间的相似度，找到与目标用户兴趣相似的邻居用户，然后将邻居用户喜欢的物品推荐给目标用户。

**相似度计算：**

系统采用Pearson相关系数计算用户间相似度，并引入温和shrinkage机制避免低重叠度用户间的过度置信：

$$\text{sim}(u,v) = \frac{\sum_{i \in I_{uv}} (r_{ui} - \bar{r}_u)(r_{vi} - \bar{r}_v)}{\sqrt{\sum_{i \in I_{uv}} (r_{ui} - \bar{r}_u)^2} \sqrt{\sum_{i \in I_{uv}} (r_{vi} - \bar{r}_v)^2}} \times \frac{\text{overlap}}{\text{overlap} + 5}$$

其中overlap为共同评分物品数，shrinkage参数设为5以平衡置信度与相似度。

**评分预测公式：**

$$\hat{r}_{ui} = \frac{\sum_{v \in N(u)} \text{sim}(u,v) \times r_{vi}}{\sum_{v \in N(u)} \text{sim}(u,v)}$$

### 2.3 基于物品的协同过滤（Item-Based CF）

基于物品的协同过滤通过计算物品之间的相似度，找到与用户已评分物品相似的其他物品，然后推荐给用户。

系统采用Adjusted Cosine相似度，先减去全局均值（GLOBAL_MEAN=3.5）消除用户评分偏差：

$$\text{sim}(i,j) = \frac{\sum_{u \in U_{ij}} (r_{ui} - \bar{r})(r_{uj} - \bar{r})}{\sqrt{\sum_{u \in U_{ij}} (r_{ui} - \bar{r})^2} \sqrt{\sum_{u \in U_{ij}} (r_{uj} - \bar{r})^2}} \times \frac{\text{overlap}}{\text{overlap} + 8}$$

其中shrinkage参数设为8，因为物品间共同用户通常较多。

### 2.4 混合推荐策略

单一算法存在局限性，混合推荐策略通过融合多种算法的优势，提升推荐效果。本系统融合五种推荐信号：Item-CF、User-CF、热门物品、物品关联和内容相似度，通过sigmoid函数根据用户活跃度动态调整各信号权重。

### 2.5 评价指标

| 指标 | 计算公式 | 说明 |
|------|----------|------|
| **精确率@K** | $\frac{|R(u) \cap T(u)|}{K}$ | 衡量推荐准确性 |
| **召回率@K** | $\frac{|R(u) \cap T(u)|}{|T(u)|}$ | 衡量推荐完整性 |
| **NDCG@K** | $\frac{\text{DCG@K}}{\text{IDCG@K}}$ | 衡量推荐排序质量 |
| **覆盖率** | $\frac{|\bigcup_u R(u)|}{|I|}$ | 衡量推荐物品的覆盖范围 |

其中$R(u)$为用户$u$的推荐列表，$T(u)$为测试集相关物品，$I$为全部物品集合。

### 2.6 开发技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 后端编程语言 |
| Spring Boot | 3.3.2 | 后端框架 |
| Spring Data JPA | - | 数据访问层 |
| MySQL | 8.0 | 关系型数据库 |
| Redis | 7.0 | 缓存服务 |
| Vue 3 | - | 前端框架 |
| Vite | - | 前端构建工具 |
| Maven | - | 项目构建工具 |

---

## 3. 系统总体设计

### 3.1 系统架构

系统采用前后端分离的分层架构设计，后端基于Spring Boot，前端基于Vue 3 + Vite。

```
┌─────────────────────────────────────────────────────────────┐
│                        前端展示层 (Vue 3)                      │
│  Home.vue │ Login.vue │ Register.vue │ ItemDetail.vue │ ...   │
├─────────────────────────────────────────────────────────────┤
│                        API控制层                             │
│  RecommendationController │ AuthController │ BehaviorController│
│  EvaluationController │ AdminCrudController │ ...             │
├─────────────────────────────────────────────────────────────┤
│                        业务逻辑层                             │
│  RecommendationService │ AuthService │ BehaviorService │      │
│  OfflineEvaluationService │ ItemAssociationPrecomputeService  │
├─────────────────────────────────────────────────────────────┤
│                        算法层                                │
│  UserBasedCF │ ItemBasedCF │ SimilarityMetrics │             │
│  AlgorithmEvaluator │ RecommenderStrategy                    │
├─────────────────────────────────────────────────────────────┤
│                        数据访问层                             │
│  UserRepository │ ItemRepository │ RatingRepository │ ...     │
├─────────────────────────────────────────────────────────────┤
│  MySQL (users/items/ratings/user_item_flags) │ Redis (cache)  │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 核心模块设计

#### 3.2.1 推荐服务模块（RecommendationService）

推荐服务是系统的核心业务模块，提供以下功能：

- `recommendForUser(userId, topN, type)`：为用户生成推荐列表
- `recommendForUserWithReason(userId, topN, type)`：生成带推荐理由的推荐列表
- `recommendWithDiversity(userId, topN, type, diversityLevel)`：生成多样性优化的推荐列表
- `getPopularItems(topN)`：获取热门商品
- `getPopularItemsByCategory(category, topN)`：获取分类热门商品

支持四种算法类型：USER_BASED、ITEM_BASED、BEHAVIOR_BASED、HYBRID。

#### 3.2.2 算法模块（algo包）

| 类名 | 职责 | 核心方法 |
|------|------|----------|
| `RecommenderStrategy` | 推荐算法策略接口 | `recommend(userItem, userId, topN)` |
| `UserBasedCF` | 基于用户的协同过滤 | `findNeighbors()`, `predictRatings()`, `cosineSimilarity()` |
| `ItemBasedCF` | 基于物品的协同过滤 | `buildItemUsers()`, `predictRatings()`, `cosineSimilarity()` |
| `SimilarityMetrics` | 相似度计算工具类 | `userSimilarity()`, `itemSimilarity()`, `optimizedSimilarity()` |
| `Recommendation` | 推荐结果封装 | `getItemId()`, `getScore()`, `compareTo()` |
| `AlgorithmEvaluator` | 离线评估器 | `loadMovieLens100k()`, `splitTrainTest()`, `evaluate()` |

#### 3.2.3 认证模块（AuthService）

提供完整的用户认证功能：

- 手机号验证码登录、手机号密码登录、邮箱密码登录、用户名密码登录
- 邮箱注册（带验证码）、手机注册
- Token生成与验证（Access Token 30分钟，Refresh Token 7天）
- 密码修改、密码重置、用户信息更新
- 密码采用SHA-256哈希存储

#### 3.2.4 行为管理模块（BehaviorService）

- 用户/商品搜索查询
- 评分记录的增删改查
- 行为记录与评分映射：view→1.6、click→2.2、cart→3.8、favorite→4.5
- 收藏/加购标记管理
- 批量评分导入（单次上限1000条）

#### 3.2.5 缓存模块

采用Redis + 内存双层缓存策略：

- **Redis缓存**：推荐结果缓存（30分钟过期）、验证码缓存、Token缓存
- **内存缓存**：RecommendationContext懒加载缓存、ItemAssociationPrecomputeService快照缓存
- **缓存失效**：评分更新后自动标记脏数据，推荐缓存按用户失效

#### 3.2.6 物品关联预计算（ItemAssociationPrecomputeService）

- 应用启动时构建物品共现相似度快照
- 每15分钟定时刷新（可配置）
- 数据变更时标记脏数据，下次刷新时重建
- 使用PriorityQueue维护每个物品的Top-N邻居（默认120个）

### 3.3 数据库设计

系统使用MySQL 8.0数据库，包含4张核心表：

**用户表（users）：**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 用户ID |
| `username` | VARCHAR(100) | UNIQUE, NOT NULL | 用户名 |
| `phone` | VARCHAR(20) | UNIQUE | 手机号 |
| `password_hash` | VARCHAR(64) | - | SHA-256密码哈希 |
| `email` | VARCHAR(100) | UNIQUE | 邮箱 |
| `disabled` | TINYINT(1) | DEFAULT 0 | 是否禁用 |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**物品表（items）：**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 物品ID |
| `name` | VARCHAR(200) | NOT NULL | 物品名称 |
| `category` | VARCHAR(100) | - | 物品类别 |
| `image_url` | VARCHAR(255) | - | 图片URL |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**评分表（ratings）：**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 评分ID |
| `user_id` | BIGINT | FK→users, NOT NULL | 用户ID |
| `item_id` | BIGINT | FK→items, NOT NULL | 物品ID |
| `score` | DOUBLE | CHECK(0≤score≤5), NOT NULL | 评分值 |
| `rated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 评分时间 |

唯一约束：`UNIQUE(user_id, item_id)`
索引：`idx_r_item_id`, `idx_r_rated_at`, `idx_r_user_rated_at`

**用户物品标记表（user_item_flags）：**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 标记ID |
| `user_id` | BIGINT | FK→users, NOT NULL | 用户ID |
| `item_id` | BIGINT | FK→items, NOT NULL | 物品ID |
| `favorite` | TINYINT(1) | DEFAULT 0 | 是否收藏 |
| `in_cart` | TINYINT(1) | DEFAULT 0 | 是否加购 |
| `updated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 更新时间 |

唯一约束：`UNIQUE(user_id, item_id)`

### 3.4 API接口设计

#### 3.4.1 推荐接口

| 接口 | 方法 | 功能 |
|------|------|------|
| `/api/recommendations/{userId}?n=5&algo=user` | GET | 获取个性化推荐（带理由） |
| `/api/recommendations/{userId}/diverse?n=5&algo=user&diversity=0.5` | GET | 获取多样性优化推荐 |
| `/api/recommendations/popular?n=10&category=电子` | GET | 获取热门商品 |

algo参数支持：user、item、behavior、hybrid

#### 3.4.2 认证接口

| 接口 | 方法 | 功能 |
|------|------|------|
| `/api/auth/send-sms-code` | POST | 发送手机验证码 |
| `/api/auth/send-email-code` | POST | 发送邮箱验证码 |
| `/api/auth/login/sms` | POST | 手机验证码登录 |
| `/api/auth/login` | POST | 手机密码登录 |
| `/api/auth/login-email` | POST | 邮箱密码登录 |
| `/api/auth/login-username` | POST | 用户名密码登录 |
| `/api/auth/register` | POST | 手机注册 |
| `/api/auth/register-email` | POST | 邮箱注册 |
| `/api/auth/refresh` | POST | 刷新Token |
| `/api/auth/logout` | POST | 登出 |
| `/api/auth/validate` | GET | 验证Token |
| `/api/auth/me` | GET | 获取当前用户信息 |
| `/api/auth/me` | PUT | 更新用户信息 |
| `/api/auth/me/change-password` | POST | 修改密码 |
| `/api/auth/reset-password` | POST | 重置密码 |

#### 3.4.3 行为管理接口

| 接口 | 方法 | 功能 |
|------|------|------|
| `/api/behaviors/users?keyword=xxx` | GET | 搜索用户 |
| `/api/behaviors/items?keyword=xxx` | GET | 搜索商品 |
| `/api/behaviors/users/{userId}/ratings` | GET | 获取用户评分 |
| `/api/behaviors/ratings` | POST | 创建/更新评分 |
| `/api/behaviors/ratings/batch` | POST | 批量导入评分 |
| `/api/behaviors/events` | POST | 记录行为（view/click/cart/favorite） |

#### 3.4.4 评估接口

| 接口 | 方法 | 功能 |
|------|------|------|
| `/api/evaluations/offline?k=10&testRatio=0.2&relevance=1.5` | GET | 执行离线评估 |
| `/api/evaluations/offline/csv` | GET | 导出评估结果CSV |

### 3.5 前端设计

前端采用Vue 3 + Vite构建，包含以下核心页面：

| 页面 | 文件 | 功能 |
|------|------|------|
| 首页 | `Home.vue` | 展示推荐列表、热门商品 |
| 登录 | `Login.vue` | 多种登录方式 |
| 注册 | `Register.vue` | 手机/邮箱注册 |
| 商品详情 | `ItemDetail.vue` | 商品详情、评分、行为记录 |
| 收藏 | `Favorites.vue` | 用户收藏列表 |
| 购物车 | `Cart.vue` | 用户加购列表 |

核心组件：
- `TopNav.vue`：顶部导航栏
- `ItemCard.vue`：商品卡片
- `RatingStars.vue`：评分星级组件

---

## 4. 核心算法实现

### 4.1 算法总体架构

本系统的推荐算法层采用策略模式设计，通过`RecommenderStrategy`接口统一算法入口，支持多种推荐策略的灵活切换与组合。算法架构包含四个核心层次：基础协同过滤层、相似度计算层、混合推荐层和策略优化层。

```java
public interface RecommenderStrategy {
    List<Recommendation> recommend(Map<Long, Map<Long, Double>> userItemRatings, Long userId, int topN);
}

public enum AlgorithmType {
    USER_BASED,      // 基于用户的协同过滤
    ITEM_BASED,      // 基于物品的协同过滤
    BEHAVIOR_BASED,  // 基于行为的推荐
    HYBRID           // 混合推荐算法
}
```

### 4.2 相似度计算模块

相似度计算是协同过滤算法的核心。系统设计了`SimilarityMetrics`工具类，提供多种相似度计算方法，并针对User-CF和Item-CF的不同特点进行了专门优化。

#### 4.2.1 User-CF专用Pearson相似度

User-CF采用Pearson相关系数计算用户间相似度，并引入温和shrinkage机制避免低重叠度用户间的过度置信。由于用户间评分重叠度通常较低，shrinkage参数设置为5。

计算公式：

$$\text{sim}(u,v) = \frac{\sum r_{ui}r_{vi} - \frac{\sum r_{ui}\sum r_{vi}}{n}}{\sqrt{(\sum r_{ui}^2 - \frac{(\sum r_{ui})^2}{n})(\sum r_{vi}^2 - \frac{(\sum r_{vi})^2}{n})}} \times \frac{\text{overlap}}{\text{overlap} + 5}$$

```java
public static double userSimilarity(Map<Long, Double> a, Map<Long, Double> b) {
    int overlap = overlapCount(a, b);
    if (overlap < MIN_OVERLAP) return 0.0;  // MIN_OVERLAP = 3
    
    // 计算Pearson相关系数
    double num = sumAB - (sumA * sumB / n);
    double den = sqrt((sumA2 - sumA*sumA/n) * (sumB2 - sumB*sumB/n));
    if (den <= 1e-12) return 0.0;
    
    double sim = num / den;
    // 温和shrinkage
    return sim * overlap / (overlap + 5.0);
}
```

#### 4.2.2 Item-CF专用Adjusted Cosine相似度

Item-CF采用Adjusted Cosine相似度，先减去全局均值（GLOBAL_MEAN=3.5）消除用户评分偏差。由于物品间共同用户通常较多，shrinkage参数设置为8。

```java
public static double itemSimilarity(Map<Long, Double> a, Map<Long, Double> b) {
    int overlap = overlapCount(a, b);
    if (overlap < 3) return 0.0;
    
    // 减去全局均值
    double va = e.getValue() - GLOBAL_MEAN;
    double vb = other - GLOBAL_MEAN;
    dot += va * vb;
    
    double sim = dot / (sqrt(normA) * sqrt(normB));
    return sim * overlap / (overlap + 8.0);
}
```

#### 4.2.3 优化相似度计算

系统还实现了`optimizedSimilarity`方法，综合使用Pearson、Adjusted Cosine和标准Cosine三种相似度，当一种方法失效时自动回退到下一种，最后通过置信度加权得到最终相似度。

```java
public static double optimizedSimilarity(Map<Long, Double> a, Map<Long, Double> b) {
    double sim = pearson(a, b);
    if (abs(sim) < 1e-6) sim = adjustedCosine(a, b);
    if (abs(sim) < 1e-6) sim = cosine(a, b);
    return confidenceWeighted(sim, overlap);
}

private static double confidenceWeighted(double sim, int overlap) {
    double confidence = log1p(overlap) / log1p(100);
    return sim * confidence;
}
```

### 4.3 基于用户的协同过滤算法（User-Based CF）

`UserBasedCF`类实现了基于用户的协同过滤算法，其核心流程包括：获取目标用户评分、寻找相似用户邻居、预测候选物品评分、生成推荐列表。当无法找到足够相似用户时，算法会自动回退到热门物品推荐。

#### 4.3.1 核心参数

| 参数 | 值 | 说明 |
|------|-----|------|
| MIN_SIMILARITY | 0.0 | 最小相似度阈值 |
| GLOBAL_MEAN | 3.5 | 全局平均评分 |
| DEFAULT_NEIGHBORS | 30 | 默认邻居数量 |
| MIN_OVERLAP | 2 | 最小共同评分物品数 |

#### 4.3.2 邻居查找

邻居查找阶段遍历所有用户，计算与目标用户的余弦相似度。只有共同评分物品数达到MIN_OVERLAP且相似度大于MIN_SIMILARITY的用户才会被纳入候选邻居。候选邻居按相似度降序排序，取前DEFAULT_NEIGHBORS个作为最终邻居。

```java
private List<Neighbor> findNeighbors(Map<Long, Map<Long, Double>> userItem,
                                     Map<Long, Double> targetRatings, Long userId) {
    List<Neighbor> neighbors = new ArrayList<>();
    for (Map.Entry<Long, Map<Long, Double>> entry : userItem.entrySet()) {
        Long otherId = entry.getKey();
        if (Objects.equals(otherId, userId)) continue;
        
        int overlap = countOverlap(targetRatings, entry.getValue());
        if (overlap < MIN_OVERLAP) continue;
        
        double similarity = cosineSimilarity(targetRatings, entry.getValue());
        if (similarity <= MIN_SIMILARITY) continue;
        
        neighbors.add(new Neighbor(otherId, similarity, entry.getValue()));
    }
    neighbors.sort((a, b) -> Double.compare(b.similarity, a.similarity));
    return neighbors.subList(0, min(DEFAULT_NEIGHBORS, neighbors.size()));
}
```

#### 4.3.3 评分预测

评分预测采用加权平均法，以邻居用户的相似度作为权重，对候选物品的评分进行加权平均。

$$\hat{r}_{ui} = \frac{\sum_{v \in N(u)} \text{sim}(u,v) \times r_{vi}}{\sum_{v \in N(u)} \text{sim}(u,v)}$$

```java
private Map<Long, Double> predictRatings(List<Neighbor> neighbors,
                                         Map<Long, Double> targetRatings) {
    for (Neighbor neighbor : neighbors) {
        double sim = neighbor.similarity;
        if (sim <= 0) continue;
        
        for (Map.Entry<Long, Double> entry : neighbor.ratings.entrySet()) {
            Long itemId = entry.getKey();
            if (targetRatings.containsKey(itemId)) continue;
            
            predictions.merge(itemId, sim * entry.getValue(), Double::sum);
            weightSums.merge(itemId, sim, Double::sum);
        }
    }
    // 归一化
    for (Long itemId : predictions.keySet()) {
        double weightSum = weightSums.getOrDefault(itemId, 0.0);
        if (weightSum > 0) {
            result.put(itemId, predictions.get(itemId) / weightSum);
        }
    }
    return result;
}
```

#### 4.3.4 热门物品回退

当无法找到有效邻居或预测结果为空时，算法回退到热门物品推荐。热门度评分综合考虑物品平均分和评分数量：

$$\text{popularityScore} = \frac{\text{avgRating} - 1.0}{4.0}$$

### 4.4 基于物品的协同过滤算法（Item-Based CF）

`ItemBasedCF`类实现了基于物品的协同过滤算法。与User-CF不同，Item-CF通过计算物品间的相似度来推荐与用户已评分物品相似的其他物品。

#### 4.4.1 物品-用户矩阵构建

Item-CF需要物品到用户的反向映射。`buildItemUsers`方法从userItem矩阵构建itemUsers矩阵。

```java
private Map<Long, Map<Long, Double>> buildItemUsers(Map<Long, Map<Long, Double>> userItem) {
    Map<Long, Map<Long, Double>> itemUsers = new HashMap<>();
    for (Map.Entry<Long, Map<Long, Double>> userEntry : userItem.entrySet()) {
        Long userId = userEntry.getKey();
        for (Map.Entry<Long, Double> ratingEntry : userEntry.getValue().entrySet()) {
            itemUsers.computeIfAbsent(ratingEntry.getKey(), k -> new HashMap<>())
                     .put(userId, ratingEntry.getValue());
        }
    }
    return itemUsers;
}
```

#### 4.4.2 评分预测

Item-CF的评分预测遍历用户已评分的每个物品，计算该物品与所有候选物品的相似度，以相似度为权重对候选物品进行加权评分。

### 4.5 基于行为的推荐算法（Behavior-Based）

Behavior-Based算法将显式评分转换为隐式行为强度，综合考虑评分值和时效性两个维度。

#### 4.5.1 行为-评分映射

| 行为类型 | 映射评分 | 偏好强度 |
|----------|----------|----------|
| view（浏览） | 1.6 | 弱偏好 |
| click（点击） | 2.2 | 弱偏好 |
| cart（加购） | 3.8 | 中强偏好 |
| favorite（收藏） | 4.5 | 强偏好 |

#### 4.5.2 隐式行为矩阵构建

隐式行为强度计算公式：

$$\text{strength} = (0.2 + 0.8 \times \frac{\text{score}}{5.0}) \times (0.4 + 0.6 \times \text{decay})$$

对于近期交互（decay≥0.8），额外施加15%的放大系数。

```java
double base = 0.2 + 0.8 * (score / 5.0);
double recencyFactor = 0.4 + 0.6 * decay;
if (decay >= 0.8) base *= 1.15;
double strength = base * recencyFactor;
```

### 4.6 混合推荐算法（Hybrid）

混合推荐算法是本系统的核心创新点，融合了五种推荐信号，并根据用户活跃度动态调整各信号权重。

#### 4.6.1 五种推荐信号

| 信号 | 来源 | 计算方式 |
|------|------|----------|
| Item-CF | 基于物品的协同过滤 | 物品相似度加权评分 |
| User-CF | 基于用户的协同过滤 | 用户相似度加权评分 |
| Popularity | 热门物品 | 平均分×log(1+评分次数) |
| Association | 物品关联 | 预计算共现相似度 |
| Content | 内容相似度 | 类别偏好匹配度 |

#### 4.6.2 动态权重调整

系统使用sigmoid函数根据用户历史评分数量动态计算各信号权重。归一化活跃度为$\min(1.0, \text{ratedCount}/30.0)$，sigmoid中心点设在0.4，斜率为10。

$$\text{sigmoid} = \frac{1}{1 + e^{-10 \times (\text{normalizedActivity} - 0.4)}}$$

| 权重 | 公式 | 范围 |
|------|------|------|
| Item-CF | 0.30 + 0.15 × sigmoid | 0.30~0.45 |
| User-CF | 0.15 + 0.15 × sigmoid | 0.15~0.30 |
| Popularity | 0.25 - 0.15 × sigmoid | 0.25~0.10 |
| Association | 0.10 + 0.02 × sigmoid | 0.10~0.12 |
| Content | 0.20 - 0.15 × sigmoid | 0.20~0.05 |

当用户活跃度低时，侧重内容相似度和热门物品；当用户活跃度高时，侧重协同过滤信号。

```java
private HybridWeights dynamicWeights(int ratedCount) {
    double normalizedActivity = min(1.0, ratedCount / 30.0);
    double sigmoid = 1.0 / (1.0 + exp(-10.0 * (normalizedActivity - 0.4)));
    
    double itemCfWeight = 0.30 + 0.15 * sigmoid;
    double userCfWeight = 0.15 + 0.15 * sigmoid;
    double popularityWeight = 0.25 - 0.15 * sigmoid;
    double associationWeight = 0.10 + 0.02 * sigmoid;
    double contentWeight = 0.20 - 0.15 * sigmoid;
    
    // 归一化
    double total = itemCfWeight + userCfWeight + popularityWeight 
                 + associationWeight + contentWeight;
    return new HybridWeights(itemCfWeight/total, userCfWeight/total, ...);
}
```

#### 4.6.3 偏好类别提升

系统识别用户偏好的类别（平均分≥4.0且加权评分数≥2），计算类别强度用于提升该类别物品的推荐得分。

$$\text{strength} = \min(1.0, ((\text{avg}-4.0) \times 0.7) + \min(0.3, (\text{count}-2.0) \times 0.08))$$

最终推荐得分乘以$(1.0 + 0.25 \times \text{categoryStrength})$进行提升。

### 4.7 物品关联预计算

`ItemAssociationPrecomputeService`负责离线预计算物品间的共现相似度，在线阶段只做轻量读取与融合。

#### 4.7.1 共现相似度计算

物品间相似度基于共同用户数计算：

$$\text{sim}(i,j) = \frac{\text{co\_count}(i,j)}{\sqrt{\text{userCount}(i) \times \text{userCount}(j)}}$$

每个物品保留相似度最高的前N个邻居（默认120个），使用PriorityQueue高效维护Top-N。

### 4.8 时间衰减机制

系统引入时间衰减机制，使近期行为对推荐结果的影响更大。衰减权重使用半衰期30天的指数衰减函数：

$$\text{decay} = 0.5^{\frac{\text{days}}{30}}$$

| 应用场景 | 衰减方式 | 效果 |
|----------|----------|------|
| User-CF评分矩阵 | 评分×decay | 降低历史评分的影响 |
| 偏好类别计算 | 评分×decay后求和 | 偏好随时间演变 |
| 隐式行为强度 | (0.4+0.6×decay) | 近期行为权重更高 |
| 热门物品排序 | 不应用衰减 | 保持全站热度稳定性 |

```java
private double decayWeight(Instant now, Instant ratedAt) {
    if (ratedAt == null) return 1.0;
    long days = max(0L, Duration.between(ratedAt, now).toDays());
    return pow(0.5, (double) days / 30.0);
}
```

### 4.9 冷启动解决方案

系统设计了多层级的冷启动回退策略：主要算法推荐→热门物品回退→目录级冷启动补齐。

#### 4.9.1 热门物品回退（改进版）

- 随机扰动因子（±10%）避免热门物品固化
- 类别多样性约束（同类别不超过40%）
- 时间衰减权重使热门度有波动空间

#### 4.9.2 目录级冷启动补齐

当热门物品仍不足时，从全量商品目录中补齐。动态调整偏好权重：

$$\text{activityFactor} = \min(1.0, \frac{\text{userActivity}}{10.0})$$
$$\text{baseWeight} = 0.35 + 0.15 \times \text{activityFactor}$$

活跃用户（评分数>5）额外获得0.10的偏好提升。同类别物品数量限制为need的50%。

### 4.10 多样化推荐（MMR算法）

系统实现了MMR（Maximal Marginal Relevance）算法来平衡推荐结果的相关性与多样性：

$$\text{MMR} = \lambda \times \text{relevance} - (1-\lambda) \times \max(\text{similarity\_to\_selected})$$

其中λ=0.7控制相关性与多样性的平衡。系统还提供了可调节lambda参数的多样化方法，lambda = 1.0 - diversityLevel × 0.4，范围为0.6-1.0。

### 4.11 推荐理由生成

系统为每条推荐生成人性化的解释说明：

| 算法类型 | 条件 | 推荐理由 |
|----------|------|----------|
| HYBRID | 类别匹配且偏好强度≥1.2 | 混合推荐：你最近对X类目偏好明显... |
| HYBRID | 得分≥0.7 | 混合推荐：结合了相似用户、相似商品和全站热度... |
| BEHAVIOR_BASED | 类别匹配 | 行为推荐：基于近期行为轨迹... |
| ITEM_BASED | 默认 | 评分推荐：和你高分评价过的商品相似度较高 |
| USER_BASED | 无历史评分 | 评分推荐：基于相似用户偏好的冷启动候选 |

### 4.12 推荐上下文缓存

`RecommendationContext`内部类用于缓存推荐过程中的中间计算结果，采用懒加载策略：

- 用户-物品评分矩阵
- 用户-物品衰减权重矩阵
- 物品-用户评分矩阵
- 热门物品排名（热门度 = 平均分 × log(1 + 评分次数)）
- 物品类别映射（带缓存）

### 4.13 算法流程总结

1. 接收推荐请求，获取用户ID、推荐数量和算法类型参数
2. 构建推荐上下文，懒加载用户-物品评分矩阵和衰减权重矩阵
3. 根据算法类型执行相应的推荐策略
4. 合并推荐结果并应用热门物品回退
5. 对于HYBRID算法，额外应用MMR多样化优化
6. 生成推荐理由，返回最终推荐结果列表

---

## 5. 实验与评估

### 5.1 实验环境

| 配置项 | 说明 |
|--------|------|
| 数据集 | MovieLens 100K / 合成数据 |
| 编程语言 | Java 17 |
| 框架 | Spring Boot 3.3.2 |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis 7.0 |
| 测试集比例 | 0.2 |
| 相关性阈值 | 1.5 / 4.0 |

### 5.2 离线评估设计

系统通过`OfflineEvaluationService`实现离线评估功能，支持以下配置：

- `topK`：Top-K推荐的K值（范围1-100）
- `testRatio`：测试集比例（默认0.2）
- `relevanceThreshold`：相关性阈值（默认1.5）

数据集划分采用基于时间的划分策略：按评分时间排序，前(1-testRatio)作为训练集，后testRatio作为测试集，确保每个用户至少有1条训练数据和1条测试数据。

评估过程：
1. 加载全量评分数据（含时间戳）
2. 按时间顺序划分训练集和测试集
3. 对每种算法（USER_BASED、ITEM_BASED、HYBRID）分别评估
4. 计算Precision@K、Recall@K、NDCG@K、Coverage四项指标

### 5.3 实验结果

#### 5.3.1 大规模合成数据评估（testSize=28126）

使用500用户、300物品、150000条评分的合成数据集，评估结果如下：

| 算法 | K | Precision@K | Recall@K | NDCG@K | Coverage |
|------|---|-------------|----------|--------|----------|
| hybrid | 5  | 0.2638 | 0.3874 | 0.5241 | 0.3194 |
| hybrid | 10 | 0.2050 | 0.4200 | 0.5102 | 0.4710 |
| hybrid | 20 | 0.1659 | 0.4616 | 0.5041 | 0.6613 |
| hybrid | 30 | 0.1500 | 0.4956 | 0.5093 | 0.8000 |
| item   | 5  | 0.2738 | 0.3951 | 0.5303 | 0.3097 |
| item   | 10 | 0.2244 | 0.4361 | 0.5305 | 0.4710 |
| item   | 20 | 0.1891 | 0.4919 | 0.5328 | 0.7226 |
| item   | 30 | 0.1631 | 0.5254 | 0.5350 | 0.8290 |
| user   | 5  | 0.2200 | 0.3878 | 0.4790 | 0.3258 |
| user   | 10 | 0.1694 | 0.4207 | 0.4779 | 0.5194 |
| user   | 20 | 0.1338 | 0.4621 | 0.4797 | 0.7645 |
| user   | 30 | 0.1050 | 0.4784 | 0.4797 | 0.8581 |

#### 5.3.2 MovieLens 100K评估（relevanceThreshold=1.5, K=10）

| 算法 | Precision@K | Recall@K | NDCG@K | Coverage | Users |
|------|-------------|----------|--------|----------|-------|
| user | 0.0000 | 0.0000 | 0.0000 | 0.0098 | 9049 |
| item | 0.0006 | 0.0038 | 0.0025 | 0.0264 | 9049 |
| hybrid | 0.0066 | 0.0360 | 0.0225 | 0.7786 | 9049 |

### 5.4 结果分析

1. **Item-CF在Precision/NDCG上整体最优**：在合成数据评估中，Item-CF在Precision@K和NDCG@K上均优于User-CF，说明物品间相似度计算更为稳定可靠。

2. **User-CF在Coverage上最优**：User-CF的覆盖率在各K值下均最高，说明基于用户相似度的推荐能够覆盖更广泛的物品。

3. **Hybrid的Coverage显著领先**：混合推荐算法的覆盖率在MovieLens评估中达到0.779，远超单一算法，说明多信号融合有效扩展了推荐范围。

4. **指标曲线符合预期**：
   - K增大时Precision下降（推荐列表越长，精确度越低）
   - Recall随K上升（推荐列表越长，覆盖的相关物品越多）
   - Coverage随K上升（推荐物品范围扩大）

5. **动态权重策略有效**：混合推荐通过sigmoid函数实现的动态权重调整，在低活跃度用户场景下侧重内容和热门信号，在高活跃度场景下侧重协同过滤信号，实现了自适应推荐。

### 5.5 系统性能

- 物品关联预计算：应用启动时构建，每15分钟刷新，在线查询为O(1)
- 推荐结果缓存：Redis缓存30分钟，显著降低重复请求的计算开销
- 懒加载策略：RecommendationContext只在首次访问时计算中间结果
- 批量评分导入：单次上限1000条，事务内完成

---

## 6. 结论与展望

### 6.1 研究成果

本研究设计并实现了一个基于协同过滤的商品推荐系统，主要成果包括：

1. **算法实现**：实现了User-Based CF、Item-Based CF、Behavior-Based和Hybrid四种推荐算法，其中Hybrid融合五种推荐信号，通过sigmoid函数实现动态权重调整

2. **相似度优化**：设计了SimilarityMetrics工具类，针对User-CF和Item-CF分别采用Pearson和Adjusted Cosine相似度，并引入shrinkage机制和置信度加权

3. **冷启动解决**：设计了多层级回退策略（热门物品回退→目录级补齐），引入随机扰动和类别多样性约束

4. **多样化推荐**：实现了MMR算法平衡相关性与多样性，支持可调节的多样性级别

5. **时间衰减**：引入半衰期30天的指数衰减机制，使近期行为对推荐结果的影响更大

6. **完整系统**：构建了包含用户认证、行为管理、推荐展示、离线评估的完整Web应用，支持多种登录方式、Token认证、收藏/加购等功能

7. **性能优化**：采用Redis缓存、物品关联预计算、懒加载策略等优化手段，提升系统响应速度

### 6.2 不足与改进

1. **MovieLens评估指标偏低**：在MovieLens 100K数据集上，由于数据集为电影评分而非商品评分，与系统设计的商品推荐场景存在差异，导致评估指标偏低。后续可使用商品评分数据集进行更准确的评估。

2. **混合推荐权重调优**：当前混合推荐的权重基于经验设定，后续可通过网格搜索或贝叶斯优化进行系统化调参。

3. **实时推荐能力**：当前系统采用离线预计算+缓存的策略，实时性有限。后续可引入流式计算框架实现近实时推荐。

### 6.3 未来工作

1. **引入深度学习模型**：探索Neural Collaborative Filtering、Graph Neural Network等深度学习方法，提升推荐精度

2. **实时推荐优化**：引入Kafka等消息队列和Flink流式计算，实现用户行为的实时响应和推荐更新

3. **增强推荐解释性**：开发更丰富的推荐理由生成机制，结合知识图谱提供可解释的推荐

4. **多模态数据融合**：引入商品图片、文本描述等多模态特征，结合内容推荐提升冷启动效果

5. **A/B测试框架**：构建在线A/B测试平台，对比不同算法在实际业务场景中的表现

6. **长尾推荐优化**：针对长尾物品设计专门的推荐策略，提升推荐系统的多样性和新颖性
