$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$certificate = $null
$certificatePath = $null
$certificateThumbprint = $null
$msiExtractionDirectory = $null
$buildIdentifier = [Guid]::NewGuid().ToString("N")
$subject = "CN=Market Ledger 2040 Internal Build $buildIdentifier"
$generatedEnvironmentNames = @(
    "ELECTRON_BUILDER_OFFLINE",
    "ML_BUILD_CHANNEL",
    "ML_BUILD_COHORT",
    "ML_DEBUG_BUNDLE_SIGNING_KEY_PKCS8_BASE64",
    "ML_DEBUG_BUNDLE_SIGNING_PUBLIC_KEY_X509_BASE64",
    "ML_FEED_SIGNING_KEY_PKCS8_B64",
    "ML_FEED_SIGNING_PUBLIC_KEY_X509_B64",
    "ML_RELEASE_PUBLISHED_AT",
    "ML_WINDOWS_SIGNING_CERT_SHA1"
)

function Remove-BuildCertificate {
    param(
        [string] $StorePath,
        [string] $Thumbprint,
        [switch] $DeletePrivateKey
    )

    if ([string]::IsNullOrWhiteSpace($Thumbprint)) {
        return
    }
    $path = Join-Path $StorePath $Thumbprint
    if (-not (Test-Path -LiteralPath $path)) {
        return
    }
    if ($DeletePrivateKey) {
        Remove-Item -LiteralPath $path -DeleteKey -Force -ErrorAction Stop
        return
    }
    Remove-Item -LiteralPath $path -Force -ErrorAction Stop
}

try {
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
        throw "The offline launcher MSI must be built on Windows."
    }
    if (-not (Test-Path -LiteralPath ".\gradlew.bat" -PathType Leaf)) {
        throw "gradlew.bat was not found in the project directory."
    }

    $certificate = New-SelfSignedCertificate `
        -Type CodeSigningCert `
        -Subject $subject `
        -CertStoreLocation "Cert:\CurrentUser\My" `
        -KeyAlgorithm RSA `
        -KeyLength 3072 `
        -HashAlgorithm SHA256 `
        -KeyExportPolicy NonExportable `
        -NotBefore (Get-Date).AddMinutes(-5) `
        -NotAfter (Get-Date).AddDays(30)

    if ($null -eq $certificate -or -not $certificate.HasPrivateKey) {
        throw "The temporary Authenticode certificate has no private key."
    }
    $codeSigningOid = "1.3.6.1.5.5.7.3.3"
    $enhancedKeyUsageOids = @(
        $certificate.EnhancedKeyUsageList | ForEach-Object { [string] $_.ObjectId }
    )
    if ($enhancedKeyUsageOids -notcontains $codeSigningOid) {
        throw "The temporary certificate is not valid for code signing."
    }

    $certificateThumbprint = $certificate.Thumbprint.ToUpperInvariant()
    if ($certificateThumbprint -notmatch "^[0-9A-F]{40}$") {
        throw "The temporary certificate has an invalid SHA-1 thumbprint."
    }

    $certificatePath = Join-Path ([IO.Path]::GetTempPath()) "market-ledger-$buildIdentifier.cer"
    Export-Certificate -Cert $certificate -FilePath $certificatePath -Type CERT | Out-Null

    $env:ML_WINDOWS_SIGNING_CERT_SHA1 = $certificateThumbprint
    & ".\gradlew.bat" clean buildWindowsRelease --configuration-cache --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "The offline Windows release build failed with exit code $LASTEXITCODE."
    }

    $msiDirectory = Join-Path $PWD "composeApp\src\launcherApp\build\compose\binaries\main\msi"
    $msiFiles = @(Get-ChildItem -LiteralPath $msiDirectory -Filter "MarketLedger2040-Launcher-*.msi" -File)
    if ($msiFiles.Count -ne 1) {
        throw "Expected exactly one packaged launcher MSI."
    }
    $trustedCertificates = @(Import-Certificate `
        -FilePath $certificatePath `
        -CertStoreLocation "Cert:\CurrentUser\Root")
    if ($trustedCertificates.Count -ne 1 -or
        $trustedCertificates[0].Thumbprint.ToUpperInvariant() -ne $certificateThumbprint) {
        throw "The temporary trusted certificate differs from the signing certificate."
    }
    $msiExtractionDirectory = Join-Path `
        ([IO.Path]::GetTempPath()) `
        "market-ledger-msi-$buildIdentifier"
    New-Item -ItemType Directory -Path $msiExtractionDirectory | Out-Null
    & msiexec.exe /a $msiFiles[0].FullName /qn "TARGETDIR=$msiExtractionDirectory"
    if ($LASTEXITCODE -ne 0) {
        throw "MSI administrative extraction failed with exit code $LASTEXITCODE."
    }
    $launcherExecutables = @(
        Get-ChildItem `
            -LiteralPath $msiExtractionDirectory `
            -Filter "MarketLedger2040Launcher.exe" `
            -File `
            -Recurse
    )
    if ($launcherExecutables.Count -ne 1) {
        throw "Expected exactly one packaged launcher executable."
    }
    foreach ($file in @($launcherExecutables[0], $msiFiles[0])) {
        $signature = Get-AuthenticodeSignature -FilePath $file.FullName
        if ($signature.Status -ne [System.Management.Automation.SignatureStatus]::Valid -or
            $signature.SignerCertificate.Thumbprint.ToUpperInvariant() -ne $certificateThumbprint) {
            throw "Authenticode verification failed for $($file.Name)."
        }
    }

    Write-Host ""
    Write-Host "Offline signed assets: build\release"
    Write-Host "Offline launcher MSI: composeApp\src\launcherApp\build\compose\binaries\main\msi"
} finally {
    $cleanupFailures = [Collections.Generic.List[string]]::new()
    foreach ($name in $generatedEnvironmentNames) {
        Remove-Item "Env:\$name" -ErrorAction SilentlyContinue
    }
    if ($null -ne $certificatePath -and (Test-Path -LiteralPath $certificatePath -PathType Leaf)) {
        try {
            Remove-Item -LiteralPath $certificatePath -Force -ErrorAction Stop
        } catch {
            $cleanupFailures.Add("temporary certificate file: $($_.Exception.Message)")
        }
    }
    if ($null -ne $msiExtractionDirectory -and
        (Test-Path -LiteralPath $msiExtractionDirectory -PathType Container)) {
        try {
            Remove-Item -LiteralPath $msiExtractionDirectory -Recurse -Force -ErrorAction Stop
        } catch {
            $cleanupFailures.Add("MSI verification directory: $($_.Exception.Message)")
        }
    }
    try {
        Remove-BuildCertificate `
            -StorePath "Cert:\CurrentUser\Root" `
            -Thumbprint $certificateThumbprint
    } catch {
        $cleanupFailures.Add("trusted certificate: $($_.Exception.Message)")
    }
    try {
        Remove-BuildCertificate `
            -StorePath "Cert:\CurrentUser\My" `
            -Thumbprint $certificateThumbprint `
            -DeletePrivateKey
    } catch {
        $cleanupFailures.Add("signing certificate and private key: $($_.Exception.Message)")
    }
    if ($cleanupFailures.Count -ne 0) {
        throw "Temporary signing material cleanup failed: $($cleanupFailures -join '; ')"
    }
}
