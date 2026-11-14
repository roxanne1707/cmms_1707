param(
    [string]$Java = 'java',
    [ValidateSet(28089,28090)][int]$Port = 28089,
    [string]$RabbitUser = 'cmms_test',
    [string]$RabbitPassword = 'cmms_test_local',
    [switch]$NoQueryCache,
    [switch]$PerformanceMode,
    [switch]$EnableScheduling,
    [switch]$NoBackgroundWorkers
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'maintenance-artifact.ps1')
$jar = Resolve-MaintenanceArtifact $repo
$runtime = Join-Path $repo "local-runtime\maintenance-lab-$Port"
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
$probe = [System.Net.Sockets.TcpClient]::new()
try { $probe.Connect('127.0.0.1',$Port); throw "Port $Port is already in use; no process was changed." }
catch [System.Net.Sockets.SocketException] { } finally { $probe.Dispose() }

# Dedicated test connection and explicit disabling of all real external credentials/providers.
. (Join-Path $PSScriptRoot 'maintenance-environment.ps1')
Set-MaintenanceDatabaseEnvironment -Database postgresql
$keyPath = Join-Path $runtime 'jwt.key'
if (-not (Test-Path -LiteralPath $keyPath)) {
    $bytes = New-Object byte[] 64
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [IO.File]::WriteAllText($keyPath, [Convert]::ToBase64String($bytes))
}
$env:JWT_SECRET_KEY = [IO.File]::ReadAllText($keyPath).Trim()
$metricsKeyPath = Join-Path $runtime 'metrics.key'
if (-not (Test-Path -LiteralPath $metricsKeyPath)) {
    [IO.File]::WriteAllText($metricsKeyPath, [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N'))
}
$env:CMMS_LAB_METRICS_PASSWORD = [IO.File]::ReadAllText($metricsKeyPath).Trim()
$env:PUBLIC_FRONT_URL = "http://127.0.0.1:$Port"; $env:PUBLIC_API_URL = "http://127.0.0.1:$Port"
$env:MAIL_RECIPIENTS = 'test@example.invalid'; $env:STORAGE_TYPE = 'MINIO'
$env:ENABLE_EMAIL_NOTIFICATIONS = 'false'; $env:ENABLE_MAIL_HEALTH_CHECK = 'false'
$env:LDAP_ENABLED = 'false'; $env:ENABLE_SSO = 'false'; $env:CLOUD_VERSION = 'false'
foreach ($name in @('LICENSE_KEY','LICENSE_FILE_PATH','SENTRY_DSN','INTERCOM_TOKEN','PADDLE_API_KEY',
    'SENDGRID_API_KEY','SMTP_USER','SMTP_PWD','MINIO_ENDPOINT','MINIO_BUCKET','MINIO_ACCESS_KEY','MINIO_SECRET_KEY',
    'PUBLIC_MINIO_ENDPOINT','GCP_JSON','GCP_JSON_PATH','LOGO_PATHS','OAUTH2_CLIENT_ID','OAUTH2_CLIENT_SECRET')) {
    [Environment]::SetEnvironmentVariable($name, '', 'Process')
}
$env:LAB_RABBIT_USER = $RabbitUser; $env:LAB_RABBIT_PASSWORD = $RabbitPassword
$cache = if ($NoQueryCache) { 'false' } else { 'true' }
$arguments = @('-Xmx512m','-Duser.timezone=UTC','-jar',('"'+$jar+'"'),
    '--spring.profiles.active=maintenance-lab',"--server.port=$Port",'--server.address=127.0.0.1',
    "--enhancement.query.cache.enabled=$cache",'--cmms.events.external-delivery-enabled=false',
    '--spring.data.redis.repositories.enabled=false',
    '--management.metrics.distribution.percentiles-histogram.http.server.requests=true',
    '--spring.quartz.scheduler-name=cmms-enhancement-proof',
    "--spring.quartz.properties.org.quartz.scheduler.instanceId=LAB_$Port")
if ($NoBackgroundWorkers) { $arguments += @('--cmms.events.workers-enabled=false','--cmms.events.listener-auto-startup=false') }
# Benchmark mode disables ONLY the synthetic lab's per-user HTTP quota; authentication remains enabled.
if ($EnableScheduling) { $arguments += '--spring.quartz.auto-startup=true' }
if ($PerformanceMode) { $arguments += '--security.rate-limit.enabled=false' }
$process = Start-Process -FilePath $Java -ArgumentList $arguments -WorkingDirectory $repo -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $runtime 'api.log') -RedirectStandardError (Join-Path $runtime 'api.err.log')
Set-Content -LiteralPath (Join-Path $runtime 'api.pid') -Value $process.Id -Encoding ascii
@{pid=$process.Id;jar=$jar;port=$Port;startedAtUtc=$process.StartTime.ToUniversalTime().ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime 'api.process.json') -Encoding utf8
Write-Output "Lab API process $($process.Id) starting at http://127.0.0.1:$Port ; logs: $runtime"
