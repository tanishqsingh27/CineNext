# Installs a project-local JDK. Does not change the system Java installation or registry.
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$toolsPath = Join-Path $projectPath '.tools'
$jdkPath = Join-Path $toolsPath 'jdk-21.0.13+9'
if (Test-Path -LiteralPath (Join-Path $jdkPath 'bin/java.exe')) {
    Write-Host "Already installed: $jdkPath"
    exit 0
}
New-Item -ItemType Directory -Path $toolsPath -Force | Out-Null
$archivePath = Join-Path $toolsPath 'temurin21-windows-x64.zip'
$downloadUrl = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_windows_hotspot_21.0.13_9.zip'
$sha256 = 'a4dbaa05fca4bd89d84584e58c459bd732efebb1722af80ab13c6516832004f9'
Write-Host 'Downloading Eclipse Temurin 21.0.13+9 (Windows x64, approximately 205 MB)...'
Invoke-WebRequest -Uri $downloadUrl -OutFile $archivePath
if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sha256) {
    throw 'JDK checksum verification failed. Archive was not extracted.'
}
Expand-Archive -LiteralPath $archivePath -DestinationPath $toolsPath -Force
& (Join-Path $jdkPath 'bin/java.exe') --version
if ($LASTEXITCODE -ne 0) { throw 'Java verification failed.' }
Write-Host "Project JDK ready: $jdkPath"
