package com.ds.goroute.config.database;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Generic TypeHandler for PostgreSQL ENUM types.
 * Converts between Java Enum and PostgreSQL VARCHAR/TEXT stored as enum name.
 * 
 * MyBatis automatically detects the enum type from the field/parameter.
 *
 * @param <E> the enum type
 */
public class GenericEnumTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E> {

    private Class<E> enumType;

    /**
     * Default constructor required by MyBatis.
     * The enum type will be set automatically by MyBatis.
     */
    public GenericEnumTypeHandler() {
        // MyBatis will call setType() after construction
    }

    /**
     * Constructor with enum type (for explicit usage).
     */
    public GenericEnumTypeHandler(Class<E> enumType) {
        if (enumType == null) {
            throw new IllegalArgumentException("Type argument cannot be null");
        }
        this.enumType = enumType;
    }

    /**
     * Called by MyBatis to set the enum type.
     */
    public void setType(Class<E> type) {
        this.enumType = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return toEnum(value);
    }

    @Override
    public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return toEnum(value);
    }

    @Override
    public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return toEnum(value);
    }

    private E toEnum(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        
        if (enumType == null) {
            throw new IllegalStateException("Enum type not set. This should not happen.");
        }
        
        try {
            // Try exact match first (uppercase)
            return Enum.valueOf(enumType, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try to match ignoring case
            for (E constant : enumType.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(value)) {
                    return constant;
                }
            }
            // Log warning but return null instead of throwing
            System.err.println("⚠️ WARNING: Unknown enum value '" + value + "' for type " + enumType.getSimpleName() + ". Returning null.");
            return null;
        }
    }
}
