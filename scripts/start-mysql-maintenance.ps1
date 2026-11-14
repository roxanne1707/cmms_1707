param([string]$Java='java',[string]$Node='node',[switch]$Frontend,[switch]$EnableScheduling)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$runtime=Join-Path $repo 'local-runtime\mysql-maintenance'
$accessPath=Join-Path $runtime 'access.json'
if(-not(Test-Path -LiteralPath $accessPath)){throw 'Provision the isolated MySQL lab first; see docs/mysql-maintenance-acceptance.md. Never point this script to an existing deployment.'}
$access=Get-Content -LiteralPath $accessPath -Raw | ConvertFrom-Json
try {if([Convert]::FromBase64String($access.jwtSecret).Length -lt 32){throw 'Key is too short'}} catch {throw 'The isolated JWT key must be standard Base64 containing at least 32 random bytes.'}
function Assert-FreePort([int]$Port){
  $probe=[Net.Sockets.TcpClient]::new()
  try {$probe.Connect('127.0.0.1',$Port);throw "Port $Port is occupied; no process was changed."}
  catch [Net.Sockets.SocketException] {} finally {$probe.Dispose()}
}
if($Frontend){
  Assert-FreePort 3389
  if(-not(Test-Path -LiteralPath (Join-Path $repo 'frontend\build\index.html'))){throw 'Build frontend first.'}
  $env:CMMS_PREVIEW_DATABASE='mysql'
  $process=Start-Process -FilePath $Node -ArgumentList ('"'+(Join-Path $PSScriptRoot 'serve-maintenance-frontend.cjs')+'"') -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'frontend.log') -RedirectStandardError (Join-Path $runtime 'frontend.err.log')
  Set-Content -LiteralPath (Join-Path $runtime 'frontend.pid') -Value $process.Id -Encoding ascii
  Write-Output "MySQL preview starting: http://127.0.0.1:3389 ; PID $($process.Id)"
  exit
}
Assert-FreePort 28189
. (Join-Path $PSScriptRoot 'maintenance-artifact.ps1')
$jar=Resolve-MaintenanceArtifact $repo
. (Join-Path $PSScriptRoot 'maintenance-environment.ps1')
Set-MaintenanceDatabaseEnvironment -Database mysql -MySqlAccess $access
$env:CMMS_BOOTSTRAP_ADMINEMAIL=$access.adminEmail;$env:CMMS_BOOTSTRAP_ADMINPASSWORD=$access.adminPassword
$env:CMMS_BOOTSTRAP_ADMIN_EMAIL=$access.adminEmail;$env:CMMS_BOOTSTRAP_ADMIN_PASSWORD=$access.adminPassword
# Separate pre-counter cache entries on the first additive epoch migration.
$env:MYSQL_CACHE_NAMESPACE='cmms-mysql-lab-epoch-v2'
$env:JWT_SECRET_KEY=$access.jwtSecret;$env:CMMS_LAB_METRICS_PASSWORD=$access.metricsPassword
$env:PUBLIC_FRONT_URL='http://127.0.0.1:3389';$env:PUBLIC_API_URL='http://127.0.0.1:28189'
$env:MAIL_RECIPIENTS='test@example.invalid';$env:STORAGE_TYPE='MINIO'
$env:ENABLE_EMAIL_NOTIFICATIONS='false';$env:ENABLE_MAIL_HEALTH_CHECK='false'
$env:LDAP_ENABLED='false';$env:ENABLE_SSO='false';$env:CLOUD_VERSION='false'
$env:LAB_RABBIT_USER='cmms_test';$env:LAB_RABBIT_PASSWORD='cmms_test_local'
foreach($name in @('LICENSE_KEY','LICENSE_FILE_PATH','SENTRY_DSN','INTERCOM_TOKEN','PADDLE_API_KEY','SENDGRID_API_KEY','SMTP_USER','SMTP_PWD','MINIO_ENDPOINT','MINIO_BUCKET','MINIO_ACCESS_KEY','MINIO_SECRET_KEY','PUBLIC_MINIO_ENDPOINT','GCP_JSON','GCP_JSON_PATH','LOGO_PATHS','OAUTH2_CLIENT_ID','OAUTH2_CLIENT_SECRET')){
  [Environment]::SetEnvironmentVariable($name,'','Process')
}
$arguments=@('-Xmx512m','-Duser.timezone=UTC','-jar',('"'+$jar+'"'),
  '--spring.profiles.active=maintenance-lab,mysql','--server.port=28189','--server.address=127.0.0.1',
  '--spring.data.redis.database=1','--spring.rabbitmq.virtual-host=cmms-mysql',
  '--cmms.events.external-delivery-enabled=false','--mail.enable=false',
  '--spring.sql.init.mode=never','--spring.data.redis.repositories.enabled=false',
  '--spring.quartz.scheduler-name=cmms-mysql-lab',
  '--spring.quartz.properties.org.quartz.scheduler.instanceId=MYSQL_LAB_28189')
if($EnableScheduling){$arguments+='--spring.quartz.auto-startup=true'}
$process=Start-Process -FilePath $Java -ArgumentList $arguments -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'api.log') -RedirectStandardError (Join-Path $runtime 'api.err.log')
Set-Content -LiteralPath (Join-Path $runtime 'api.pid') -Value $process.Id -Encoding ascii
@{pid=$process.Id;jar=$jar;port=28189;startedAtUtc=$process.StartTime.ToUniversalTime().ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime 'api.process.json') -Encoding utf8
Write-Output "MySQL API starting: http://127.0.0.1:28189 ; PID $($process.Id); only cmms_mysql_lab on :23306."
