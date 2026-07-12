package com.ds.goroute.config.database;

import com.ds.goroute.type.TranslationLocale;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TranslationLocaleTypeHandler extends BaseTypeHandler<TranslationLocale> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, TranslationLocale parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.code());
    }

    @Override
    public TranslationLocale getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public TranslationLocale getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public TranslationLocale getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private TranslationLocale parse(String value) {
        return TranslationLocale.fromCode(value).orElse(null);
    }
}
