param([string]$Maven='mvn',[string]$Settings='',[Parameter(Mandatory=$true)][string]$CredentialFile,[string]$Tests='*Test,*Tests')
$ErrorActionPreference='Stop'
$access=Get-Content -LiteralPath $CredentialFile -Raw | ConvertFrom-Json
if($access.username -ne 'cmms_mysql_test'){throw 'Only the isolated MySQL test account is accepted.'}
$repo=Split-Path $PSScriptRoot -Parent
$arguments=@('-B','test',"-Dtest=$Tests",'-Dcmms.test.jdbc-url=jdbc:mysql://127.0.0.1:23306/cmms_mysql_test',"-Dcmms.test.jdbc-user=$($access.username)","-Dcmms.test.jdbc-password=$($access.password)",'-Dcmms.test.redis-host=127.0.0.1','-Dcmms.test.redis-port=26379','-Dcmms.test.rabbit-host=127.0.0.1','-Dcmms.test.rabbit-port=25672','-Dcmms.test.rabbit-user=cmms_test','-Dcmms.test.rabbit-password=cmms_test_local')
if($Settings){$arguments=@('-s',$Settings)+$arguments}
Push-Location (Join-Path $repo 'api')
try{& $Maven @arguments;if($LASTEXITCODE -ne 0){throw 'MySQL regression failed; inspect Surefire reports.'}}finally{Pop-Location}
