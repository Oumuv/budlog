package dev.oumuv.budlog.common;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Preserve the previous Hibernate/JVM-zone response representation without changing the instant. */
@MappedTypes(OffsetDateTime.class)
@MappedJdbcTypes(value = {JdbcType.TIMESTAMP, JdbcType.TIMESTAMP_WITH_TIMEZONE}, includeNullJdbcType = true)
public class OffsetDateTimeTypeHandler extends BaseTypeHandler<OffsetDateTime> {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, OffsetDateTime value, JdbcType type)
            throws SQLException {
        statement.setObject(index, value);
    }

    private OffsetDateTime inApplicationZone(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime();
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet result, String column) throws SQLException {
        return inApplicationZone(result.getObject(column, OffsetDateTime.class));
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet result, int column) throws SQLException {
        return inApplicationZone(result.getObject(column, OffsetDateTime.class));
    }

    @Override
    public OffsetDateTime getNullableResult(CallableStatement statement, int column) throws SQLException {
        return inApplicationZone(statement.getObject(column, OffsetDateTime.class));
    }
}
