@echo off
powershell.exe -NoProfile -Command "try { Disable-ScheduledTask -TaskName 'NotificationForwarder-Laptop' -ErrorAction Stop | Out-Null; Stop-ScheduledTask -TaskName 'NotificationForwarder-Laptop' -ErrorAction Stop; Write-Host 'Forwarder stopped; automatic startup paused until you use Start Forwarder.' } catch { Write-Host $_; exit 1 }"
pause
