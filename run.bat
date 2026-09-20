@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1"
if errorlevel 1 (
  echo Avvio non riuscito. Eseguire build.bat e verificare JDK 21.
  pause
  exit /b 1
)
