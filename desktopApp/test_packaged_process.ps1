$app = Start-Process -FilePath "desktopApp\build\compose\binaries\main\app\Lyrune\Lyrune.exe" -PassThru
Start-Sleep -Seconds 5

Write-Host "Checking running processes:"
$procs = Get-Process -Name "Lyrune", "python" -ErrorAction SilentlyContinue
foreach ($p in $procs) {
    $wsMb = [math]::Round($p.WorkingSet64 / 1MB, 2)
    Write-Host "PID: $($p.Id), Name: $($p.ProcessName), WorkingSet: $wsMb MB"
}

$pythonCount = ($procs | Where-Object { $_.ProcessName -eq "python" }).Count
Write-Host "Total Python processes: $pythonCount"

if ($app -and !$app.HasExited) {
    Stop-Process -Id $app.Id -Force
}
