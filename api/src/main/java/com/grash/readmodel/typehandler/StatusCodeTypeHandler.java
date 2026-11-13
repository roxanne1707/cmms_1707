package com.grash.readmodel.typehandler;

import com.grash.model.enums.Status;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.sql.*;

public class StatusCodeTypeHandler extends BaseTypeHandler<Status> {
    public static Status decode(int code) throws SQLException {
        return switch(code) {
            case 0 -> Status.OPEN; case 1 -> Status.IN_PROGRESS; case 2 -> Status.ON_HOLD;
            case 3 -> Status.COMPLETE; default -> throw new SQLException("Unknown persisted work order status: "+code);
        };
    }
    public void setNonNullParameter(PreparedStatement p,int i,Status value,JdbcType type) throws SQLException {
        p.setInt(i,switch(value) {case OPEN->0;case IN_PROGRESS->1;case ON_HOLD->2;case COMPLETE->3;});
    }
    public Status getNullableResult(ResultSet r,String c) throws SQLException {int v=r.getInt(c);return r.wasNull()?null:decode(v);}
    public Status getNullableResult(ResultSet r,int c) throws SQLException {int v=r.getInt(c);return r.wasNull()?null:decode(v);}
    public Status getNullableResult(CallableStatement r,int c) throws SQLException {int v=r.getInt(c);return r.wasNull()?null:decode(v);}
}
