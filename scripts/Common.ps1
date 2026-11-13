$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot

function Use-ProjectJava {
    param([string]$JavaHome)
    if (-not $JavaHome) {
        $localJdk = Join-Path $ProjectRoot '.tools/jdk-21.0.13+9'
        if (Test-Path -LiteralPath (Join-Path $localJdk 'bin/java.exe')) { $JavaHome = $localJdk }
        elseif ($env:JAVA_HOME) { $JavaHome = $env:JAVA_HOME }
    }
    if ($JavaHome) {
        $resolvedJdk = (Resolve-Path -LiteralPath $JavaHome).Path
        if (-not (Test-Path -LiteralPath (Join-Path $resolvedJdk 'bin/javac.exe'))) { throw 'JavaHome must point to a JDK with bin/javac.exe.' }
        $env:JAVA_HOME = $resolvedJdk
        $env:PATH = (Join-Path $resolvedJdk 'bin') + [IO.Path]::PathSeparator + $env:PATH
    }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java 21 is required. Run ./scripts/Install-LocalJdk.ps1.' }
    $javaOutput = (& java --version | Out-String)
    if ($LASTEXITCODE -ne 0 -or $javaOutput -notmatch '(?m)^(openjdk|java) 21\.') { throw 'Java 21 is required. Pass -JavaHome <JDK21 directory> or run ./scripts/Install-LocalJdk.ps1.' }
    if (-not (Get-Command mvn.cmd -ErrorAction SilentlyContinue)) { throw 'Maven 3.9+ is required on PATH. See README.md.' }
}

function Import-ProjectEnvironment {
    $envPath = Join-Path $ProjectRoot '.env'
    if (Test-Path -LiteralPath $envPath) {
        foreach ($line in Get-Content -LiteralPath $envPath) {
            if ($line -match '^\s*(#|$)') { continue }
            if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw 'Invalid .env line. Expected NAME=value.' }
            # Values are data: never evaluate .env content as PowerShell.
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
}

function Initialize-LocalEnvironment {
    $envPath = Join-Path $ProjectRoot '.env'
    if (-not (Test-Path -LiteralPath $envPath)) { Copy-Item -LiteralPath (Join-Path $ProjectRoot '.env.example') -Destination $envPath }
    $lines = @(Get-Content -LiteralPath $envPath)
    if (-not ($lines | Where-Object { $_ -match '^JWT_SECRET=.{40,}$' })) {
        $randomBytes = New-Object byte[] 48
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
        $lines = @($lines | Where-Object { $_ -notmatch '^JWT_SECRET=' }) + ('JWT_SECRET=' + [Convert]::ToBase64String($randomBytes))
        $lines | Set-Content -LiteralPath $envPath -Encoding utf8
    }
}
