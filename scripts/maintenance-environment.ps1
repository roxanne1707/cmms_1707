# Both launchers own their database settings, even when invoked in the same shell.
function Set-MaintenanceDatabaseEnvironment {
    param([ValidateSet('postgresql','mysql')][string]$Database,[object]$MySqlAccess)
    # Imported/global Spring configuration must not redirect an isolated launcher.
    foreach($name in @('SPRING_APPLICATION_JSON','SPRING_CONFIG_LOCATION','SPRING_CONFIG_ADDITIONAL_LOCATION','SPRING_CONFIG_IMPORT',
                       'SPRING_LIQUIBASE_DEFAULT_SCHEMA','SPRING_LIQUIBASE_LIQUIBASE_SCHEMA')) {
        Remove-Item -LiteralPath ('Env:'+ $name) -ErrorAction SilentlyContinue
    }
    if($Database -eq 'postgresql') {
        $url='jdbc:postgresql://127.0.0.1:25432/cmms_enhancement_test'
        $user='cmms_test';$password='cmms_test_local';$migrationUser=$user;$migrationPassword=$password
        $driver='org.postgresql.Driver';$dialect='org.hibernate.dialect.PostgreSQLDialect';$changelog='classpath:/db/master.xml'
        $env:DB_URL='127.0.0.1:25432/cmms_enhancement_test';$env:DB_USER=$user;$env:DB_PWD=$password
        foreach($name in @('CMMS_BOOTSTRAP_ADMINEMAIL','CMMS_BOOTSTRAP_ADMINPASSWORD','CMMS_BOOTSTRAP_ADMIN_EMAIL','CMMS_BOOTSTRAP_ADMIN_PASSWORD')) {
            Remove-Item -LiteralPath ('Env:'+ $name) -ErrorAction SilentlyContinue
        }
    } else {
        if(-not $MySqlAccess -or -not $MySqlAccess.runtimeUser -or -not $MySqlAccess.runtimePassword -or
           -not $MySqlAccess.migrationUser -or -not $MySqlAccess.migrationPassword){throw 'Isolated MySQL access record is incomplete.'}
        $url='jdbc:mysql://127.0.0.1:23306/cmms_mysql_lab?useAffectedRows=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
        $user=$MySqlAccess.runtimeUser;$password=$MySqlAccess.runtimePassword
        $migrationUser=$MySqlAccess.migrationUser;$migrationPassword=$MySqlAccess.migrationPassword
        $driver='com.mysql.cj.jdbc.Driver';$dialect='com.grash.database.CmmsMySqlDialect';$changelog='classpath:/db/mysql/master.xml'
        $env:MYSQL_JDBC_URL=$url;$env:MYSQL_USER=$user;$env:MYSQL_PASSWORD=$password
    }
    $env:CMMS_DATABASE=$Database
    $env:SPRING_DATASOURCE_URL=$url;$env:SPRING_DATASOURCE_USERNAME=$user;$env:SPRING_DATASOURCE_PASSWORD=$password
    $env:SPRING_DATASOURCE_DRIVER_CLASS_NAME=$driver
    $env:SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=$dialect
    $env:SPRING_LIQUIBASE_URL=$url;$env:SPRING_LIQUIBASE_USER=$migrationUser;$env:SPRING_LIQUIBASE_PASSWORD=$migrationPassword
    $env:SPRING_LIQUIBASE_CHANGE_LOG=$changelog
}
