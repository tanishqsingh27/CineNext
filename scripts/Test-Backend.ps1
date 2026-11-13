param([string]$JavaHome, [switch]$Integration)
. (Join-Path $PSScriptRoot 'Common.ps1')
Use-ProjectJava -JavaHome $JavaHome
Push-Location (Join-Path $ProjectRoot 'backend')
try {
    $arguments = @('-B', 'verify')
    if ($Integration) { $arguments += '-Pmongo-integration' }
    & mvn.cmd @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Backend checks failed.' }
} finally { Pop-Location }
