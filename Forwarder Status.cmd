@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0webhook\windows\status.ps1"
pause
