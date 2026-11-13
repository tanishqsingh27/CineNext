param([string]$JavaHome)
. (Join-Path $PSScriptRoot 'Common.ps1')
Use-ProjectJava -JavaHome $JavaHome
if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'Node.js 22.12+ and npm are required.' }
$nodeVersion = [version]((& node --version).TrimStart('v'))
if ($nodeVersion.Major -lt 22 -or ($nodeVersion.Major -eq 22 -and $nodeVersion.Minor -lt 12)) { throw 'Use Node.js 22.12+ or a newer supported LTS release.' }
if (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot '.env'))) {
    Copy-Item -LiteralPath (Join-Path $ProjectRoot '.env.example') -Destination (Join-Path $ProjectRoot '.env')
}
Push-Location (Join-Path $ProjectRoot 'frontend')
try {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
} finally { Pop-Location }
Push-Location (Join-Path $ProjectRoot 'backend')
try {
    & mvn.cmd -B verify
    if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed.' }
} finally { Pop-Location }
Write-Host 'Dependencies, frontend build, and backend verification complete. Start the API and frontend in separate terminals; see README.md.'
