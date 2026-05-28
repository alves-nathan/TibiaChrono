package com.nathan.tibiastats.db;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationVersionTest {
    private static final Pattern VERSION_PATTERN = Pattern.compile("^V([^_]+)__.*\\.sql$");

    @Test
    void flywayMigrationVersionsAreUniqueOnRuntimeClasspath() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*__*.sql");

        Map<String, String> seenByVersion = new LinkedHashMap<>();
        Map<String, String> duplicates = new LinkedHashMap<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) {
                continue;
            }
            Matcher matcher = VERSION_PATTERN.matcher(filename);
            if (!matcher.matches()) {
                continue;
            }

            String version = matcher.group(1);
            String previous = seenByVersion.putIfAbsent(version, filename);
            if (previous != null) {
                duplicates.put(version, previous + " and " + filename);
            }
        }

        assertThat(duplicates)
                .as("Flyway fails application startup when two migrations share the same version")
                .isEmpty();
    }
}
