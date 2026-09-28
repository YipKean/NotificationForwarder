@echo off
powershell.exe -NoProfile -Command "try { Enable-ScheduledTask -TaskName 'NotificationForwarder-Laptop' -ErrorAction Stop | Out-Null; Start-ScheduledTask -TaskName 'NotificationForwarder-Laptop' -ErrorAction Stop; Write-Host 'Forwarder start requested; automatic startup enabled. See webhook\logs\supervisor.log.' } catch { Write-Host $_; exit 1 }"
pause
