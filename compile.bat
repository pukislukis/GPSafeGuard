@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0compile.ps1"
set "BUILD_EXIT_CODE=%ERRORLEVEL%"
if /I not "%~1"=="--no-pause" pause
exit /b %BUILD_EXIT_CODE%
