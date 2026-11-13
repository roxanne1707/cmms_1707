param([ValidateSet(28089,28090)][int]$Port = 28089)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$pidFile=Join-Path $repo "local-runtime\maintenance-lab-$Port\api.pid"
if(-not(Test-Path -LiteralPath $pidFile)){throw 'No lab API PID record exists.'}
$labPid=[int](Get-Content -LiteralPath $pidFile)
$process=Get-CimInstance Win32_Process -Filter "ProcessId=$labPid"
if(-not $process){Write-Output 'Lab API is already stopped.';exit 0}
$expectedJar=Join-Path $repo 'api\target\app.jar'
if(-not $process.CommandLine.Contains($expectedJar) -or -not $process.CommandLine.Contains('--spring.profiles.active=maintenance-lab') -or -not $process.CommandLine.Contains("--server.port=$Port")){
    throw 'PID belongs to a different process; refusing to stop it.'
}
Stop-Process -Id $labPid
Write-Output "Stopped only the verified lab API on $Port; no database or file was removed."
