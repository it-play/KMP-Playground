@echo off
setlocal
cd /d "%~dp0"

echo [Market Ledger 2040] Running tests and building the Windows MSI...
call gradlew.bat :composeApp:desktopTest :composeApp:packageMsi --no-daemon
if errorlevel 1 goto :failed

echo.
echo MSI created in composeApp\build\compose\binaries\main\msi
exit /b 0

:failed
echo.
echo Windows installer build failed.
exit /b 1
