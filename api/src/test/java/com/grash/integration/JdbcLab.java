package com.grash.integration;

import com.grash.database.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Pattern;

/** Narrow service fixtures, separate from full Hibernate/master-migration tests. Owns only a fresh random schema. */
public final class JdbcLab implements AutoCloseable {
    public final boolean mysql;
    public final String schema;
    public final DriverManagerDataSource source;
    public final JdbcTemplate jdbc;
    public final DatabasePlatform database;
    private final JdbcTemplate admin;
    public JdbcLab() {
        String url=System.getProperty("cmms.test.jdbc-url");
        mysql="jdbc:mysql://127.0.0.1:23306/cmms_mysql_test".equals(url);
        if (!mysql && !"jdbc:postgresql://127.0.0.1:25432/cmms_enhancement_test".equals(url))
            throw new IllegalArgumentException("Only exact isolated loopback test databases are accepted");
        String user=System.getProperty("cmms.test.jdbc-user","cmms_test"),password=System.getProperty("cmms.test.jdbc-password","cmms_test_local");
        schema=(mysql?"cmms_mysql_reg_":"cmms_jdbc_")+UUID.randomUUID().toString().replace("-","");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        admin.execute(mysql?"CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs":"CREATE SCHEMA "+schema);
        source=new DriverManagerDataSource(mysql?"jdbc:mysql://127.0.0.1:23306/"+schema+"?useAffectedRows=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true":url+"?currentSchema="+schema,user,password);
        if(mysql) {var properties=new Properties();properties.setProperty("sessionVariables","transaction_isolation='READ-COMMITTED'");source.setConnectionProperties(properties);}
        jdbc=mysql?new MysqlJdbcConfiguration().jdbcTemplate(source):new JdbcTemplate(source);
        database=new DatabasePlatform(mysql?"mysql":"postgresql",jdbc);
    }
    public String sql(String pg,String my) {return database.sql(pg,my);}
    public static String resource(String path) {
        try {return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    /** Use the shipped MySQL table definitions and constraints, not hand-translated substitutes. */
    public void mysqlTables(String... names) {
        String ddl=resource("db/mysql/schema.sql");
        for(String name:names) {
            var m=Pattern.compile("(?ms)^CREATE TABLE `"+Pattern.quote(name)+"` \\(.*?;").matcher(ddl);
            if(!m.find()) throw new IllegalArgumentException("Missing table "+name);
            jdbc.execute(m.group());
        }
        for(String name:names) {
            var m=Pattern.compile("(?m)^ALTER TABLE `"+Pattern.quote(name)+"` .*?;").matcher(ddl);
            while(m.find()) jdbc.execute(m.group());
        }
    }
    public long insert(String sql,Object... args) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(c->{var p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},keys);
        return ((Number)Objects.requireNonNull(keys.getKeys()).values().iterator().next()).longValue();
    }
    @Override public void close() {
        if(!schema.matches(mysql?"cmms_mysql_reg_[a-f0-9]{32}":"cmms_jdbc_[a-f0-9]{32}"))throw new IllegalStateException("Not an owned schema");
        admin.execute(mysql?"DROP DATABASE "+schema:"DROP SCHEMA "+schema+" CASCADE");
    }
}
