package com.ds.goroute.config.database;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two branches that each pick the next free migration number both merge cleanly, and Flyway then
 * refuses to start the application. That took production down on 2026-09-27 (two V147 files).
 * Flyway only notices at startup against a live database; this notices at build time.
 */
class MigrationVersionsTest {

    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+(?:[._]\\d+)*)__.+\\.sql$");

    @Test
    void everyMigrationVersionIsUnique() throws Exception {
        Resource[] scripts = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/*.sql");
        assertThat(scripts).isNotEmpty();

        Map<String, List<String>> byVersion = new TreeMap<>();
        for (Resource script : scripts) {
            Matcher matcher = VERSIONED.matcher(script.getFilename());
            if (matcher.matches()) {
                String version = matcher.group(1).replace('_', '.').replaceFirst("^0+(?=\\d)", "");
                byVersion.computeIfAbsent(version, v -> new ArrayList<>()).add(script.getFilename());
            }
        }

        Map<String, List<String>> duplicates = new TreeMap<>();
        byVersion.forEach((version, files) -> {
            if (files.size() > 1) duplicates.put(version, files);
        });
        assertThat(duplicates).as("migrations sharing a version").isEmpty();
    }
}
