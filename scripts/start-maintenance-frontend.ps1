param([string]$Node = 'node')
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$script=Join-Path $repo 'scripts\serve-maintenance-frontend.cjs'
$build=Join-Path $repo 'frontend\build\index.html'
if(-not(Test-Path -LiteralPath $build)){throw 'Build the fork frontend first.'}
$probe=[Net.Sockets.TcpClient]::new()
try {$probe.Connect('127.0.0.1',3289);throw 'Port 3289 is in use; no existing process was changed.'}
catch [Net.Sockets.SocketException] {} finally {$probe.Dispose()}
$runtime=Join-Path $repo 'local-runtime\maintenance-frontend'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$process=Start-Process -FilePath $Node -ArgumentList ('"'+$script+'"') -WorkingDirectory $repo -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $runtime 'frontend.log') -RedirectStandardError (Join-Path $runtime 'frontend.err.log')
Set-Content -LiteralPath (Join-Path $runtime 'frontend.pid') -Value $process.Id -Encoding ascii
Write-Output "Isolated frontend starting at http://127.0.0.1:3289 ; PID $($process.Id); API proxy targets only 28089."
