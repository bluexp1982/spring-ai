package com.example.springai.common.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class LongArrayTypeHandler extends BaseTypeHandler<List<Long>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Long> parameter, JdbcType jdbcType) throws SQLException {
        Connection conn = ps.getConnection();
        Long[] values = parameter.toArray(new Long[0]);
        Array array = conn.createArrayOf("bigint", values);
        ps.setArray(i, array);
    }

    @Override
    public List<Long> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toList(rs.getArray(columnName));
    }

    @Override
    public List<Long> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toList(rs.getArray(columnIndex));
    }

    @Override
    public List<Long> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toList(cs.getArray(columnIndex));
    }

    private List<Long> toList(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Long[] longs = (Long[]) array.getArray();
        List<Long> list = new ArrayList<>(longs.length);
        for (Long l : longs) {
            list.add(l);
        }
        return list;
    }
}