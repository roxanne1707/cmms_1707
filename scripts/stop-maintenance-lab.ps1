param([ValidateSet(28089,28090,28189)][int]$Port = 28089)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'maintenance-artifact.ps1')
$repo=Split-Path $PSScriptRoot -Parent
$runtime=if($Port -eq 28189){Join-Path $repo 'local-runtime/mysql-maintenance'}else{Join-Path $repo "local-runtime/maintenance-lab-$Port"}
$pidFile=Join-Path $runtime 'api.pid'
if(-not(Test-Path -LiteralPath $pidFile)){throw 'No lab API PID record exists.'}
$labPid=[int](Get-Content -LiteralPath $pidFile)
$process=Get-CimInstance Win32_Process -Filter "ProcessId=$labPid"
if(-not $process){Write-Output 'Lab API is already stopped.';exit 0}
$recordPath=Join-Path $runtime 'api.process.json'
if(Test-Path -LiteralPath $recordPath) {
    $record=Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
    $expectedJar=[IO.Path]::GetFullPath($record.jar)
    $expectedDirectory=[IO.Path]::GetFullPath((Join-Path $repo 'api/target'))
    if([IO.Path]::GetDirectoryName($expectedJar) -ne $expectedDirectory -or
       [IO.Path]::GetFileName($expectedJar) -notmatch '^app-maintenance-[0-9]{8}T[0-9]{9}Z-[a-f0-9]{8}[.]jar$' -or
       $record.pid -ne $labPid -or $record.port -ne $Port -or
       [Math]::Abs(($process.CreationDate.ToUniversalTime()-(Get-MaintenanceProcessStartUtc $record.startedAtUtc)).TotalSeconds) -gt 2) {
        throw 'Test process identity changed; refusing to stop.'
    }
} else {
    # Compatibility only for the previous verified test launchers; never use a deployment path.
    $name=if($Port -eq 28189){'app-mysql.jar'}else{'app.jar'}
    $expectedJar=Join-Path $repo ('api/target/'+$name)
}
if($process.Name -ne 'java.exe' -or -not $process.CommandLine.Contains($expectedJar) -or
   -not $process.CommandLine.Contains('--spring.profiles.active=maintenance-lab') -or
   -not $process.CommandLine.Contains("--server.port=$Port")){
    throw 'PID belongs to a different process; refusing to stop it.'
}
Stop-Process -Id $labPid
Write-Output "Stopped only the verified lab API on $Port; no database or file was removed."
