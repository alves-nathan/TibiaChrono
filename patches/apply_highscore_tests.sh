#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-$(pwd)}"
cd "$ROOT_DIR"

echo "[highscore-tests] Adding highscore storage and scheduler tests..."

mkdir -p "$(dirname 'src/test/java/com/nathan/tibiastats/config/HighscorePlanConfigurationTest.java')"
cat > 'src/test/java/com/nathan/tibiastats/config/HighscorePlanConfigurationTest.java' <<'JAVA_TEST_EOF'
package com.nathan.tibiastats.config;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class HighscorePlanConfigurationTest {
    @Test
    void applicationDevDefinesSafeDistributedHighscorePlans() {
        HighscoreScrapeProperties properties = bindApplicationDevHighscoreProperties();

        assertThat(properties.getPlans()).hasSize(18);

        HighscoreScrapeProperties.Plan dailyExp = properties.getPlans().get("daily-exp");
        assertThat(dailyExp).isNotNull();
        assertThat(dailyExp.isEnabled()).isTrue();
        assertThat(dailyExp.getCron()).isEqualTo("0 0 7 * * *");
        assertThat(dailyExp.getZone()).isEqualTo("America/Sao_Paulo");
        assertThat(dailyExp.categoryList()).containsExactly(StatCategory.EXPERIENCE);
        assertThat(dailyExp.vocationFilterIds()).containsExactly(0, 1, 2, 3, 4, 5, 6);
        assertThat(dailyExp.getPageWindowSize()).isEqualTo(1);
        assertThat(dailyExp.getRequestMaxAttempts()).isEqualTo(1);
        assertThat(dailyExp.isAbortRunOnForbidden()).isTrue();
        assertThat(dailyExp.isRunOnStartup()).isFalse();

        HighscoreScrapeProperties.Plan manualBackfill = properties.getPlans().get("manual-backfill-all-highscores");
        assertThat(manualBackfill).isNotNull();
        assertThat(manualBackfill.isEnabled()).isFalse();
        assertThat(manualBackfill.getParallelism()).isEqualTo(1);
        assertThat(manualBackfill.getRequestParallelism()).isEqualTo(1);
        assertThat(manualBackfill.getRequestMinIntervalMs()).isGreaterThanOrEqualTo(2_000);

        properties.getPlans().forEach((name, plan) -> {
            if (!plan.isEnabled()) {
                return;
            }
            assertThat(plan.getPageWindowSize()).as(name + " must not use page-level parallelism").isEqualTo(1);
            assertThat(plan.getRequestMaxAttempts()).as(name + " must not keep retrying 403/429 responses").isEqualTo(1);
            assertThat(plan.isAbortRunOnForbidden()).as(name + " must abort on 403/429").isTrue();
            assertThat(plan.isRunOnStartup()).as(name + " should not run automatically on every app boot").isFalse();
        });
    }

    private HighscoreScrapeProperties bindApplicationDevHighscoreProperties() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-dev.yml"));
        Properties yamlProperties = yaml.getObject();
        assertThat(yamlProperties).as("application-dev.yml must be loadable").isNotNull();

        Map<String, Object> source = new LinkedHashMap<>();
        yamlProperties.forEach((key, value) -> source.put(String.valueOf(key), value));

        return new Binder(new MapConfigurationPropertySource(source))
                .bind("tibiastats.scrape.highscores", HighscoreScrapeProperties.class)
                .orElseThrow(() -> new AssertionError("Could not bind tibiastats.scrape.highscores from application-dev.yml"));
    }
}
JAVA_TEST_EOF

mkdir -p "$(dirname 'src/test/java/com/nathan/tibiastats/db/FlywayMigrationVersionTest.java')"
cat > 'src/test/java/com/nathan/tibiastats/db/FlywayMigrationVersionTest.java' <<'JAVA_TEST_EOF'
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
JAVA_TEST_EOF

mkdir -p "$(dirname 'src/test/java/com/nathan/tibiastats/db/HighscoreFlywayMigrationIntegrationTest.java')"
cat > 'src/test/java/com/nathan/tibiastats/db/HighscoreFlywayMigrationIntegrationTest.java' <<'JAVA_TEST_EOF'
package com.nathan.tibiastats.db;

