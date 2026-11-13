param([string]$JavaHome)
. (Join-Path $PSScriptRoot 'Common.ps1')
Use-ProjectJava -JavaHome $JavaHome
Initialize-LocalEnvironment
Import-ProjectEnvironment
Push-Location (Join-Path $ProjectRoot 'backend')
try {
    & mvn.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
    if ($LASTEXITCODE -ne 0) { throw 'Backend stopped with an error. Check MongoDB and the log above.' }
} finally { Pop-Location }
