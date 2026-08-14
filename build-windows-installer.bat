@echo off
setlocal
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
    echo Node.js 18 or newer is required to build the MSI.
    exit /b 1
)
node -e "const major=Number(process.versions.node.split('.')[0]); if (major < 18) process.exit(1)"
if errorlevel 1 (
    echo Node.js 18 or newer is required to build the MSI.
    exit /b 1
)
where npx >nul 2>nul
if errorlevel 1 (
    echo npx is required to build the MSI.
    exit /b 1
)

echo [Market Ledger 2040] Compiling and building the Windows MSI...
call gradlew.bat clean :composeApp:compileKotlinDesktop :composeApp:packageMsi --no-daemon
if errorlevel 1 goto :failed

echo.
echo MSI created in composeApp\build\compose\binaries\main\msi
exit /b 0

:failed
echo.
echo Windows installer build failed.
exit /b 1
