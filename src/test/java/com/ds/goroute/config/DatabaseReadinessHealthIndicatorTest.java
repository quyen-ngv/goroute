package com.ds.goroute.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseReadinessHealthIndicatorTest {
    private final DataSource dataSource = mock(DataSource.class);
    private final DatabaseReadinessHealthIndicator indicator =
            new DatabaseReadinessHealthIndicator(dataSource);

    @Test
    void reportsUpOnlyAfterSelectOneSucceeds() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT 1")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(1);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWithoutLeakingDatabaseException() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection details"));
        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("database", "unreachable");
        assertThat(health.getDetails().toString()).doesNotContain("connection details");
    }
}
