package com.nathan.tibiastats.highscore;

import com.nathan.tibiastats.AbstractPostgresTest;
import com.nathan.tibiastats.domain.model.HighscoreStatRow;
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

    private HighscoreStatRow row(
            Long characterId,
            Integer worldId,
            StatCategory category,
            int vocationFilterId,
            LocalDate date,
            long value,
            int rank,
            Instant scrapedAt
    ) {
        return new HighscoreStatRow(
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
