package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadModelJdbcCoverageTest {
    private static final Instant NOW = Instant.parse("2026-06-02T12:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2026-06-02");

    @Test
    void legacyHighscoreReadModelMapsNullableHighscoreRows() throws Exception {
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(rs.getLong("nullable_value")).thenReturn(0L);
        org.mockito.Mockito.when(rs.wasNull()).thenReturn(true);

        java.lang.reflect.Method nullableLong = LegacyHighscoreReadModelService.class
                .getDeclaredMethod("nullableLong", java.sql.ResultSet.class, String.class);
        nullableLong.setAccessible(true);

        Object value = nullableLong.invoke(null, rs, "nullable_value");

        assertThat(value).isNull();
        org.mockito.Mockito.verify(rs).getLong("nullable_value");
        org.mockito.Mockito.verify(rs).wasNull();
    }

    @Test
    void legacyHighscoreReadModelMapsExactDateRowsWithNonNullValue() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultSet rs = highscoreResultSet(false);
        stubSingleRowQuery(jdbcTemplate, rs);
        LegacyHighscoreReadModelService service = new LegacyHighscoreReadModelService(jdbcTemplate);

        List<ApiQueryService.HighscoreView> result = service.findHighscores("Antica", StatCategory.MAGIC_LEVEL, 4, DAY, 5);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().category()).isEqualTo(StatCategory.EXPERIENCE.name());
        assertThat(result.getFirst().value()).isEqualTo(123456L);
    }

    @Test
    void scrapeJobReadModelMapsNullableJobCountersAndNormalizesFilters() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(10L);
        when(rs.getString("job_name")).thenReturn("WORLD_SCRAPER");
        when(rs.getString("status")).thenReturn("RUNNING");
        when(rs.getTimestamp("started_at")).thenReturn(Timestamp.from(NOW));
        when(rs.getTimestamp("finished_at")).thenReturn(null);
        when(rs.getLong("duration_ms")).thenReturn(0L);
        when(rs.getInt("items_processed")).thenReturn(0);
        when(rs.getInt("items_created")).thenReturn(0);
        when(rs.getInt("items_updated")).thenReturn(0);
        when(rs.getInt("items_failed")).thenReturn(0);
        when(rs.wasNull()).thenReturn(true, true, true, true, true);
        when(rs.getString("error_message")).thenReturn(null);
        stubSingleRowQuery(jdbcTemplate, rs);
        ScrapeJobReadModelService service = new ScrapeJobReadModelService(jdbcTemplate);

        List<ApiQueryService.ScrapeJobView> result = service.findScrapeJobs(" WORLD_SCRAPER ", " running ", -1);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().durationMs()).isNull();
        assertThat(result.getFirst().itemsProcessed()).isNull();
        assertThat(result.getFirst().startedAt()).isEqualTo(NOW);
    }

    @Test
    void worldReadModelMapsWorldRowsAndOptionalLookup() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("id")).thenReturn(1);
        when(rs.getString("name")).thenReturn("Antica");
        when(rs.getString("pvp_type")).thenReturn("Open PvP");
        when(rs.getString("location")).thenReturn("EU");
        when(rs.getString("online_record")).thenReturn("1000 players");
        when(rs.getObject("creation_date", LocalDate.class)).thenReturn(DAY);
        when(rs.getString("transfer_type")).thenReturn("blocked");
        when(rs.getString("game_world_type")).thenReturn("regular");
        when(rs.getInt("players_online")).thenReturn(0);
        when(rs.wasNull()).thenReturn(true);
        when(rs.getTimestamp("last_scraped_at")).thenReturn(Timestamp.from(NOW));
        stubSingleRowQuery(jdbcTemplate, rs);
        WorldReadModelService service = new WorldReadModelService(jdbcTemplate);

        List<ApiQueryService.WorldView> worlds = service.findWorlds();
        var world = service.findWorld("Antica");

        assertThat(worlds).hasSize(1);
        assertThat(worlds.getFirst().playersOnline()).isNull();
        assertThat(world).isPresent();
        assertThat(world.get().lastScrapedAt()).isEqualTo(NOW);
    }

    @Test
    void characterTimelineHighscoreReadModelMapsExperienceAndNonExperienceRows() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultSet experience = timelineResultSet("EXPERIENCE");
        ResultSet magic = timelineResultSet("11");
        stubSequentialSingleRowQueries(jdbcTemplate, experience, magic);
        CharacterTimelineHighscoreReadModelService service = new CharacterTimelineHighscoreReadModelService(jdbcTemplate);

        List<CharacterTimelineService.CharacterTimelineEvent> exp = service.experienceTimeline(" Knight ", NOW.minusSeconds(60), NOW, 0);
        List<CharacterTimelineService.CharacterTimelineEvent> nonExp = service.nonExperienceHighscoreTimeline("Knight", null, null, 10);

        assertThat(exp.getFirst().category()).isEqualTo("EXPERIENCE");
        assertThat(nonExp.getFirst().category()).isEqualTo(StatCategory.MAGIC_LEVEL.name());
        assertThat(nonExp.getFirst().rank()).isEqualTo(1);
    }

    @Test
    void characterTimelineHighscoreReadModelRejectsBlankCharacterNameAndMapsUnknownCategoryCode() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultSet unknown = timelineResultSet("99");
        stubSingleRowQuery(jdbcTemplate, unknown);
        CharacterTimelineHighscoreReadModelService service = new CharacterTimelineHighscoreReadModelService(jdbcTemplate);

        assertThatThrownBy(() -> service.experienceTimeline(" ", null, null, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("characterName is required");
        assertThat(service.nonExperienceHighscoreTimeline("Knight", null, null, 10).getFirst().category()).isEqualTo("UNKNOWN");
    }

    private static ResultSet highscoreResultSet(boolean valueIsNull) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(100L);
        when(rs.getInt("rank")).thenReturn(1);
        when(rs.getString("character_name")).thenReturn("Knight One");
        when(rs.getLong("character_id")).thenReturn(10L);
        when(rs.getString("world")).thenReturn("Antica");
        when(rs.getString("category")).thenReturn(StatCategory.EXPERIENCE.name());
        when(rs.getInt("vocation_filter_id")).thenReturn(0);
        when(rs.getObject("date", LocalDate.class)).thenReturn(DAY);
        when(rs.getLong("value")).thenReturn(valueIsNull ? 0L : 123456L);
        when(rs.wasNull()).thenReturn(false, false, valueIsNull);
        when(rs.getTimestamp("scraped_at")).thenReturn(Timestamp.from(NOW));
        return rs;
    }

    private static ResultSet timelineResultSet(String category) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("character_id")).thenReturn(10L);
        when(rs.getString("character_name")).thenReturn("Knight One");
        when(rs.getString("event_type")).thenReturn("HIGHSCORE");
        when(rs.getTimestamp("occurred_at")).thenReturn(Timestamp.from(NOW));
        when(rs.getString("world")).thenReturn("Antica");
        when(rs.getString("guild_name")).thenReturn(null);
        when(rs.getString("category")).thenReturn(category);
        when(rs.getInt("rank")).thenReturn(1);
        when(rs.getLong("value")).thenReturn(123456L);
        when(rs.wasNull()).thenReturn(false, false);
        when(rs.getString("title")).thenReturn("300");
        when(rs.getString("description")).thenReturn("snapshot");
        return rs;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void stubSingleRowQuery(JdbcTemplate jdbcTemplate, ResultSet rs) {
        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void stubSequentialSingleRowQueries(JdbcTemplate jdbcTemplate, ResultSet first, ResultSet second) {
        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(first, 0));
                })
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(second, 0));
                });
    }
}
