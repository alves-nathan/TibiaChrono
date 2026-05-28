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