import com.nathan.tibiastats.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "tibiastats.scrape.highscores.enabled=false")
@ActiveProfiles("test")
class HighscoreFlywayMigrationIntegrationTest extends AbstractPostgresTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void compactHighscoreStorageTablesExistAfterFlywayMigrations() {
        assertTableExists("highscore_exp_daily");
        assertTableExists("highscore_exp_rank_daily");
        assertTableExists("highscore_current_records");
        assertTableExists("highscore_record_periods");
        assertTableExists("highscore_scrape_scopes");
    }

    private void assertTableExists(String tableName) {
        String qualifiedName = jdbc.queryForObject("select to_regclass(?)", String.class, "public." + tableName);
        assertThat(qualifiedName).as(tableName + " table must exist").isNotBlank();
    }
}
JAVA_TEST_EOF

mkdir -p "$(dirname 'src/test/java/com/nathan/tibiastats/highscore/HighscoreCompactStorageIntegrationTest.java')"
cat > 'src/test/java/com/nathan/tibiastats/highscore/HighscoreCompactStorageIntegrationTest.java' <<'JAVA_TEST_EOF'
package com.nathan.tibiastats.highscore;

import com.nathan.tibiastats.AbstractPostgresTest;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.infrastructure.persistence.HighscoreStatRecordWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "tibiastats.scrape.highscores.enabled=false")
@ActiveProfiles("test")
class HighscoreCompactStorageIntegrationTest extends AbstractPostgresTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired HighscoreStatRecordWriter writer;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("""
            truncate table
                highscore_exp_rank_daily,
                highscore_exp_daily,
                highscore_record_periods,
                highscore_current_records,
                highscore_scrape_scopes,
                character_statrecords,
                character_names,
                character_worlds,
                scrape_players,
                scrapes,
                guild_characters,
                guilds,
                characters,
                worlds
            restart identity cascade
            """);
    }

    @Test
    void experienceDailyIsDeduplicatedByCharacterDateAndWorldButRanksAreStoredPerFilter() {
        Integer worldId = insertWorld("Expia");
        Long characterId = insertCharacter();
        LocalDate date = LocalDate.of(2026, 5, 27);
        Instant scrapedAt = Instant.parse("2026-05-27T10:00:00Z");

        writer.upsertBatch(List.of(
                row(characterId, worldId, StatCategory.EXPERIENCE, 0, date, 1_000_000L, 500, scrapedAt),
                row(characterId, worldId, StatCategory.EXPERIENCE, 3, date, 1_000_000L, 20, scrapedAt.plusSeconds(1))
        ));

        assertThat(count("highscore_exp_daily")).isEqualTo(1);
        assertThat(count("highscore_exp_rank_daily")).isEqualTo(2);
        assertThat(count("character_statrecords")).as("legacy table must not be written by highscore scraper").isZero();

        assertThat(queryLong("select experience from highscore_exp_daily where character_id = ?", characterId))
                .isEqualTo(1_000_000L);
        assertThat(queryInteger("select first_seen_filter from highscore_exp_daily where character_id = ?", characterId))
                .isEqualTo(0);
        assertThat(queryInteger("select rank from highscore_exp_rank_daily where character_id = ? and vocation_filter_id = 0", characterId))
                .isEqualTo(500);
        assertThat(queryInteger("select rank from highscore_exp_rank_daily where character_id = ? and vocation_filter_id = 3", characterId))
                .isEqualTo(20);

        writer.upsertBatch(List.of(
                row(characterId, worldId, StatCategory.EXPERIENCE, 0, date, 1_000_000L, 500, scrapedAt.plusSeconds(10)),
                row(characterId, worldId, StatCategory.EXPERIENCE, 3, date, 1_000_000L, 20, scrapedAt.plusSeconds(11))
        ));

        assertThat(count("highscore_exp_daily")).as("same day rerun must not duplicate EXP daily snapshot").isEqualTo(1);
        assertThat(count("highscore_exp_rank_daily")).as("same day rerun must not duplicate rank records").isEqualTo(2);
    }

    @Test
    void nonExperienceRowsKeepOneOpenPeriodWhenRankAndValueDoNotChange() {
        Integer worldId = insertWorld("Magica");
        Long characterId = insertCharacter();
        LocalDate firstDate = LocalDate.of(2026, 5, 27);
        LocalDate secondDate = firstDate.plusDays(1);

        writer.upsertBatch(List.of(row(characterId, worldId, StatCategory.MAGIC_LEVEL, 0, firstDate, 95L, 10, Instant.parse("2026-05-27T10:00:00Z"))));
        writer.upsertBatch(List.of(row(characterId, worldId, StatCategory.MAGIC_LEVEL, 0, secondDate, 95L, 10, Instant.parse("2026-05-28T10:00:00Z"))));

        assertThat(count("highscore_current_records")).isEqualTo(1);
        assertThat(count("highscore_record_periods")).isEqualTo(1);
        assertThat(count("highscore_exp_daily")).isZero();
        assertThat(count("character_statrecords")).as("legacy table must not be written by highscore scraper").isZero();

        assertThat(queryDate("select first_seen_date from highscore_current_records where character_id = ?", characterId))
                .isEqualTo(firstDate);
        assertThat(queryDate("select last_seen_date from highscore_current_records where character_id = ?", characterId))
                .isEqualTo(secondDate);
        assertThat(queryDate("select last_changed_date from highscore_current_records where character_id = ?", characterId))
                .isEqualTo(firstDate);
        assertThat(queryDate("select valid_from from highscore_record_periods where character_id = ?", characterId))
                .isEqualTo(firstDate);
        assertThat(queryNullableDate("select valid_until from highscore_record_periods where character_id = ?", characterId))
                .isNull();
    }

    @Test
    void nonExperienceRowsCloseCurrentPeriodAndOpenANewOneWhenRankOrValueChanges() {
        Integer worldId = insertWorld("Skillia");
        Long characterId = insertCharacter();
        LocalDate firstDate = LocalDate.of(2026, 5, 27);
        LocalDate secondDate = firstDate.plusDays(1);

        writer.upsertBatch(List.of(row(characterId, worldId, StatCategory.AXE_FIGHTING, 0, firstDate, 120L, 30, Instant.parse("2026-05-27T10:00:00Z"))));
        writer.upsertBatch(List.of(row(characterId, worldId, StatCategory.AXE_FIGHTING, 0, secondDate, 121L, 25, Instant.parse("2026-05-28T10:00:00Z"))));

        assertThat(count("highscore_current_records")).isEqualTo(1);
        assertThat(count("highscore_record_periods")).isEqualTo(2);

        assertThat(queryInteger("select rank from highscore_current_records where character_id = ?", characterId)).isEqualTo(25);
        assertThat(queryLong("select value from highscore_current_records where character_id = ?", characterId)).isEqualTo(121L);
        assertThat(queryDate("select last_changed_date from highscore_current_records where character_id = ?", characterId)).isEqualTo(secondDate);

        assertThat(queryDate("""
                select valid_until from highscore_record_periods
                where character_id = ? and rank = 30 and value = 120
                """, characterId)).isEqualTo(secondDate);
        assertThat(queryNullableDate("""
                select valid_until from highscore_record_periods
                where character_id = ? and rank = 25 and value = 121
                """, characterId)).isNull();
    }

    @Test
    void sameDayCorrectionUpdatesOpenPeriodInsteadOfCreatingZeroLengthPeriod() {
        Integer worldId = insertWorld("Correctia");
        Long characterId = insertCharacter();
        LocalDate date = LocalDate.of(2026, 5, 27);

        writer.upsertBatch(List.of(row(characterId, worldId, StatCategory.SWORD_FIGHTING, 0, date, 118L, 40, Instant.parse("2026-05-27T10:00:00Z"))));
        writer.upsertBatch(List.of(row(characterId, worldId, StatCategory.SWORD_FIGHTING, 0, date, 119L, 35, Instant.parse("2026-05-27T11:00:00Z"))));

        assertThat(count("highscore_record_periods")).isEqualTo(1);
        assertThat(queryInteger("select rank from highscore_record_periods where character_id = ?", characterId)).isEqualTo(35);
        assertThat(queryLong("select value from highscore_record_periods where character_id = ?", characterId)).isEqualTo(119L);
        assertThat(queryNullableDate("select valid_until from highscore_record_periods where character_id = ?", characterId)).isNull();
    }

    private HighscoreStatRecordWriter.HighscoreStatRow row(
            Long characterId,
            Integer worldId,
            StatCategory category,
            int vocationFilterId,
            LocalDate date,
            long value,
            int rank,
            Instant scrapedAt
    ) {
        return new HighscoreStatRecordWriter.HighscoreStatRow(
                characterId,
                worldId,
                category,
                vocationFilterId,
                date,
                value,
                rank,
                scrapedAt
        );
    }

    private Integer insertWorld(String name) {
        return jdbc.queryForObject(
                "insert into worlds(name, pvp_type, location) values (?, 'Open PvP', 'Europe') returning id",
                Integer.class,
                name
        );
    }

    private Long insertCharacter() {
        return jdbc.queryForObject("insert into characters(level) values (100) returning id", Long.class);
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return value == null ? 0 : value;
    }

    private Integer queryInteger(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private Long queryLong(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private LocalDate queryDate(String sql, Object... args) {
        return jdbc.queryForObject(sql, (rs, rowNum) -> rs.getDate(1).toLocalDate(), args);
    }

    private LocalDate queryNullableDate(String sql, Object... args) {
        return jdbc.queryForObject(sql, (rs, rowNum) -> {
            Date date = rs.getDate(1);
            return date == null ? null : date.toLocalDate();
        }, args);
    }
}
JAVA_TEST_EOF

mkdir -p "$(dirname 'src/test/java/com/nathan/tibiastats/highscore/HighscoreRateLimitCircuitBreakerIntegrationTest.java')"
cat > 'src/test/java/com/nathan/tibiastats/highscore/HighscoreRateLimitCircuitBreakerIntegrationTest.java' <<'JAVA_TEST_EOF'
package com.nathan.tibiastats.highscore;

import com.nathan.tibiastats.AbstractPostgresTest;
import com.nathan.tibiastats.application.service.HighscoreService;
import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.port.HighscorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "tibiastats.scrape.highscores.enabled=true")
@ActiveProfiles("test")
class HighscoreRateLimitCircuitBreakerIntegrationTest extends AbstractPostgresTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired HighscoreService highscoreService;

    @MockBean HighscorePort highscorePort;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("""
            truncate table
                highscore_exp_rank_daily,
                highscore_exp_daily,
                highscore_record_periods,
                highscore_current_records,
                highscore_scrape_scopes,
                character_statrecords,
                character_names,
                character_worlds,
                scrape_players,
                scrapes,
                guild_characters,
                guilds,
                characters,
                worlds
            restart identity cascade
            """);
    }

    @Test
    void http403AbortsTheCurrentPlanInsteadOfWalkingAllScopes() {
        insertWorld("Breaker One");
        insertWorld("Breaker Two");

        when(highscorePort.fetchHighscores(anyString(), eq(StatCategory.EXPERIENCE), eq(0), anyInt()))
                .thenThrow(new RuntimeException("HTTP 403 from Tibia highscores"));

        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setEnabled(true);
        plan.setCategories("EXPERIENCE");
        plan.setVocations("0");
        plan.setMaxPages(1);
        plan.setWorldLimit(0);
        plan.setScopesPerRun(0);
        plan.setParallelism(1);
        plan.setPageWindowSize(1);
        plan.setRequestParallelism(1);
        plan.setRequestMinIntervalMs(0);
        plan.setRequestJitterMs(0);
        plan.setPageDelayMs(0);
        plan.setRequestMaxAttempts(1);
        plan.setForbiddenCooldownMs(1_000);
        plan.setAbortRunOnForbidden(true);
        plan.setProgressLogIntervalScopes(1);

        highscoreService.updateHighscores("test-rate-limit", plan);

        verify(highscorePort, times(1)).fetchHighscores(
                anyString(),
                eq(StatCategory.EXPERIENCE),
                eq(0),
                eq(1)
        );

        assertThat(countScopesWithStatus("RATE_LIMITED")).isEqualTo(1);
        assertThat(countScopesWithStatus("FAILED")).isZero();
        assertThat(countRows("highscore_exp_daily")).isZero();
        assertThat(countRows("character_statrecords")).isZero();
    }

    private void insertWorld(String name) {
        jdbc.update("insert into worlds(name, pvp_type, location) values (?, 'Open PvP', 'Europe')", name);
    }

    private long countScopesWithStatus(String status) {
        Long count = jdbc.queryForObject(
                "select count(*) from highscore_scrape_scopes where last_status = ?",
                Long.class,
                status
        );
        return count == null ? 0 : count;
    }

    private long countRows(String table) {
        Long count = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return count == null ? 0 : count;
    }
}
JAVA_TEST_EOF

echo "[highscore-tests] Done."
echo "Run: mvn test"
