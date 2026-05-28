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
                highscore_http_backoff_state,
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
