$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$config = Get-Content -LiteralPath "$PSScriptRoot/settings.json" -Raw | ConvertFrom-Json
$receiverConfig = & $config.node "$PSScriptRoot/preflight.js"
if ($LASTEXITCODE -ne 0) { throw 'Receiver configuration failed preflight.' }
$port = ($receiverConfig | ConvertFrom-Json).port
Get-ScheduledTask -TaskName NotificationForwarder-Laptop | Select-Object TaskName,State | Format-Table
try { Invoke-RestMethod "http://127.0.0.1:$port/health" -TimeoutSec 3 | Format-List } catch { Write-Output 'Receiver is unavailable.' }
try { (Invoke-RestMethod http://127.0.0.1:4040/api/tunnels -TimeoutSec 3).tunnels | Select-Object public_url | Format-List } catch { Write-Output 'ngrok is unavailable.' }
Get-Content -LiteralPath (Join-Path $root 'logs/supervisor.log') -Tail 12
