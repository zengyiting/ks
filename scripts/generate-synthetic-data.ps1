param(
    [int]$UserCount = 400,
    [int]$ItemCount = 240,
    [double]$Density = 0.12,
    [int]$Seed = 20260308,
    [int]$StartUserId = 10001,
    [int]$StartItemId = 20001,
    [int]$MinRatingsPerUser = 15,
    [int]$MinRatingsPerItem = 12,
    [string]$OutFile = "sql\synthetic-data.sql",
    [switch]$ReplaceExisting
)

$ErrorActionPreference = "Stop"
$sw = [System.Diagnostics.Stopwatch]::StartNew()

$UserCount = [Math]::Max(1, $UserCount)
$ItemCount = [Math]::Max(1, $ItemCount)
$density = [Math]::Max(0.02, [Math]::Min(0.80, $Density))
$effectiveMinRatingsPerUser = [Math]::Min([Math]::Max(1, $MinRatingsPerUser), $ItemCount)
$effectiveMinRatingsPerItem = [Math]::Min([Math]::Max(1, $MinRatingsPerItem), $UserCount)
$rand = [System.Random]::new($Seed)
$segmentCount = 8
$categories = @("books", "electronics", "furniture", "sports", "beauty", "kitchen", "office", "toy")
Write-Output "Start generating synthetic data: users=$UserCount, items=$ItemCount, density=$density"
Write-Output "Min targets: perUser=$effectiveMinRatingsPerUser, perItem=$effectiveMinRatingsPerItem"

$users = New-Object System.Collections.Generic.List[object]
for ($i = 0; $i -lt $UserCount; $i++) {
    $users.Add([PSCustomObject]@{
        id = $StartUserId + $i
        username = "syn_user_{0:D5}" -f ($i + 1)
        segment = $rand.Next($segmentCount)
        bias = ($rand.NextDouble() - 0.5) * 0.8
    })
}
Write-Output "Users generated: $($users.Count)"

$items = New-Object System.Collections.Generic.List[object]
for ($i = 0; $i -lt $ItemCount; $i++) {
    $seg = $rand.Next($segmentCount)
    $items.Add([PSCustomObject]@{
        id = $StartItemId + $i
        name = "synthetic-item-{0:D5}" -f ($i + 1)
        category = $categories[$seg % $categories.Count]
        segment = $seg
        bias = ($rand.NextDouble() - 0.5) * 0.7
    })
}
Write-Output "Items generated: $($items.Count)"

$ratings = New-Object System.Collections.Generic.List[object]
$seen = New-Object System.Collections.Generic.HashSet[string]
$userRatingCount = @{}
$itemRatingCount = @{}

function Add-Rating {
    param(
        [long]$UserId,
        [long]$ItemId,
        [double]$Score
    )
    $key = "$UserId-$ItemId"
    if (-not $seen.Add($key)) { return }
    $s = [Math]::Round([Math]::Max(0.5, [Math]::Min(5.0, $Score)), 1)
    $ratings.Add([PSCustomObject]@{
        user_id = $UserId
        item_id = $ItemId
        score = $s
    })
    if (-not $userRatingCount.ContainsKey($UserId)) { $userRatingCount[$UserId] = 0 }
    if (-not $itemRatingCount.ContainsKey($ItemId)) { $itemRatingCount[$ItemId] = 0 }
    $userRatingCount[$UserId]++
    $itemRatingCount[$ItemId]++
}

$uIndex = 0
foreach ($u in $users) {
    $uIndex++
    foreach ($it in $items) {
        $sameSeg = [int]($u.segment -eq $it.segment)
        $prob = if ($sameSeg -eq 1) { [Math]::Min(0.92, $density * 2.6) } else { [Math]::Max(0.03, $density * 0.35) }
        if ($rand.NextDouble() -gt $prob) { continue }
        $matchBoost = if ($sameSeg -eq 1) { 1.0 + ($rand.NextDouble() * 1.5) } else { -0.8 + ($rand.NextDouble() * 1.0) }
        $noise = ($rand.NextDouble() - 0.5) * 0.8
        $score = 3.0 + $matchBoost + $u.bias + $it.bias + $noise
        Add-Rating -UserId $u.id -ItemId $it.id -Score $score
    }
    if (($uIndex % 100) -eq 0 -or $uIndex -eq $users.Count) {
        Write-Output ("Phase1 sampled users: {0}/{1}, ratings={2}, elapsed={3:n1}s" -f $uIndex, $users.Count, $ratings.Count, $sw.Elapsed.TotalSeconds)
    }
}

