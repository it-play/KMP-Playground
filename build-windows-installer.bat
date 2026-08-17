@echo off
setlocal EnableExtensions
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
    echo Node.js 18 or newer is required to build the launcher MSI.
    exit /b 1
)
node -e "const major=Number(process.versions.node.split('.')[0]); if (major < 18) process.exit(1)"
if errorlevel 1 (
    echo Node.js 18 or newer is required to build the launcher MSI.
    exit /b 1
)
where npx >nul 2>nul
if errorlevel 1 (
    echo npx is required to build the launcher MSI.
    exit /b 1
)

set "ML_BUILD_CHANNEL=release"
if not defined ML_BUILD_COHORT (
    for /f "usebackq delims=" %%C in (`powershell -NoProfile -Command "[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()"`) do set "ML_BUILD_COHORT=%%C"
)
if not defined ML_RELEASE_PUBLISHED_AT (
    for /f "usebackq delims=" %%T in (`powershell -NoProfile -Command "[DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')"`) do set "ML_RELEASE_PUBLISHED_AT=%%T"
)

for %%V in (
    ML_DEBUG_BUNDLE_SIGNING_KEY_PKCS8_BASE64
    ML_DEBUG_BUNDLE_SIGNING_PUBLIC_KEY_X509_BASE64
    ML_FEED_SIGNING_KEY_PKCS8_B64
    ML_FEED_SIGNING_PUBLIC_KEY_X509_B64
    ML_WINDOWS_SIGNING_CERT_SHA1
) do (
    if not defined %%V (
        echo %%V is required. Import the Authenticode certificate and configure all release signing inputs first.
        exit /b 1
    )
)

echo [Market Ledger 2040] Building one signed game, debug bundle, feed, and launcher MSI release...
call gradlew.bat clean buildWindowsRelease --configuration-cache --no-daemon
if errorlevel 1 goto :failed

echo.
echo Signed stable assets: build\release
echo Launcher MSI: src\launcherApp\build\compose\binaries\main\msi
exit /b 0

:failed
echo.
echo Windows release build failed. No artifact should be published.
exit /b 1
