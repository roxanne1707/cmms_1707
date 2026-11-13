$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$pidFile=Join-Path $repo 'local-runtime\maintenance-frontend\frontend.pid'
if(-not(Test-Path -LiteralPath $pidFile)){throw 'No isolated frontend PID record exists.'}
$frontendPid=[int](Get-Content -LiteralPath $pidFile)
$process=Get-CimInstance Win32_Process -Filter "ProcessId=$frontendPid"
if(-not $process){Write-Output 'Isolated frontend is already stopped.';exit 0}
$expected=Join-Path $repo 'scripts\serve-maintenance-frontend.cjs'
if(-not $process.CommandLine -or -not $process.CommandLine.Contains($expected)){
    throw 'PID belongs to a different process; refusing to stop it.'
}
Stop-Process -Id $frontendPid
Write-Output 'Stopped only the verified isolated frontend; no deployment or database was removed.'
