$ErrorActionPreference = 'Stop'
& "$PSScriptRoot/supervise.ps1" -Check
if ($LASTEXITCODE -ne 0) { throw 'Preflight failed' }
$name = 'NotificationForwarder-Laptop'
$account = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$action = New-ScheduledTaskAction -Execute "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -Argument "-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$PSScriptRoot\supervise.ps1`"" -WorkingDirectory (Split-Path $PSScriptRoot -Parent)
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $account
$watchdog = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 1)
$principal = New-ScheduledTaskPrincipal -UserId $account -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName $name -Action $action -Trigger @($trigger, $watchdog) -Principal $principal -Settings $settings -Description 'Synthetic notification receiver, ngrok and Hermes worker with process recovery.' -Force | Out-Null
Write-Output 'Startup installed at sign-in with a one-minute recovery check. Double-click Start Forwarder.cmd to start now.'
