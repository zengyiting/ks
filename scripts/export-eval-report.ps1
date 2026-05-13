param(
    [string]$BaseUrl = "http://localhost:8080",
  [object[]]$Ks = @(5, 10, 20, 30),
    [double]$TestRatio = 0.2,
    [double]$Relevance = 1.5,
    [string]$OutDir = "reports\offline-eval"
)

$ErrorActionPreference = "Stop"

function Resolve-KValues {
  param([object[]]$RawValues)

  $resolved = @()
  foreach ($raw in $RawValues) {
    if ($null -eq $raw) {
      continue
    }

    if ($raw -is [System.Array] -and -not ($raw -is [string])) {
      foreach ($inner in $raw) {
        foreach ($token in ([string]$inner -split "[,\s]+")) {
          if ([string]::IsNullOrWhiteSpace($token)) {
            continue
          }
          $k = 0
          if (-not [int]::TryParse($token, [ref]$k) -or $k -le 0) {
            throw "Invalid K value: $token"
          }
          if ($resolved -notcontains $k) {
            $resolved += $k
          }
        }
      }
      continue
    }

    foreach ($token in ([string]$raw -split "[,\s]+")) {
      if ([string]::IsNullOrWhiteSpace($token)) {
        continue
      }
      $k = 0
      if (-not [int]::TryParse($token, [ref]$k) -or $k -le 0) {
        throw "Invalid K value: $token"
      }
      if ($resolved -notcontains $k) {
        $resolved += $k
      }
    }
  }

  if ($resolved.Count -eq 0) {
    throw "Ks must contain at least one positive integer value."
  }

  return $resolved
}

$Ks = Resolve-KValues -RawValues $Ks

New-Item -ItemType Directory -Path $OutDir -Force | Out-Null

$rows = @()
foreach ($k in $Ks) {
    $url = "$BaseUrl/api/evaluations/offline?k=$k&testRatio=$TestRatio&relevance=$Relevance"
    $report = Invoke-RestMethod -Uri $url -Method Get
    foreach ($algo in @("user", "item", "hybrid")) {
        $metric = $report.metrics.$algo
        if ($null -eq $metric) {
            continue
        }
        $rows += [PSCustomObject]@{
            algorithm      = $algo
            topK           = [int]$k
            testRatio      = [double]$report.testRatio
            relevance      = [double]$report.relevanceThreshold
            precisionAtK   = [double]$metric.precisionAtK
            recallAtK      = [double]$metric.recallAtK
            ndcgAtK        = [double]$metric.ndcgAtK
            coverage       = [double]$metric.coverage
            users          = [int]$metric.users
            trainSize      = [int]$report.trainSize
            testSize       = [int]$report.testSize
            evaluableUsers = [int]$report.evaluableUsers
        }
    }
}

$ordered = $rows | Sort-Object algorithm, topK
$csvPath = Join-Path $OutDir "offline-evaluation.csv"
$ordered | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8

