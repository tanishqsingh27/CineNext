. (Join-Path $PSScriptRoot 'Common.ps1')
$pidPath=Join-Path $ProjectRoot '.runtime/mongo.pid'
if(-not(Test-Path -LiteralPath $pidPath)) { throw 'No project-owned MongoDB PID file was found.' }
$mongoProcessId=[int](Get-Content -LiteralPath $pidPath)
$process=Get-CimInstance Win32_Process -Filter "ProcessId = $mongoProcessId"
$expectedPath=Join-Path $ProjectRoot '.runtime\mongo'
if(-not $process) { Write-Host 'Project MongoDB is already stopped.';return }
if($process.Name -ne 'mongod.exe' -or -not $process.CommandLine.Contains($expectedPath) -or -not $process.CommandLine.Contains('27018')) { throw 'PID no longer identifies this project MongoDB process. Nothing was stopped.' }
& mongosh.exe 'mongodb://127.0.0.1:27018/admin?directConnection=true' --quiet --eval 'db.shutdownServer()'
Write-Host 'Shutdown requested for the project replica set. Database files are retained.'
