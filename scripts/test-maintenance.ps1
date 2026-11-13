param([string]$Maven = 'mvn', [string]$Settings = '', [string]$RabbitUser = 'cmms_test', [string]$RabbitPassword = 'cmms_test_local')
$ErrorActionPreference='Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$arguments = @('-B','test',
 '-Dcmms.test.jdbc-url=jdbc:postgresql://127.0.0.1:25432/cmms_enhancement_test','-Dcmms.test.jdbc-user=cmms_test','-Dcmms.test.jdbc-password=cmms_test_local',
 '-Dcmms.test.redis-host=127.0.0.1','-Dcmms.test.redis-port=26379','-Dcmms.test.rabbit-host=127.0.0.1','-Dcmms.test.rabbit-port=25672',
 "-Dcmms.test.rabbit-user=$RabbitUser","-Dcmms.test.rabbit-password=$RabbitPassword")
if($Settings){$arguments=@('-s',$Settings)+$arguments}
Push-Location (Join-Path $repoRoot 'api')
try { & $Maven @arguments; if($LASTEXITCODE -ne 0){throw "Maintenance tests failed: $LASTEXITCODE"} } finally { Pop-Location }
