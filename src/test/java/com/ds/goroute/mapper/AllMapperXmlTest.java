package com.ds.goroute.mapper;

import com.ds.goroute.config.database.UUIDTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Parses every mapper XML the application loads, the way MyBatis does at startup.
 *
 * <p>The per-area tests only cover their own files, so a malformed statement, a duplicate
 * statement id or an XML comment containing "--" in any other mapper was only discovered
 * when the application refused to start. This also catches the other half of the same
 * class of bug: a statement whose id has no method on the mapper interface, which fails
 * at the first call instead of at boot.
 */
class AllMapperXmlTest {

    private static final Path MAPPER_DIR = Path.of("src", "main", "resources", "mapper");

    @Test
    void everyMapperXmlIsAValidMyBatisMapping() throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);

        List<String> resources = mapperResources();
        assertTrue(resources.size() > 50, "expected the full mapper set, found " + resources.size());

        // Two passes: a mapper may <include> a fragment defined in another file, which is
        // only registered once that file has been parsed.
        List<String> pending = new ArrayList<>(resources);
        List<String> failures = new ArrayList<>();
        for (int pass = 0; pass < 2 && !pending.isEmpty(); pass++) {
            List<String> stillPending = new ArrayList<>();
            failures.clear();
            for (String resource : pending) {
                try (InputStream input = Files.newInputStream(MAPPER_DIR.resolve(fileName(resource)))) {
                    new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
                } catch (Exception ex) {
                    stillPending.add(resource);
                    failures.add(resource + " -> " + rootCause(ex));
                }
            }
            pending = stillPending;
        }

        if (!failures.isEmpty()) {
            fail("Mapper XML files MyBatis cannot load:\n  " + String.join("\n  ", failures));
        }
    }

    @Test
    void everyStatementIdHasAMethodOnItsMapperInterface() throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        for (String resource : mapperResources()) {
            try (InputStream input = Files.newInputStream(MAPPER_DIR.resolve(fileName(resource)))) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            } catch (Exception ignored) {
                // Covered by the test above; nothing to add here.
            }
        }

        List<String> orphans = new ArrayList<>();
        for (String id : new HashSet<>(statementIds(configuration))) {
            int split = id.lastIndexOf('.');
            if (split < 0) {
                continue;
            }
            String type = id.substring(0, split);
            String method = id.substring(split + 1);
            if (!type.startsWith("com.ds.goroute.")) {
                continue;
            }
            Class<?> mapper;
            try {
                mapper = Class.forName(type);
            } catch (ClassNotFoundException ex) {
                orphans.add(id + " (no such interface)");
                continue;
            }
            boolean found = Arrays.stream(mapper.getMethods()).map(Method::getName).anyMatch(method::equals);
            if (!found) {
                orphans.add(id);
            }
        }

        if (!orphans.isEmpty()) {
            fail("Mapper statements with no method on the interface:\n  " + String.join("\n  ", orphans));
        }
    }

    private static List<String> statementIds(Configuration configuration) {
        // getMappedStatements() also holds Ambiguity placeholders for short names that two
        // mappers share, and those cannot be cast to MappedStatement. The name view is safe.
        return new ArrayList<>(new HashSet<>(configuration.getMappedStatementNames()));
    }

    private static List<String> mapperResources() throws Exception {
        try (Stream<Path> files = Files.list(MAPPER_DIR)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".xml"))
                    .sorted()
                    .map(name -> "mapper/" + name)
                    .toList();
        }
    }

    private static String fileName(String resource) {
        return resource.substring(resource.lastIndexOf('/') + 1);
    }

    private static String rootCause(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
