package com.nathan.tibiastats.application.query;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TimelineAndJdbcSupportBatch58CoverageTest {
    @Test
    void characterTimelineServiceBuildsHighscoreOnlineSessionEventsAndNormalizesLimits() {
        ApiQueryService queries = mock(ApiQueryService.class);
        CharacterHistoryReadModelService history = mock(CharacterHistoryReadModelService.class);
        CharacterTimelineCoreReadModelService core = mock(CharacterTimelineCoreReadModelService.class);
        CharacterTimelineHighscoreReadModelService highscores = mock(CharacterTimelineHighscoreReadModelService.class);
        CharacterTimelineService service = new CharacterTimelineService(queries, history, core, highscores);
        Instant from = Instant.parse("2026-06-05T10:00:00Z");
        Instant middle = Instant.parse("2026-06-05T11:00:00Z");
        Instant to = Instant.parse("2026-06-05T12:00:00Z");

        when(core.coreTimeline("Knight", from, to, 200)).thenReturn(List.of(
                new CharacterTimelineService.CharacterTimelineEvent(1L, "Knight", "CORE", middle, null, null, null, null, null, null, "core", java.util.Map.of()),
                new CharacterTimelineService.CharacterTimelineEvent(1L, "Knight", "NULL_EVENT", null, null, null, null, null, null, null, "ignored", java.util.Map.of())
        ));
        when(highscores.experienceTimeline("Knight", from, to, 200)).thenReturn(List.of(
                new CharacterTimelineService.CharacterTimelineEvent(1L, "Knight", "EXP", to, null, null, "EXPERIENCE", 1, 100L, null, "exp", java.util.Map.of())
        ));
        when(highscores.nonExperienceHighscoreTimeline("Knight", from, to, 200)).thenReturn(List.of(
                new CharacterTimelineService.CharacterTimelineEvent(1L, "Knight", "MAGIC", from.minusSeconds(1), null, null, "MAGIC_LEVEL", 2, 20L, null, "filtered", java.util.Map.of())
        ));
        when(queries.findCharacterOnlineSessions("Knight", null, from, to, 1440, 200)).thenReturn(List.of(
                new ApiQueryService.CharacterOnlineSessionView(1L, "Knight", "Antica", middle.plusSeconds(30), to, 30L, 3),
                new ApiQueryService.CharacterOnlineSessionView(1L, "Knight", "Antica", null, to, 30L, 3)
        ));

        List<CharacterTimelineService.CharacterTimelineEvent> events =
                service.timeline("Knight", from, to, -1, true, true, 9999);

        assertThat(events).extracting(CharacterTimelineService.CharacterTimelineEvent::eventType)
                .containsExactly("EXP", "ONLINE_SESSION", "CORE");
        assertThat(events.get(1).metadata())
                .containsEntry("endedAt", to)
                .containsEntry("observedMinutes", 30L)
                .containsEntry("samples", 3)
                .containsEntry("maxGapMinutes", 1440);

        when(history.deaths("Knight", from, to, 5)).thenReturn(List.of());
        when(history.worldHistory("Knight", from, to, 5)).thenReturn(List.of());
        when(history.guildHistory("Knight", from, to, 5)).thenReturn(List.of());
        assertThat(service.deaths("Knight", from, to, 5)).isEmpty();
        assertThat(service.worldHistory("Knight", from, to, 5)).isEmpty();
        assertThat(service.guildHistory("Knight", from, to, 5)).isEmpty();
    }

    @Test
    void timelineJdbcSupportCoversBaseParamsSafeLimitsAndNullableHelpers() throws Exception {
        TestTimelineSupport support = new TestTimelineSupport();
        Instant now = Instant.parse("2026-06-05T12:00:00Z");
        MapSqlParameterSource base = support.base(" Knight ", now.minusSeconds(60), now, 5000, 200);

        assertThat(base.getValue("characterName")).isEqualTo("Knight");
        assertThat(base.getValue("limit")).isEqualTo(1000);
        assertThat(base.getSqlType("fromInstant")).isEqualTo(Types.TIMESTAMP);
        assertThat(base.getValue("fromInstant")).isEqualTo(Timestamp.from(now.minusSeconds(60)));
        assertThat(support.safe(0, 200)).isEqualTo(200);
        assertThatThrownBy(() -> support.base(" ", null, null, null, 200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("characterName is required");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("rank")).thenReturn(0);
        when(rs.getLong("value")).thenReturn(0L);
        when(rs.getBoolean("active")).thenReturn(false);
        when(rs.wasNull()).thenReturn(true, false, false);

        assertThat(support.nullableInteger(rs, "rank")).isNull();
        assertThat(support.nullableLong(rs, "value")).isEqualTo(0L);
        assertThat(support.nullableBoolean(rs, "active")).isFalse();
        assertThat(support.instant(null)).isNull();
    }

    @Test
    void characterOnlineJdbcSupportCoversFilterAppendAndMappers() throws Exception {
        TestOnlineSupport support = new TestOnlineSupport();
        StringBuilder sql = new StringBuilder("where 1=1");
        MapSqlParameterSource params = support.baseOnline("Knight");
        Instant from = Instant.parse("2026-06-05T10:00:00Z");
        Instant to = Instant.parse("2026-06-05T12:00:00Z");

        support.append(sql, params, " Antica ", from, to);

        assertThat(sql).hasToString("where 1=1 and lower(w.name) = lower(:onlineWorld) and s.scrape_time >= :onlineFrom and s.scrape_time <= :onlineTo");
        assertThat(params.getValue("onlineWorld")).isEqualTo("Antica");

        ResultSet pointRs = mock(ResultSet.class);
        when(pointRs.getLong("character_id")).thenReturn(1L);
        when(pointRs.getString("character_name")).thenReturn("Knight");
        when(pointRs.getLong("scrape_id")).thenReturn(2L);
        when(pointRs.getString("world")).thenReturn("Antica");
        when(pointRs.getTimestamp("scrape_time")).thenReturn(Timestamp.from(from));
        when(pointRs.getInt("players_online")).thenReturn(300);
        when(pointRs.wasNull()).thenReturn(false);
        assertThat(support.mapPoint(pointRs).playersOnline()).isEqualTo(300);

        ResultSet sessionRs = mock(ResultSet.class);
        when(sessionRs.getLong("character_id")).thenReturn(1L);
        when(sessionRs.getString("character_name")).thenReturn("Knight");
        when(sessionRs.getString("world")).thenReturn("Antica");
        when(sessionRs.getTimestamp("started_at")).thenReturn(Timestamp.from(from));
        when(sessionRs.getTimestamp("ended_at")).thenReturn(Timestamp.from(to));
        when(sessionRs.getLong("observed_minutes")).thenReturn(120L);
        when(sessionRs.getInt("samples")).thenReturn(4);
        when(sessionRs.wasNull()).thenReturn(false);
        assertThat(support.mapSession(sessionRs).observedMinutes()).isEqualTo(120L);

        ResultSet summaryRs = mock(ResultSet.class);
        when(summaryRs.getLong("character_id")).thenReturn(1L);
        when(summaryRs.getString("character_name")).thenReturn("Knight");
        when(summaryRs.getString("world")).thenReturn("Antica");
        when(summaryRs.getInt("appearances")).thenReturn(5);
        when(summaryRs.getInt("sessions")).thenReturn(2);
        when(summaryRs.getLong("observed_minutes")).thenReturn(240L);
        when(summaryRs.getTimestamp("first_seen_at")).thenReturn(Timestamp.from(from));
        when(summaryRs.getTimestamp("last_seen_at")).thenReturn(Timestamp.from(to));
        when(summaryRs.wasNull()).thenReturn(false);
        assertThat(support.mapSummary(summaryRs).sessions()).isEqualTo(2);
    }

    private static final class TestTimelineSupport extends CharacterTimelineJdbcSupport {
        private TestTimelineSupport() {
            super(mock(JdbcTemplate.class));
        }

        private MapSqlParameterSource base(String characterName, Instant from, Instant to, Integer limit, int defaultLimit) {
            return baseParams(characterName, from, to, limit, defaultLimit);
        }

        private int safe(int requested, int defaultLimit) {
            return safeLimit(requested, defaultLimit);
        }

        private Integer nullableInteger(ResultSet rs, String column) throws Exception {
            return getNullableInteger(rs, column);
        }

        private Long nullableLong(ResultSet rs, String column) throws Exception {
            return getNullableLong(rs, column);
        }

        private Boolean nullableBoolean(ResultSet rs, String column) throws Exception {
            return getNullableBoolean(rs, column);
        }

        private Instant instant(Timestamp timestamp) {
            return toInstant(timestamp);
        }
    }

    private static final class TestOnlineSupport extends CharacterOnlineJdbcSupport {
        private TestOnlineSupport() {
            super(mock(JdbcTemplate.class));
        }

        private MapSqlParameterSource baseOnline(String characterName) {
            return baseCharacterOnlineParams(characterName);
        }

        private void append(StringBuilder sql, MapSqlParameterSource params, String world, Instant from, Instant to) {
            appendCharacterOnlineFilters(sql, params, world, from, to);
        }

        private ApiQueryService.CharacterOnlinePointView mapPoint(ResultSet rs) throws Exception {
            return mapCharacterOnlinePoint(rs, 0);
        }

        private ApiQueryService.CharacterOnlineSessionView mapSession(ResultSet rs) throws Exception {
            return mapCharacterOnlineSession(rs, 0);
        }

        private ApiQueryService.CharacterOnlineWorldSummaryView mapSummary(ResultSet rs) throws Exception {
            return mapCharacterOnlineWorldSummary(rs, 0);
        }
    }
}
