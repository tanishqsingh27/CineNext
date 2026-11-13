. (Join-Path $PSScriptRoot 'Common.ps1')
if (-not (Get-Command mongod.exe -ErrorAction SilentlyContinue) -or -not (Get-Command mongosh.exe -ErrorAction SilentlyContinue)) { throw 'Install MongoDB Community and mongosh on PATH first.' }
$runtimePath = Join-Path $ProjectRoot '.runtime'
$databasePath = Join-Path $runtimePath 'mongo'
New-Item -ItemType Directory -Path $databasePath -Force | Out-Null
$listener = Get-NetTCPConnection -LocalPort 27018 -State Listen -ErrorAction SilentlyContinue
if (-not $listener) {
    $mongoArguments = @('--dbpath', ('"' + $databasePath + '"'), '--port', '27018', '--bind_ip', '127.0.0.1', '--replSet', 'cinenext-rs', '--logpath', ('"' + (Join-Path $runtimePath 'mongo.log') + '"'), '--logappend')
    $mongoProcess = Start-Process -FilePath (Get-Command mongod.exe).Source -ArgumentList $mongoArguments -WindowStyle Hidden -PassThru
    $mongoProcess.Id | Set-Content -LiteralPath (Join-Path $runtimePath 'mongo.pid')
}
$probe = 'const h=db.hello(); if(h.setName && h.setName!=="cinenext-rs") quit(2); if(!h.setName) { const o=db.adminCommand({getCmdLineOpts:1}); if((o.parsed.replication?.replSetName ?? o.parsed.replication?.replSet)!=="cinenext-rs") quit(2); const r=rs.initiate({_id:"cinenext-rs",members:[{_id:0,host:"127.0.0.1:27018"}]}); if(!r.ok) quit(3); }'
$ready = $false
for ($attempt=0; $attempt -lt 20; $attempt++) {
    & mongosh.exe 'mongodb://127.0.0.1:27018/admin?directConnection=true&serverSelectionTimeoutMS=1500' --quiet --eval $probe 2>$null | Out-Null
    if ($LASTEXITCODE -eq 2) { throw 'Port 27018 belongs to a different MongoDB configuration; it was not modified.' }
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Milliseconds 500
}
if (-not $ready) { throw 'MongoDB failed to start. Inspect .runtime/mongo.log.' }
for ($attempt=0; $attempt -lt 20; $attempt++) {
    & mongosh.exe 'mongodb://127.0.0.1:27018/admin?directConnection=true&serverSelectionTimeoutMS=1500' --quiet --eval 'if(!db.hello().isWritablePrimary) quit(1)' 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { Write-Host 'CineNext MongoDB replica set ready on 127.0.0.1:27018. Existing MongoDB service was not changed.'; return }
    Start-Sleep -Milliseconds 500
}
throw 'Replica set has not elected a primary. Inspect .runtime/mongo.log.'
