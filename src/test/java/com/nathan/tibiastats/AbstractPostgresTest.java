package com.nathan.tibiastats;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests.
 *
 * The project previously used Testcontainers directly, but Docker Desktop/WSL can expose
 * a socket that works for the Docker CLI while still failing Docker-Java/Testcontainers
 * detection. These tests now use an explicit, isolated PostgreSQL test database,
 * normally started with docker-compose.test.yml on localhost:5433.
 */
@ActiveProfiles("test")
public abstract class AbstractPostgresTest {
    @Autowired
    protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> env("TEST_DB_URL", "jdbc:postgresql://localhost:5433/tibiastats_test"));
        registry.add("spring.datasource.username", () -> env("TEST_DB_USERNAME", "tibia"));
        registry.add("spring.datasource.password", () -> env("TEST_DB_PASSWORD", "secret"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.security.oauth2.resourceserver.jwt.secret-key", () -> "test-secret-key-which-is-very-long-and-random-1234567890");
    }

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("""
                truncate table
                    highscore_exp_rank_daily,
                    highscore_exp_daily,
                    highscore_record_periods,
                    highscore_current_records,
                    highscore_scrape_scopes,
                    character_statrecords,
                    character_deaths,
                    character_names,
                    character_worlds,
                    scrape_players,
                    scrapes,
                    guild_invites,
                    guild_membership_events,
                    guild_memberships,
                    guild_snapshots,
                    guild_characters,
                    guilds,
                    refresh_tokens,
                    token_blacklist,
                    users,
                    characters,
                    worlds
                restart identity cascade
                """);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
