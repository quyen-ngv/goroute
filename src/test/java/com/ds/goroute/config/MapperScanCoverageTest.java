package com.ds.goroute.config;

import com.ds.goroute.config.database.DataSourceConfig;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every {@code @Mapper} interface must live under a package {@code @MapperScan} covers, or MyBatis
 * never registers it and the application dies at boot with "No qualifying bean of type ...Mapper".
 *
 * <p>This guard exists because {@code contextLoads} cannot run here (no database), so a mapper in a
 * new feature package that the scan list forgot is invisible to the whole suite until deploy. This
 * catches exactly that, without a database.
 */
@DisplayName("Every @Mapper is covered by @MapperScan")
class MapperScanCoverageTest {

    @Test
    void everyMapperIsWithinAScannedPackage() {
        String[] scanned = DataSourceConfig.class.getAnnotation(MapperScan.class).basePackages();
        assertThat(scanned).as("@MapperScan basePackages").isNotEmpty();

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Mapper.class));

        List<String> uncovered = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.ds.goroute")) {
            String pkg = packageOf(candidate.getBeanClassName());
            boolean covered = false;
            for (String base : scanned) {
                if (pkg.equals(base) || pkg.startsWith(base + ".")) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                uncovered.add(candidate.getBeanClassName());
            }
        }

        assertThat(uncovered)
                .as("@Mapper interfaces outside every @MapperScan basePackage (they would not be "
                        + "registered as beans and the app would fail to start)")
                .isEmpty();
    }

    private static String packageOf(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? "" : className.substring(0, dot);
    }
}