$uIndex = 0
foreach ($u in $users) {
    $uIndex++
    $count = if ($userRatingCount.ContainsKey($u.id)) { $userRatingCount[$u.id] } else { 0 }
    $attempt = 0
    $maxAttempts = [Math]::Max(200, $ItemCount * 20)
    while ($count -lt $effectiveMinRatingsPerUser -and $attempt -lt $maxAttempts) {
        $it = $items[$rand.Next($items.Count)]
        $sameSeg = [int]($u.segment -eq $it.segment)
        $score = if ($sameSeg -eq 1) { 4.0 + $rand.NextDouble() } else { 1.5 + ($rand.NextDouble() * 2.2) }
        Add-Rating -UserId $u.id -ItemId $it.id -Score $score
        $count = if ($userRatingCount.ContainsKey($u.id)) { $userRatingCount[$u.id] } else { 0 }
        $attempt++
    }
    if (($uIndex % 200) -eq 0 -or $uIndex -eq $users.Count) {
        Write-Output ("Phase2 balanced users: {0}/{1}, ratings={2}, elapsed={3:n1}s" -f $uIndex, $users.Count, $ratings.Count, $sw.Elapsed.TotalSeconds)
    }
}

$iIndex = 0
foreach ($it in $items) {
    $iIndex++
    $count = if ($itemRatingCount.ContainsKey($it.id)) { $itemRatingCount[$it.id] } else { 0 }
    $attempt = 0
    $maxAttempts = [Math]::Max(200, $UserCount * 20)
    while ($count -lt $effectiveMinRatingsPerItem -and $attempt -lt $maxAttempts) {
        $u = $users[$rand.Next($users.Count)]
        $sameSeg = [int]($u.segment -eq $it.segment)
        $score = if ($sameSeg -eq 1) { 4.1 + ($rand.NextDouble() * 0.9) } else { 1.2 + ($rand.NextDouble() * 2.4) }
        Add-Rating -UserId $u.id -ItemId $it.id -Score $score
        $count = if ($itemRatingCount.ContainsKey($it.id)) { $itemRatingCount[$it.id] } else { 0 }
        $attempt++
    }
    if (($iIndex % 100) -eq 0 -or $iIndex -eq $items.Count) {
        Write-Output ("Phase3 balanced items: {0}/{1}, ratings={2}, elapsed={3:n1}s" -f $iIndex, $items.Count, $ratings.Count, $sw.Elapsed.TotalSeconds)
    }
}

New-Item -ItemType Directory -Path (Split-Path -Parent $OutFile) -Force | Out-Null
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("SET NAMES utf8mb4;")
$lines.Add("START TRANSACTION;")

if ($ReplaceExisting) {
    $lines.Add("DELETE FROM ratings WHERE user_id >= $StartUserId OR item_id >= $StartItemId;")
    $lines.Add("DELETE FROM users WHERE id >= $StartUserId;")
    $lines.Add("DELETE FROM items WHERE id >= $StartItemId;")
}

function Add-BatchInsert {
    param(
        [System.Collections.Generic.List[string]]$Buffer,
        [string]$Prefix,
        [System.Collections.IEnumerable]$Rows,
        [int]$BatchSize,
        [string]$Suffix
    )
    $batch = New-Object System.Collections.Generic.List[string]
    foreach ($row in $Rows) {
        $batch.Add($row)
        if ($batch.Count -ge $BatchSize) {
            $Buffer.Add($Prefix)
            $Buffer.Add(($batch -join ",`n"))
            $Buffer.Add($Suffix)
            $batch.Clear()
        }
    }
    if ($batch.Count -gt 0) {
        $Buffer.Add($Prefix)
        $Buffer.Add(($batch -join ",`n"))
        $Buffer.Add($Suffix)
    }
}

$userRows = $users | ForEach-Object { "  ({0}, '{1}')" -f $_.id, $_.username }
Add-BatchInsert -Buffer $lines `
    -Prefix "INSERT INTO users (id, username) VALUES" `
    -Rows $userRows `
    -BatchSize 500 `
    -Suffix "ON DUPLICATE KEY UPDATE username = VALUES(username);"

$itemRows = $items | ForEach-Object { "  ({0}, '{1}', '{2}')" -f $_.id, $_.name, $_.category }
Add-BatchInsert -Buffer $lines `
    -Prefix "INSERT INTO items (id, name, category) VALUES" `
    -Rows $itemRows `
    -BatchSize 500 `
    -Suffix "ON DUPLICATE KEY UPDATE name = VALUES(name), category = VALUES(category);"

$ratingRows = $ratings | ForEach-Object { "  ({0}, {1}, {2})" -f $_.user_id, $_.item_id, ([string]::Format([Globalization.CultureInfo]::InvariantCulture, "{0:0.0}", $_.score)) }
Add-BatchInsert -Buffer $lines `
    -Prefix "INSERT INTO ratings (user_id, item_id, score) VALUES" `
    -Rows $ratingRows `
    -BatchSize 1000 `
    -Suffix "ON DUPLICATE KEY UPDATE score = VALUES(score);"

$lines.Add("COMMIT;")
Set-Content -Path $OutFile -Value ($lines -join "`n") -Encoding UTF8

Write-Output "Generated users: $UserCount"
Write-Output "Generated items: $ItemCount"
Write-Output "Generated ratings: $($ratings.Count)"
Write-Output "Min ratings per user target: $effectiveMinRatingsPerUser"
Write-Output "Min ratings per item target: $effectiveMinRatingsPerItem"
Write-Output "SQL file: $OutFile"
$sw.Stop()
Write-Output ("Elapsed seconds: {0:n1}" -f $sw.Elapsed.TotalSeconds)
