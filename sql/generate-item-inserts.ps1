$csvPath = "d:\app\ks\recommendtwo\sql\tokopedia_products.csv"
$sqlPath = "d:\app\ks\recommendtwo\sql\insert_items.sql"
$encoding = [System.Text.Encoding]::UTF8

$lines = Get-Content -Path $csvPath -Encoding UTF8
$header = $lines[0] -replace '"', ''
$columns = $header -split ','

$titleIdx = $columns.IndexOf("title")
$categoryIdx = $columns.IndexOf("categories")
$priceIdx = $columns.IndexOf("final_price")
$imageIdx = $columns.IndexOf("main_image")
$descIdx = $columns.IndexOf("description")

$sql = New-Object System.Text.StringBuilder
[void]$sql.AppendLine("-- Tokopedia商品数据导入")
[void]$sql.AppendLine("-- 生成时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
[void]$sql.AppendLine()
[void]$sql.AppendLine("SET FOREIGN_KEY_CHECKS=0;")
[void]$sql.AppendLine("TRUNCATE TABLE items;")
[void]$sql.AppendLine()

$count = 0
for ($i = 1; $i -lt $lines.Count -and $count -lt 100; $i++) {
    $line = $lines[$i]
    if ([string]::IsNullOrWhiteSpace($line)) { continue }

    $values = @()
    $inQuotes = $false
    $currentField = New-Object System.Text.StringBuilder
    for ($j = 0; $j -lt $line.Length; $j++) {
        $char = $line[$j]
        if ($char -eq '"') {
            $inQuotes = -not $inQuotes
        } elseif ($char -eq ',' -and -not $inQuotes) {
            $values += $currentField.ToString()
            $currentField = New-Object System.Text.StringBuilder
        } else {
            [void]$currentField.Append($char)
        }
    }
    $values += $currentField.ToString()

    if ($values.Count -le [Math]::Max($titleIdx, $categoryIdx, $priceIdx, $imageIdx, $descIdx)) { continue }

    $name = $values[$titleIdx] -replace '"', '' -replace "'", "''" -replace "\\", ""
    if ([string]::IsNullOrWhiteSpace($name)) { continue }
    if ($name.Length -gt 200) { $name = $name.Substring(0, 200) }

    $category = $values[$categoryIdx] -replace '"', '' -replace "'", "''" -replace "\\", ""
    if ([string]::IsNullOrWhiteSpace($category)) { $category = "Uncategorized" }
    if ($category.Length -gt 100) { $category = $category.Substring(0, 100) }

    $priceStr = $values[$priceIdx] -replace '"', '' -replace "[^0-9.]", ""
    $price = 0
    if (-not [double]::TryParse($priceStr, [ref]$price)) { $price = 0 }

    $imageUrl = $values[$imageIdx] -replace '"', '' -replace "'", "''"
    if ([string]::IsNullOrWhiteSpace($imageUrl)) { $imageUrl = $null } else { $imageUrl = "'$imageUrl'" }

    $description = $values[$descIdx] -replace '"', '' -replace "'", "''" -replace "\\", ""
    if ([string]::IsNullOrWhiteSpace($description)) { $description = "" }
    if ($description.Length -gt 2000) { $description = $description.Substring(0, 2000) }
    $description = $description -replace "`n", " " -replace "`r", " "
    $description = "'$description'"

    $imagePart = if ($imageUrl) { "'$imageUrl'" } else { "NULL" }
    $sql.AppendLine("INSERT INTO items (name, category, price, image_url, description) VALUES ('$name', '$category', $price, $imagePart, $description);")
    $count++
}

[void]$sql.AppendLine()
[void]$sql.AppendLine("SET FOREIGN_KEY_CHECKS=1;")
[void]$sql.AppendLine("SELECT COUNT(*) AS total_items_inserted FROM items;")

$sql.ToString() | Out-File -FilePath $sqlPath -Encoding UTF8 -NoNewline
Write-Host "SQL file generated: $sqlPath"
Write-Host "Total items to insert: $count"