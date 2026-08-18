@echo off
setlocal EnableExtensions
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
    echo Node.js 18 or newer is required to build the offline launcher MSI.
    exit /b 1
)

node -e "const major=Number(process.versions.node.split('.')[0]); if (!Number.isInteger(major) || major < 18) process.exit(1)"
if errorlevel 1 (
    echo Node.js 18 or newer is required to build the offline launcher MSI.
    exit /b 1
)

where npx >nul 2>nul
if errorlevel 1 (
    echo npx is required to package the offline launcher MSI.
    exit /b 1
)

node "composeApp\src\launcherApp\scripts\build-offline-windows-installer.mjs"
exit /b %errorlevel%
