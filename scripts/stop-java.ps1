$procs = Get-Process -Name java -ErrorAction SilentlyContinue
if ($null -eq $procs) {
    Write-Output "No java processes found"
    exit 0
}
foreach ($p in $procs) {
    try {
        Write-Output ("KILL " + $p.Id)
        Stop-Process -Id $p.Id -Force -ErrorAction Stop
    } catch {
        Write-Output ("FAILED to kill " + $p.Id + ": " + $_.Exception.Message)
    }
}
Write-Output "DONE"
