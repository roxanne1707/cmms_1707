package com.grash.readmodel.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.sql.*;
import java.time.*;

/** Existing CMMS timestamps have no SQL time zone; the query contract interprets them as UTC. */
public class UtcInstantTypeHandler extends BaseTypeHandler<Instant> {
    private Instant convert(LocalDateTime value){return value==null?null:value.toInstant(ZoneOffset.UTC);}
    public void setNonNullParameter(PreparedStatement p,int i,Instant v,JdbcType type)throws SQLException{
        p.setObject(i,LocalDateTime.ofInstant(v,ZoneOffset.UTC));
    }
    public Instant getNullableResult(ResultSet r,String c)throws SQLException{return convert(r.getObject(c,LocalDateTime.class));}
    public Instant getNullableResult(ResultSet r,int c)throws SQLException{return convert(r.getObject(c,LocalDateTime.class));}
    public Instant getNullableResult(CallableStatement r,int c)throws SQLException{return convert(r.getObject(c,LocalDateTime.class));}
}
