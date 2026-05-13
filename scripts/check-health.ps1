$max=120
$i=0
while ($i -lt $max) {
    try {
        $r = Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health' -Method Get -TimeoutSec 2
        Write-Output 'READY'
        break
    } catch {
        Start-Sleep -Seconds 2
        $i = $i + 1
    }
}
if ($i -ge $max) {
    Write-Error 'SERVER_TIMEOUT'
}
