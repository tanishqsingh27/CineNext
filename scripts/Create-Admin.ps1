param([string]$JavaHome)
. (Join-Path $PSScriptRoot 'Common.ps1')
Use-ProjectJava -JavaHome $JavaHome
Initialize-LocalEnvironment
Import-ProjectEnvironment
$adminEmail = Read-Host 'New administrator email (must not already be registered)'
$securePassword = Read-Host 'New password (10-72 characters; not printed)' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:BOOTSTRAP_ADMIN_EMAIL = $adminEmail
    $env:BOOTSTRAP_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    Push-Location (Join-Path $ProjectRoot 'backend')
    try {
        & mvn.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.port=0 --cinenext.bootstrap-admin=true'
        if ($LASTEXITCODE -ne 0) { throw 'Administrator creation failed. An existing email is never promoted or overwritten.' }
    } finally { Pop-Location }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    Remove-Item Env:BOOTSTRAP_ADMIN_EMAIL -ErrorAction SilentlyContinue
    Remove-Item Env:BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
}