$json = $ordered | ConvertTo-Json -Depth 6
$htmlPath = Join-Path $OutDir "offline-evaluation.html"
$html = @"
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <title>Offline Evaluation Report</title>
  <style>
    body { font-family: Arial, sans-serif; margin: 24px; }
    h1 { margin-bottom: 8px; }
    .toolbar { margin-bottom: 16px; display: flex; gap: 12px; align-items: center; }
    #chart { border: 1px solid #ddd; width: 960px; height: 460px; }
    table { border-collapse: collapse; margin-top: 20px; font-size: 13px; }
    th, td { border: 1px solid #ddd; padding: 6px 10px; text-align: right; }
    th:first-child, td:first-child { text-align: left; }
  </style>
</head>
<body>
  <h1>Offline Evaluation Results</h1>
  <div class="toolbar">
    <label>Metric:
      <select id="metric">
        <option value="precisionAtK">Precision@K</option>
        <option value="recallAtK">Recall@K</option>
        <option value="ndcgAtK">NDCG@K</option>
        <option value="coverage">Coverage</option>
      </select>
    </label>
  </div>
  <canvas id="chart" width="960" height="460"></canvas>
  <table id="table"></table>
  <script>
    const rows = $json;
    const metricSelect = document.getElementById("metric");
    const canvas = document.getElementById("chart");
    const ctx = canvas.getContext("2d");
    const colors = { user: "#2563eb", item: "#16a34a", hybrid: "#dc2626" };
    const order = ["user", "item", "hybrid"];

    function grouped(metric) {
      const g = {};
      for (const r of rows) {
        if (!g[r.algorithm]) g[r.algorithm] = [];
        g[r.algorithm].push({ k: Number(r.topK), v: Number(r[metric]) });
      }
      for (const k of Object.keys(g)) g[k].sort((a, b) => a.k - b.k);
      return g;
    }

    function renderTable(metric) {
      const t = document.getElementById("table");
      const header = "<tr><th>algorithm</th><th>topK</th><th>" + metric + "</th><th>users</th><th>trainSize</th><th>testSize</th></tr>";
      const body = rows
        .map(r => "<tr><td>" + r.algorithm + "</td><td>" + r.topK + "</td><td>" + Number(r[metric]).toFixed(4) + "</td><td>" + r.users + "</td><td>" + r.trainSize + "</td><td>" + r.testSize + "</td></tr>")
        .join("");
      t.innerHTML = header + body;
    }

    function draw(metric) {
      const g = grouped(metric);
      const all = rows.map(r => Number(r[metric]));
      const minV = Math.min(...all, 0);
      const maxV = Math.max(...all, 1);
      const xMin = Math.min(...rows.map(r => Number(r.topK)));
      const xMax = Math.max(...rows.map(r => Number(r.topK)));
      const pad = 50;
      const w = canvas.width - pad * 2;
      const h = canvas.height - pad * 2;

      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.fillStyle = "#ffffff";
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.strokeStyle = "#e5e7eb";
      ctx.lineWidth = 1;
      for (let i = 0; i <= 5; i++) {
        const y = pad + (h * i) / 5;
        ctx.beginPath();
        ctx.moveTo(pad, y);
        ctx.lineTo(pad + w, y);
        ctx.stroke();
      }
      ctx.strokeStyle = "#111827";
      ctx.beginPath();
      ctx.moveTo(pad, pad);
      ctx.lineTo(pad, pad + h);
      ctx.lineTo(pad + w, pad + h);
      ctx.stroke();

      function sx(x) {
        if (xMax === xMin) return pad + w / 2;
        return pad + ((x - xMin) / (xMax - xMin)) * w;
      }
      function sy(y) {
        if (maxV === minV) return pad + h / 2;
        return pad + h - ((y - minV) / (maxV - minV)) * h;
      }

      for (const algo of order) {
        const points = g[algo] || [];
        if (points.length === 0) continue;
        ctx.strokeStyle = colors[algo];
        ctx.fillStyle = colors[algo];
        ctx.lineWidth = 2;
        ctx.beginPath();
        points.forEach((p, i) => {
          const x = sx(p.k), y = sy(p.v);
          if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
        });
        ctx.stroke();
        points.forEach(p => {
          const x = sx(p.k), y = sy(p.v);
          ctx.beginPath();
          ctx.arc(x, y, 3, 0, Math.PI * 2);
          ctx.fill();
        });
      }

      ctx.fillStyle = "#111827";
      ctx.font = "12px Arial";
      ctx.fillText(metric, pad, 18);
      ctx.fillText("topK", canvas.width - 50, canvas.height - 12);
      let ly = 20;
      for (const algo of order) {
        ctx.fillStyle = colors[algo];
        ctx.fillRect(canvas.width - 130, ly - 10, 10, 10);
        ctx.fillStyle = "#111827";
        ctx.fillText(algo, canvas.width - 115, ly);
        ly += 18;
      }
    }

    function render() {
      const metric = metricSelect.value;
      draw(metric);
      renderTable(metric);
    }

    metricSelect.addEventListener("change", render);
    render();
  </script>
</body>
</html>
"@

Set-Content -Path $htmlPath -Value $html -Encoding UTF8

$culture = [System.Globalization.CultureInfo]::InvariantCulture
$fmt = {
    param([double]$v)
    return [string]::Format($culture, "{0:0.0000}", $v)
}

$algorithms = @("user", "item", "hybrid")
$metrics = @("precisionAtK", "recallAtK", "ndcgAtK", "coverage")
$avgByAlgo = @{}
foreach ($algo in $algorithms) {
    $subset = $ordered | Where-Object { $_.algorithm -eq $algo }
    if ($subset.Count -eq 0) {
        continue
    }
    $avgByAlgo[$algo] = @{
        precisionAtK = ($subset | Measure-Object -Property precisionAtK -Average).Average
        recallAtK = ($subset | Measure-Object -Property recallAtK -Average).Average
        ndcgAtK = ($subset | Measure-Object -Property ndcgAtK -Average).Average
        coverage = ($subset | Measure-Object -Property coverage -Average).Average
    }
}

$latexLines = @(
    "\begin{table}[htbp]",
    "\centering",
    "\caption{Offline recommendation evaluation results}",
    "\begin{tabular}{lrrrrr}",
    "\hline",
    "Algorithm & K & Precision@K & Recall@K & NDCG@K & Coverage \\",
    "\hline"
)
foreach ($r in $ordered) {
    $latexLines += "{0} & {1} & {2} & {3} & {4} & {5} \\" -f `
        $r.algorithm, `
        $r.topK, `
        (& $fmt $r.precisionAtK), `
        (& $fmt $r.recallAtK), `
        (& $fmt $r.ndcgAtK), `
        (& $fmt $r.coverage)
}
$latexLines += "\hline"
$latexLines += "\end{tabular}"
$latexLines += "\end{table}"
$latexPath = Join-Path $OutDir "offline-evaluation.tex"
Set-Content -Path $latexPath -Value ($latexLines -join "`n") -Encoding UTF8

$summaryLines = @("# Offline Evaluation Summary", "")
$kValues = $ordered.topK | Sort-Object -Unique
foreach ($k in $kValues) {
    $summaryLines += "## K=$k"
    $subset = $ordered | Where-Object { $_.topK -eq $k }
    foreach ($metric in $metrics) {
        $best = $subset | Sort-Object -Property $metric -Descending | Select-Object -First 1
        $summaryLines += "- Best ${metric}: $($best.algorithm) = $(& $fmt $best.$metric)"
    }
    $summaryLines += ""
}

$summaryLines += "## Cross-K Average Performance"
foreach ($metric in $metrics) {
    $bestAlgo = $null
    $bestVal = -1.0
    foreach ($algo in $algorithms) {
        if (-not $avgByAlgo.ContainsKey($algo)) {
            continue
        }
        $v = [double]$avgByAlgo[$algo][$metric]
        if ($v -gt $bestVal) {
            $bestVal = $v
            $bestAlgo = $algo
        }
    }
    if ($null -ne $bestAlgo) {
        $summaryLines += "- Best average ${metric}: $bestAlgo = $(& $fmt $bestVal)"
    }
}
$summaryPath = Join-Path $OutDir "offline-evaluation-summary.md"
Set-Content -Path $summaryPath -Value ($summaryLines -join "`n") -Encoding UTF8

Write-Output "CSV: $csvPath"
Write-Output "HTML: $htmlPath"
Write-Output "TEX: $latexPath"
Write-Output "SUMMARY: $summaryPath"
