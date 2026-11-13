$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $projectPath 'frontend')
try {
    & npm.cmd run dev -- --host 127.0.0.1
    if ($LASTEXITCODE -ne 0) { throw 'Frontend stopped with an error.' }
} finally { Pop-Location }
