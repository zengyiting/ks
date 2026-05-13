# 离线评估导出与绘图

先启动服务：

```bash
mvn -s .mvn/settings.xml spring-boot:run
```

调用 JSON 评估接口：

```bash
GET /api/evaluations/offline?k=10&testRatio=0.2&relevance=4.0
```

调用 CSV 导出接口：

```bash
GET /api/evaluations/offline/csv?k=10&testRatio=0.2&relevance=4.0
```

一键导出并绘图：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\export-eval-report.ps1 -BaseUrl http://localhost:8080 -Ks 5,10,20,30
```

也可以直接用包装命令（推荐）：

```bash
.\scripts\run-export-eval.cmd -BaseUrl http://localhost:8080 -Ks 5,10,20,30
```

输出文件：

- `reports/offline-eval/offline-evaluation.csv`
- `reports/offline-eval/offline-evaluation.html`
- `reports/offline-eval/offline-evaluation.tex`
- `reports/offline-eval/offline-evaluation-summary.md`

生成更多训练/验证数据：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\generate-synthetic-data.ps1 -UserCount 1200 -ItemCount 600 -Density 0.10 -ReplaceExisting
```

也可以直接用包装命令（推荐）：

```bash
.\scripts\run-generate-synthetic.cmd -UserCount 1200 -ItemCount 600 -Density 0.10 -ReplaceExisting
```

小规模调试建议同时指定最小交互阈值，避免参数过高：

```bash
.\scripts\run-generate-synthetic.cmd -UserCount 50 -ItemCount 40 -MinRatingsPerUser 8 -MinRatingsPerItem 6
```

导入生成的数据：

```bash
mysql -u root -p recommend < .\sql\synthetic-data.sql
```

常见报错排查：

- `running scripts is disabled`：先执行 `Set-ExecutionPolicy -Scope Process Bypass`
- `不是内部或外部命令`：先切到项目根目录再运行命令
- `找不到脚本文件`：优先使用 `run-*.cmd`，避免 PowerShell 路径转义问题
- 脚本长时间不结束：检查 `MinRatingsPerUser <= ItemCount` 且 `MinRatingsPerItem <= UserCount`
