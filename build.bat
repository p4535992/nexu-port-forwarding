@echo off
setlocal
pushd "%~dp0"
where mvn.cmd >nul 2>nul
if errorlevel 1 (
  echo Installare Maven 3.9+ e JDK 21.
  popd
  exit /b 1
)
call mvn.cmd -B clean verify
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%
