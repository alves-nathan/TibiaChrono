package com.nathan.tibiastats.application.query;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryBatch59RemainingCoverageTest {
    @Test
    void timelineCanExcludeOptionalSourcesApplyDefaultLimitAndFilterByDateRange() {
        ApiQueryService queries = mock(ApiQueryService.class);
        CharacterHistoryReadModelService history = mock(CharacterHistoryReadModelService.class);
        CharacterTimelineCoreReadModelService core = mock(CharacterTimelineCoreReadModelService.class);
        CharacterTimelineHighscoreReadModelService highscores = mock(CharacterTimelineHighscoreReadModelService.class);
        CharacterTimelineService service = new CharacterTimelineService(queries, history, core, highscores);
        Instant from = Instant.parse("2026-06-05T10:00:00Z");
        Instant inside = Instant.parse("2026-06-05T11:00:00Z");
        Instant after = Instant.parse("2026-06-05T13:00:00Z");
        when(core.coreTimeline("Knight", from, after, 200)).thenReturn(List.of(
                event("DEATH", inside),
                event("TOO_LATE", after.plusSeconds(1)),
                event("NULL", null)
        ));

        var events = service.timeline("Knight", from, after, 0, false, false, null);

        assertThat(events).extracting(CharacterTimelineService.CharacterTimelineEvent::eventType)
                .containsExactly("DEATH");
    }

    @Test
    void timelineJdbcSupportPrepareParamsHandlesNullAndInstantValues() {
        TestTimelineSupport support = new TestTimelineSupport();
        Instant now = Instant.parse("2026-06-05T12:00:00Z");

        assertThat(support.prepare(null).getValues()).isEmpty();

        MapSqlParameterSource params = new MapSqlParameterSource("occurredAt", now);
        support.prepare(params);

        assertThat(params.getValue("occurredAt")).isEqualTo(Timestamp.from(now));
    }

    private static CharacterTimelineService.CharacterTimelineEvent event(String type, Instant occurredAt) {
        return new CharacterTimelineService.CharacterTimelineEvent(
                1L,
                "Knight",
                type,
                occurredAt,
                null,
                null,
                null,
                null,
                null,
                null,
                type,
                java.util.Map.of()
        );
    }

    private static final class TestTimelineSupport extends CharacterTimelineJdbcSupport {
        private TestTimelineSupport() {
            super(mock(JdbcTemplate.class));
        }

        private MapSqlParameterSource prepare(MapSqlParameterSource params) {
            return prepareParams(params);
        }
    }
}
