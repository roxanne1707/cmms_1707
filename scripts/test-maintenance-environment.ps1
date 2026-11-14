$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'maintenance-environment.ps1')
$before=[Environment]::GetEnvironmentVariables('Process')
$passed=0
try {
    $env:SPRING_LIQUIBASE_URL='jdbc:mysql://not-a-lab/shared'
    $env:SPRING_DATASOURCE_URL='jdbc:mysql://not-a-lab/shared'
    $env:SPRING_APPLICATION_JSON='{"spring":{"datasource":{"url":"jdbc:mysql://not-a-lab/shared"}}}'
    $env:CMMS_BOOTSTRAP_ADMINEMAIL='foreign@example.invalid'
    Set-MaintenanceDatabaseEnvironment -Database postgresql
    if($env:SPRING_DATASOURCE_URL -ne 'jdbc:postgresql://127.0.0.1:25432/cmms_enhancement_test' -or
       $env:SPRING_LIQUIBASE_URL -ne $env:SPRING_DATASOURCE_URL -or $env:CMMS_DATABASE -ne 'postgresql' -or
       (Test-Path Env:SPRING_APPLICATION_JSON) -or (Test-Path Env:SPRING_CONFIG_LOCATION) -or $env:CMMS_BOOTSTRAP_ADMINEMAIL){throw 'PostgreSQL isolation settings leaked.'}
    $passed++
    $access=[pscustomobject]@{runtimeUser='fixture-runtime';runtimePassword='fixture-runtime-only';migrationUser='fixture-migration';migrationPassword='fixture-migration-only'}
    Set-MaintenanceDatabaseEnvironment -Database mysql -MySqlAccess $access
    if($env:SPRING_DATASOURCE_URL -notlike 'jdbc:mysql://127.0.0.1:23306/cmms_mysql_lab?*' -or
       $env:SPRING_LIQUIBASE_URL -ne $env:SPRING_DATASOURCE_URL -or $env:SPRING_DATASOURCE_USERNAME -ne $access.runtimeUser -or
       $env:SPRING_LIQUIBASE_USER -ne $access.migrationUser -or $env:SPRING_LIQUIBASE_CHANGE_LOG -ne 'classpath:/db/mysql/master.xml'){throw 'MySQL isolation settings leaked.'}
    $passed++
    Set-MaintenanceDatabaseEnvironment -Database postgresql
    if($env:SPRING_LIQUIBASE_URL -ne 'jdbc:postgresql://127.0.0.1:25432/cmms_enhancement_test' -or
       $env:SPRING_LIQUIBASE_USER -ne 'cmms_test' -or $env:SPRING_DATASOURCE_USERNAME -ne 'cmms_test' -or
       $env:SPRING_LIQUIBASE_CHANGE_LOG -ne 'classpath:/db/master.xml' -or $env:SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT -ne 'org.hibernate.dialect.PostgreSQLDialect'){throw 'MySQL -> PostgreSQL transition leaked.'}
    $passed++
    @{passed=$passed;failed=0;checks=@('reject inherited connection/config redirection','PostgreSQL to MySQL keeps runtime/migration identities','MySQL to PostgreSQL resets both connections and dialect')}|ConvertTo-Json
} finally {
    foreach($key in @([Environment]::GetEnvironmentVariables('Process').Keys)) {
        if(-not $before.Contains($key)){Remove-Item -LiteralPath ('Env:'+ $key) -ErrorAction SilentlyContinue}
    }
    foreach($key in $before.Keys){[Environment]::SetEnvironmentVariable($key,$before[$key],'Process')}
}
