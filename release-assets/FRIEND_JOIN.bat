@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0friend_join.ps1"
pause
