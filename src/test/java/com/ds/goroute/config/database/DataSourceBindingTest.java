package com.ds.goroute.config.database;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the shape of the datasource block in application.yaml.
 *
 * <p>Until 2026-09-16 the pool settings sat in a nested {@code hikari:} block while the
 * bean bound the level above it, so every one of them was read by nobody:
 * {@code HikariDataSource} has no {@code hikari} property. Nothing failed, the pool just
 * ran on defaults. Since both spellings bind without error, only an assertion catches a
 * regression here — {@code TicketmasterApplicationTests} cannot, because it needs a live
 * database and does not run in this environment.
 */
class DataSourceBindingTest {

    @Test
    void masterPoolSettingsFromTheYamlReachTheHikariDataSource() throws Exception {
        HikariDataSource dataSource = bind("spring.datasource.master.hikari");

        assertNotNull(dataSource.getJdbcUrl(), "jdbc-url must bind");
        assertEquals("org.postgresql.Driver", dataSource.getDriverClassName());
        assertEquals(10, dataSource.getMaximumPoolSize());
        assertEquals(5, dataSource.getMinimumIdle(), "minimum-idle is the setting that silently did nothing");
        assertEquals(30000, dataSource.getConnectionTimeout());
        assertEquals(600000, dataSource.getIdleTimeout());
        assertEquals(1800000, dataSource.getMaxLifetime());
    }

    @Test
    void slavePoolSettingsFromTheYamlReachTheHikariDataSource() throws Exception {
        HikariDataSource dataSource = bind("spring.datasource.slave.hikari");

        assertNotNull(dataSource.getJdbcUrl(), "jdbc-url must bind");
        assertEquals(10, dataSource.getMaximumPoolSize());
        assertEquals(5, dataSource.getMinimumIdle());
    }

    private static HikariDataSource bind(String prefix) throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yaml", new ClassPathResource("application.yaml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);

        HikariDataSource dataSource = DataSourceBuilder.create().type(HikariDataSource.class).build();
        new Binder(ConfigurationPropertySources.from(propertySources))
                .bind(prefix, Bindable.ofInstance(dataSource));
        return dataSource;
    }
}
