package com.grash.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import java.sql.*;
import java.util.*;

/** Native CI/lab fallback, NEVER the deployed DB. Each application context owns a fresh schema.
 * Schemas are retained for failure diagnosis; no cleanup can touch public or user data. */
public final class IsolatedTestDatabase {
    private static final Map<DynamicPropertyRegistry,String> schemas = new WeakHashMap<>();
    private IsolatedTestDatabase() {}
    private static void configureMysql(DynamicPropertyRegistry registry,String url) {
        if (!url.equals("jdbc:mysql://127.0.0.1:23306/cmms_mysql_test")) throw new IllegalArgumentException("Only isolated MySQL :23306 is allowed");
        String user=System.getProperty("cmms.test.jdbc-user");
        String password=System.getProperty("cmms.test.jdbc-password");
        String schema=schemas.computeIfAbsent(registry,ignored -> {
            String name="cmms_mysql_reg_"+UUID.randomUUID().toString().replace("-","");
            try(Connection c=DriverManager.getConnection(url,user,password);Statement st=c.createStatement()) {
                st.execute("CREATE DATABASE "+name+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs");
            } catch(SQLException ex) { throw new IllegalStateException("Cannot create isolated MySQL test database",ex); }
            return name;
        });
        registry.add("enhancement.query.cache.namespace",()->schema);
        registry.add("cmms.database",()->"mysql");
        registry.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:23306/"+schema+"?useAffectedRows=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        registry.add("spring.datasource.username",()->user);registry.add("spring.datasource.password",()->password);
        registry.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.hikari.transaction-isolation",()->"TRANSACTION_READ_COMMITTED");
        registry.add("spring.liquibase.change-log",()->"classpath:/db/mysql/master.xml");
        registry.add("spring.jpa.properties.hibernate.dialect",()->"com.grash.database.CmmsMySqlDialect");
        registry.add("spring.jpa.properties.hibernate.type.preferred_uuid_jdbc_type",()->"CHAR");
        registry.add("spring.jpa.properties.hibernate.jdbc.time_zone",()->"UTC");
        registry.add("spring.datasource.hikari.maximum-pool-size",()->"5");
        registry.add("spring.datasource.hikari.minimum-idle",()->"0");
        registry.add("spring.quartz.properties.org.quartz.jobStore.driverDelegateClass",()->"org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
        registry.add("spring.quartz.auto-startup",()->"false");
        registry.add("cmms.events.workers-enabled",()->"false");
        registry.add("cmms.events.listener-auto-startup",()->"false");
        registry.add("cmms.events.external-delivery-enabled",()->"false");
        registry.add("mail.enable",()->"false");
        registry.add("license-key",()->"");registry.add("license-file-path",()->"");
        registry.add("spring.data.redis.database",()->"2");
    }
    public static boolean configured() { return System.getProperty("cmms.test.jdbc-url") != null; }
    public static synchronized void configure(DynamicPropertyRegistry registry, String group) {
        String url=System.getProperty("cmms.test.jdbc-url");
        if (url!=null && url.startsWith("jdbc:mysql:")) { configureMysql(registry,url); return; }
        if (!"jdbc:postgresql://127.0.0.1:25432/cmms_enhancement_test".equals(url))
            throw new IllegalArgumentException("Only the dedicated loopback enhancement database is allowed");
        String username=System.getProperty("cmms.test.jdbc-user","cmms_test");
        String password=System.getProperty("cmms.test.jdbc-password","cmms_test_local");
        String schema=schemas.computeIfAbsent(registry, ignored -> {
            String name="cmms_reg_"+UUID.randomUUID().toString().replace("-", "");
            try(Connection connection=DriverManager.getConnection(url,username,password);
                Statement statement=connection.createStatement()) {
                statement.execute("CREATE SCHEMA "+name);
                // PostgreSQL extensions are database-wide. Keep table search_path isolated while
                // exposing only the shared extension function through an explicit local wrapper.
                try(ResultSet extension=statement.executeQuery("SELECT to_regprocedure('public.unaccent(text)') IS NOT NULL")) {
                    if(extension.next() && extension.getBoolean(1)) {
                        try(Statement ddl=connection.createStatement()) {
                            ddl.execute("CREATE FUNCTION "+name+".unaccent(text) RETURNS text LANGUAGE sql STABLE AS 'SELECT public.unaccent($1)'");
                        }
                    }
                }
            } catch(SQLException e) { throw new IllegalStateException("Cannot create isolated regression schema",e); }
            return name;
        });
        registry.add("enhancement.query.cache.namespace",()->schema);
        registry.add("spring.datasource.url",()->url+"?currentSchema="+schema);
        registry.add("spring.datasource.username",()->username);
        registry.add("spring.datasource.password",()->password);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.liquibase.default-schema",()->schema);
        registry.add("spring.jpa.properties.hibernate.default_schema",()->schema);
        registry.add("spring.datasource.hikari.maximum-pool-size",()->"5");
        registry.add("spring.datasource.hikari.minimum-idle",()->"0");
        registry.add("spring.quartz.auto-startup",()->"false");
        registry.add("cmms.events.workers-enabled",()->"false");
        registry.add("cmms.events.listener-auto-startup",()->"false");
        registry.add("cmms.events.external-delivery-enabled",()->"false");
        registry.add("mail.enable",()->"false");
        registry.add("license-key",()->"");
        registry.add("license-file-path",()->"");
    }
}
