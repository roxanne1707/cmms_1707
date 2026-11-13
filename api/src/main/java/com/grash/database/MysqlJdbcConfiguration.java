package com.grash.database;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import javax.sql.DataSource;
import java.util.UUID;

/** Connector/J cannot bind java.util.UUID as PostgreSQL's native UUID type. SQL stays explicit. */
@Configuration
@ConditionalOnProperty(name="cmms.database",havingValue="mysql")
public class MysqlJdbcConfiguration {
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource source) {
        return new JdbcTemplate(source) {
            private Object[] bind(Object[] args) {
                if(args==null)return null;
                Object[] result=args.clone();
                for(int i=0;i<result.length;i++) if(result[i] instanceof UUID)result[i]=result[i].toString();
                return result;
            }
            @Override protected org.springframework.jdbc.core.PreparedStatementSetter newArgPreparedStatementSetter(Object[] args) { return super.newArgPreparedStatementSetter(bind(args)); }
            @Override public int update(String sql,Object... args) { return super.update(sql,bind(args)); }
            @Override public <T> java.util.List<T> query(String sql,RowMapper<T> mapper,Object... args) { return super.query(sql,mapper,bind(args)); }
            @Override public <T> T queryForObject(String sql,Class<T> type,Object... args) {
                if(type==UUID.class)return type.cast(UUID.fromString(super.queryForObject(sql,String.class,bind(args))));
                return super.queryForObject(sql,type,bind(args));
            }
            @Override public java.util.List<java.util.Map<String,Object>> queryForList(String sql,Object... args) { return super.queryForList(sql,bind(args)); }
            @Override public java.util.Map<String,Object> queryForMap(String sql,Object... args) { return super.queryForMap(sql,bind(args)); }
        };
    }
}
