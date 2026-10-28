@echo off
setlocal
cd /d "%~dp0"
if "%~1"=="" (
    call "%~dp0gradlew.bat" assembleDebug
) else (
    call "%~dp0gradlew.bat" %*
)
exit /b %ERRORLEVEL%
