$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'maintenance-artifact.ps1')
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('cmms-artifact-test-'+[Guid]::NewGuid().ToString('N'))
$target=Join-Path $fixture 'api/target'
$runtime=Join-Path $fixture 'local-runtime/maintenance-artifacts'
New-Item -ItemType Directory -Path $target,$runtime -Force | Out-Null
$file='app-maintenance-20261114T010203456Z-1234abcd.jar'
$jar=Join-Path $target $file
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::Open($jar,[IO.Compression.ZipArchiveMode]::Create)
try {
 foreach($entry in @('META-INF/MANIFEST.MF','BOOT-INF/classes/com/grash/ApiApplication.class','BOOT-INF/lib/dependency.jar')) {
  $writer=[IO.StreamWriter]::new($zip.CreateEntry($entry).Open())
  try {$writer.Write("Main-Class: org.springframework.boot.loader.launch.JarLauncher`nStart-Class: com.grash.ApiApplication`n")} finally {$writer.Dispose()}
 }
} finally {$zip.Dispose()}
$pointer=Join-Path $runtime 'current.json'
$hash=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
@{file=$file;sha256=$hash}|ConvertTo-Json|Set-Content -LiteralPath $pointer
if((Resolve-MaintenanceArtifact $fixture) -ne $jar){throw 'Valid artifact was not selected'}
$passed=1
function Must-Reject([scriptblock]$Action) { $rejected=$false;try {& $Action | Out-Null} catch {$rejected=$true};if(-not $rejected){throw 'Expected rejection'} }
@{file=$file;sha256=('0'*64)}|ConvertTo-Json|Set-Content -LiteralPath $pointer
Must-Reject {Resolve-MaintenanceArtifact $fixture};$passed++
@{file='../app.jar';sha256=$hash}|ConvertTo-Json|Set-Content -LiteralPath $pointer
Must-Reject {Resolve-MaintenanceArtifact $fixture};$passed++
$thin=Join-Path $target 'thin.jar'
$zip=[IO.Compression.ZipFile]::Open($thin,[IO.Compression.ZipArchiveMode]::Create)
try {$null=$zip.CreateEntry('ordinary.class')}finally{$zip.Dispose()}
Must-Reject {Assert-MaintenanceBootJar $thin};$passed++
Must-Reject {Resolve-MaintenanceArtifact (Join-Path $fixture 'missing')};$passed++
$expectedUtc=[DateTimeOffset]::Parse('2026-11-14T00:46:33.5223729Z').UtcDateTime
foreach($value in @('2026-11-14T00:46:33.5223729Z', $expectedUtc, '2026-11-14T08:46:33.5223729+08:00')) {
 if((Get-MaintenanceProcessStartUtc $value) -ne $expectedUtc){throw 'Process timestamp lost its timezone'}
 $passed++
}
Must-Reject {Get-MaintenanceProcessStartUtc '2026-11-14T00:46:33'};$passed++
@{passed=$passed;failed=0;scope='artifact checksum, executable layout, traversal, missing pointer and process timestamp timezone'}|ConvertTo-Json
# The tiny, uniquely named diagnostic fixture is retained; no recursive cleanup of shared temp paths.
