@echo off
setlocal EnableExtensions

cd /d "%~dp0"

set "BACKEND_PORT=8081"
set "APP_JAR=MGserver-v1.0.0.jar"
set "LOG_DIR=%~dp0logs"
set "BACKEND_LOG=%LOG_DIR%\backend-startup.log"

call :KillPort %BACKEND_PORT%
timeout /t 1 /nobreak >nul

if not exist "%APP_JAR%" (
  echo Cannot find %APP_JAR% in %cd%
  exit /b 1
)
where java >nul 2>&1
if errorlevel 1 (
  echo Cannot find Java in PATH. JDK 21 or newer is required.
  exit /b 1
)
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo Starting MGserver from %APP_JAR%
start "MGserver" /b java -jar "%APP_JAR%" --server.port=%BACKEND_PORT% >> "%BACKEND_LOG%" 2>&1
if errorlevel 1 (
  echo Failed to start MGserver. Check %BACKEND_LOG%
  exit /b 1
)

echo.
echo MGserver: http://localhost:%BACKEND_PORT%
echo Logs:     %LOG_DIR%
echo.
echo MGserver started. The frontend is served from the JAR.
exit /b 0

:KillPort
set "PORT=%~1"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$c=Get-NetTCPConnection -LocalPort %PORT% -State Listen -ErrorAction SilentlyContinue; foreach($x in $c){Stop-Process -Id $x.OwningProcess -Force -ErrorAction SilentlyContinue}"
exit /b 0
