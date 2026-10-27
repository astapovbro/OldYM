@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if errorlevel 1 (
  echo Install Python 3 with the Python Launcher, and JDK 11.
  pause
  exit /b 1
)
py -3 tools\build_portable.py --create-key %*
set "RESULT=%ERRORLEVEL%"
echo.
pause
exit /b %RESULT%
