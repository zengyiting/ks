# 推荐系统实现逻辑与流程说明

## 1. 总体实现思路

本系统采用“数据层 -> 算法层 -> 服务层 -> 接口层 -> 评估与导出层”的分层结构：

- 数据层负责用户、物品、评分读写
- 算法层负责 UserCF / ItemCF 相似度计算与打分
- 服务层负责策略选择、混合融合、冷启动兜底
- 接口层负责参数校验、结果组装、异常响应
- 评估与脚本层负责离线实验、报表导出、数据扩充

## 2. 关键模块与职责

### 2.1 数据与仓库层

- `users/items/ratings` 三张核心表，`ratings` 为训练主数据
- 仓库负责提供训练矩阵与热门统计查询

核心位置：

- [RatingRepository.java](file:///d:/app/ks/recommend/src/main/java/com/example/recommend/repository/RatingRepository.java)

### 2.2 算法层

- `UserBasedCF`：基于用户相似度预测候选评分
- `ItemBasedCF`：基于物品相似度预测候选评分
- `SimilarityMetrics`：提供 Pearson、Cosine、重叠数与收缩权重

核心位置：

- [UserBasedCF.java](file:///d:/app/ks/recommend/src/main/java/com/example/recommend/algo/UserBasedCF.java)
- [ItemBasedCF.java](file:///d:/app/ks/recommend/src/main/java/com/example/recommend/algo/ItemBasedCF.java)
- [SimilarityMetrics.java](file:///d:/app/ks/recommend/src/main/java/com/example/recommend/algo/SimilarityMetrics.java)

### 2.3 服务层

- 构建 user-item 评分矩阵
- 根据 `AlgorithmType` 选择 user/item/hybrid
- hybrid 采用多路候选融合（item + user + popularity）
- 结果不足时使用热门兜底

核心位置：

- [RecommendationService.java](file:///d:/app/ks/recommend/src/main/java/com/example/recommend/service/RecommendationService.java)
- [AlgorithmType.java](file:///d:/app/ks/recommend/src/main/java/com/example/recommend/service/AlgorithmType.java)

### 2.4 接口层

- 推荐接口：入参解析、范围限制、DTO 组装
- 评估接口：返回 JSON 指标与 CSV 导出
- 异常处理：统一错误结构与状态码

核心位置：

- [RecommendationController.java](file:///d:/app/ks/recommend/src/main/java/com/example/recommend/web/RecommendationController.java)
- [EvaluationController.java](file:///d:/app/ks/recommend/src/main/java/com/example/recommend/web/EvaluationController.java)
- [ApiExceptionHandler.java](file:///d:/app/ks/recommend/src/main/java/com/example/recommend/web/ApiExceptionHandler.java)

### 2.5 离线评估层

- 对训练/测试集做可复现切分
- 统一评估 user/item/hybrid 三种算法
- 计算 Precision@K / Recall@K / NDCG@K / Coverage

核心位置：

- [OfflineEvaluationService.java](file:///d:/app/ks/recommend/src/main/java/com/example/recommend/service/OfflineEvaluationService.java)

### 2.6 脚本层

- 生成合成训练数据 SQL
- 一键导出 CSV/HTML/TEX/Summary 报告
- `.cmd` 包装提升 Windows 兼容性

核心位置：

- [generate-synthetic-data.ps1](file:///d:/app/ks/recommend/scripts/generate-synthetic-data.ps1)
- [export-eval-report.ps1](file:///d:/app/ks/recommend/scripts/export-eval-report.ps1)
- [run-generate-synthetic.cmd](file:///d:/app/ks/recommend/scripts/run-generate-synthetic.cmd)
- [run-export-eval.cmd](file:///d:/app/ks/recommend/scripts/run-export-eval.cmd)

## 3. 在线推荐流程

请求示例：

- `GET /api/recommendations/{userId}?n=5&algo=hybrid`

执行流程：

1. Controller 校验参数并限制 `n` 范围  
2. Service 从评分表构建 user-item 矩阵  
3. 依据算法类型进入 UserCF / ItemCF / Hybrid  
4. Hybrid 对多路候选进行融合打分  
5. 若候选不足，执行 popularity fallback  
6. 根据 itemId 反查商品信息并返回 DTO

## 4. Hybrid 逻辑实现流程

Hybrid 当前实现采用加权融合：

- ItemCF 排名分：0.55
- UserCF 排名分：0.30
- Popularity 先验分：0.15

流程说明：

1. 分别生成 item/user 候选池（扩容到 `max(topN*5,20)`）  
2. 将排名转换为 rank score（`1/(1+i)`）  
3. 计算并归一化热门分数  
4. 对候选 item 做加权合成  
5. 按分数排序并截断 topN  

## 5. 离线评估流程

请求示例：

- `GET /api/evaluations/offline?k=10&testRatio=0.2&relevance=4.0`

执行流程：

1. 读取全量评分并做确定性切分（train/test）  
2. 为每个可评估用户生成推荐结果  
3. 对比 test 中相关项，累计命中  
4. 计算 Precision@K / Recall@K / NDCG@K  
5. 统计 Coverage 和用户数  
6. 返回三算法对比结果

## 6. 数据扩充与实验流程

### 6.1 生成实验数据

```bash
.\scripts\run-generate-synthetic.cmd -UserCount 1200 -ItemCount 600 -Density 0.10 -ReplaceExisting
```

### 6.2 导入数据库

```bash
mysql -u root -p recommend < .\sql\synthetic-data.sql
```

### 6.3 运行评估并导出

```bash
.\scripts\run-export-eval.cmd -BaseUrl http://localhost:8080 -Ks 5,10,20,30
```

产物：

- `offline-evaluation.csv`
- `offline-evaluation.html`
- `offline-evaluation.tex`
- `offline-evaluation-summary.md`

## 7. 答辩讲解建议（可直接按此顺序讲）

1. 先讲分层架构，说明各层职责  
2. 再讲三种算法与 hybrid 融合策略  
3. 然后讲冷启动与兜底机制  
4. 展示离线评估指标及导出报告  
5. 最后讲数据扩充与实验可复现能力
