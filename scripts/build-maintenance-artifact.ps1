param([string]$Maven='mvn',[string]$Settings='',[switch]$SkipTests)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'maintenance-artifact.ps1')
$name='app-maintenance-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$runtime=Join-Path $repo 'local-runtime/maintenance-artifacts'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
$arguments=@('-B','package',"-Dcmms.artifact-name=$name")
if($Settings){$arguments=@('-s',$Settings)+$arguments}
# For the documented release sequence only: full database regressions run separately before packaging.
if($SkipTests){$arguments+='-DskipTests'}
Push-Location (Join-Path $repo 'api')
try { & $Maven @arguments; if($LASTEXITCODE -ne 0){throw 'Packaging failed. The previous artifact pointer was preserved.'} }
finally {Pop-Location}
$jar=Join-Path $repo ('api/target/'+$name+'.jar')
Assert-MaintenanceBootJar $jar
$info=@{file=$name+'.jar';sha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash;builtAtUtc=[DateTime]::UtcNow.ToString('o');testsSkipped=[bool]$SkipTests}
$temporary=Join-Path $runtime ($name+'.json')
$info | ConvertTo-Json | Set-Content -LiteralPath $temporary -Encoding utf8
$pointer=Join-Path $runtime 'current.json'
# Both paths are fixed files in the isolated runtime directory, not directories or user data.
Move-Item -LiteralPath $temporary -Destination $pointer -Force
Write-Output "Verified immutable artifact: $jar"
