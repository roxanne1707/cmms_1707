package com.grash.readmodel.typehandler;

import com.grash.model.enums.Priority;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.sql.*;

public class PriorityCodeTypeHandler extends BaseTypeHandler<Priority> {
    public static Priority decode(int code) throws SQLException {
        return switch(code) {
            case 0 -> Priority.NONE; case 1 -> Priority.LOW; case 2 -> Priority.MEDIUM;
            case 3 -> Priority.HIGH; default -> throw new SQLException("Unknown persisted priority: "+code);
        };
    }
    public void setNonNullParameter(PreparedStatement p,int i,Priority value,JdbcType type) throws SQLException {
        p.setInt(i,switch(value) {case NONE->0;case LOW->1;case MEDIUM->2;case HIGH->3;});
    }
    public Priority getNullableResult(ResultSet r,String c) throws SQLException {int v=r.getInt(c);return r.wasNull()?null:decode(v);}
    public Priority getNullableResult(ResultSet r,int c) throws SQLException {int v=r.getInt(c);return r.wasNull()?null:decode(v);}
    public Priority getNullableResult(CallableStatement r,int c) throws SQLException {int v=r.getInt(c);return r.wasNull()?null:decode(v);}
}
