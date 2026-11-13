package com.grash.readmodel.typehandler;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import java.sql.*;
import java.util.UUID;
@MappedTypes(UUID.class)
public class PortableUuidTypeHandler extends BaseTypeHandler<UUID> {
    @Override public void setNonNullParameter(PreparedStatement statement,int index,UUID value,JdbcType type) throws SQLException {
        if(statement.getConnection().getMetaData().getDatabaseProductName().equals("MySQL"))statement.setString(index,value.toString());
        else statement.setObject(index,value);
    }
    private UUID parse(String value){return value==null?null:UUID.fromString(value);}
    @Override public UUID getNullableResult(ResultSet rs,String column)throws SQLException{return parse(rs.getString(column));}
    @Override public UUID getNullableResult(ResultSet rs,int column)throws SQLException{return parse(rs.getString(column));}
    @Override public UUID getNullableResult(CallableStatement s,int column)throws SQLException{return parse(s.getString(column));}
}
