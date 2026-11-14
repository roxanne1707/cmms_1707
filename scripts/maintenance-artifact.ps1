# Shared by isolated launchers. Never trust a thin Maven jar or an artifact outside this checkout.
function Assert-MaintenanceBootJar([string]$Path) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entry=$zip.GetEntry('META-INF/MANIFEST.MF')
        if(-not $entry){throw 'Missing JAR manifest.'}
        $reader=[IO.StreamReader]::new($entry.Open())
        try {$manifest=$reader.ReadToEnd()} finally {$reader.Dispose()}
        if($manifest -notmatch 'Main-Class: org.springframework.boot.loader.launch.JarLauncher' -or
           $manifest -notmatch 'Start-Class: com.grash.ApiApplication' -or
           -not $zip.GetEntry('BOOT-INF/classes/com/grash/ApiApplication.class') -or
           -not ($zip.Entries | Where-Object {$_.FullName.StartsWith('BOOT-INF/lib/')} | Select-Object -First 1)) {
            throw 'Not an executable CMMS Spring Boot jar. Run scripts/build-maintenance-artifact.ps1.'
        }
    } finally {$zip.Dispose()}
}
function Resolve-MaintenanceArtifact([string]$Repo) {
    $pointer=Join-Path $Repo 'local-runtime/maintenance-artifacts/current.json'
    if(-not(Test-Path -LiteralPath $pointer)){throw 'Build a verified isolated artifact with scripts/build-maintenance-artifact.ps1 first.'}
    $info=Get-Content -LiteralPath $pointer -Raw | ConvertFrom-Json
    if($info.file -notmatch '^app-maintenance-[0-9]{8}T[0-9]{9}Z-[a-f0-9]{8}[.]jar$'){throw 'Invalid artifact filename.'}
    $jar=Join-Path $Repo ('api/target/'+$info.file)
    if(-not(Test-Path -LiteralPath $jar)){throw 'Verified artifact missing; rebuild it.'}
    if((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $info.sha256){throw 'Artifact checksum mismatch; rebuild it.'}
    Assert-MaintenanceBootJar $jar
    return $jar
}

# PowerShell 7 may deserialize ISO JSON timestamps as DateTime; Windows PowerShell
# keeps a string. Do not Parse a DateTime again: implicit formatting loses its zone.
function Get-MaintenanceProcessStartUtc([object]$Value) {
    if($Value -is [DateTimeOffset]) { return $Value.UtcDateTime }
    if($Value -is [DateTime]) {
        if($Value.Kind -eq [DateTimeKind]::Unspecified) { throw 'Process timestamp has no timezone.' }
        return $Value.ToUniversalTime()
    }
    if($Value -isnot [string] -or $Value -notmatch 'T.*(Z|[+-][0-9]{2}:[0-9]{2})$') {
        throw 'Process timestamp must include an explicit timezone.'
    }
    return [DateTimeOffset]::Parse($Value,[Globalization.CultureInfo]::InvariantCulture).UtcDateTime
}
