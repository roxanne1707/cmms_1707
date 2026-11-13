$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$xmlFile=Join-Path $repo 'api\target\surefire-reports\TEST-com.grash.aspect.TenantAspectTest.xml'
if(!(Test-Path $xmlFile)){throw '请先按审计报告运行指定 Maven 单元测试，生成测试 classpath。'}
if(!$env:JAVA_HOME){throw '请先将 JAVA_HOME 指向本机 JDK 17。'}
$xml=[xml](Get-Content $xmlFile -Raw)
$cp=($xml.testsuite.properties.property|Where-Object {$_.name-eq 'java.class.path'}).value
if(!$cp){throw '未找到测试 classpath。'}
$output=Join-Path $repo 'local-runtime\audit-probes'
New-Item -ItemType Directory -Force -Path $output|Out-Null
$source=Join-Path $PSScriptRoot 'CmmsAuditProbe.java'
$compileArgs=Join-Path $output 'compile.args'
@('-proc:none','-encoding','UTF-8','-d',('"'+$output.Replace('\','/')+'"'),'-cp',('"'+$cp.Replace('\','/')+'"'),('"'+$source.Replace('\','/')+'"'))|Set-Content $compileArgs
& "$env:JAVA_HOME\bin\javac.exe" "@$compileArgs"
if($LASTEXITCODE-ne 0){throw '审计探针编译失败'}
$runArgs=Join-Path $output 'run.args'
@('-Xmx256m','--add-opens=java.base/java.lang=ALL-UNNAMED','-cp',('"'+$output.Replace('\','/')+';'+$cp.Replace('\','/')+'"'),'CmmsAuditProbe')|Set-Content $runArgs
& "$env:JAVA_HOME\bin\java.exe" "@$runArgs"
if($LASTEXITCODE-ne 0){throw '审计探针断言失败'}
