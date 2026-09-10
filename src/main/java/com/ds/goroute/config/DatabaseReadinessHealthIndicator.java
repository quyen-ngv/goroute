package com.ds.goroute.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Readiness is UP only after the primary database accepts an actual query. */
@Component("readinessDatabase")
public class DatabaseReadinessHealthIndicator implements HealthIndicator {
    private final DataSource dataSource;

    public DatabaseReadinessHealthIndicator(@Qualifier("masterDataSource") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT 1");
                 ResultSet result = statement.executeQuery()) {
                if (result.next() && result.getInt(1) == 1) {
                    return Health.up().withDetail("database", "reachable").build();
                }
            }
            return Health.down().withDetail("database", "unexpected response").build();
        } catch (Exception exception) {
            return Health.down().withDetail("database", "unreachable").build();
        }
    }
}
