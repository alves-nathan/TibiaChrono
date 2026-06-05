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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyHighscoreReadModelTailCoverageTest {
    private static final Instant NOW = Instant.parse("2026-06-05T12:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2026-06-05");

    @Test
    void findCharacterHighscoresMapsRowsWithOptionalFiltersAndNullableValue() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultSet rs = highscoreResultSet();
        stubSingleRowQuery(jdbcTemplate, rs);
        LegacyHighscoreReadModelService service = new LegacyHighscoreReadModelService(jdbcTemplate);

        List<ApiQueryService.HighscoreView> result = service.findCharacterHighscores(
                "Knight One",
                StatCategory.MAGIC_LEVEL,
                " Antica ",
                4,
                DAY.minusDays(1),
                DAY,
                -5
        );

        assertThat(result).hasSize(1);
        ApiQueryService.HighscoreView row = result.getFirst();
        assertThat(row.id()).isEqualTo(100L);
        assertThat(row.rank()).isEqualTo(1);
        assertThat(row.characterName()).isEqualTo("Knight One");
        assertThat(row.characterId()).isEqualTo(10L);
        assertThat(row.world()).isEqualTo("Antica");
        assertThat(row.category()).isEqualTo(StatCategory.MAGIC_LEVEL.name());
        assertThat(row.vocationFilterId()).isEqualTo(4);
        assertThat(row.date()).isEqualTo(DAY);
        assertThat(row.value()).isNull();
        assertThat(row.scrapedAt()).isEqualTo(NOW);
    }

    private static ResultSet highscoreResultSet() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(100L);
        when(rs.getInt("rank")).thenReturn(1);
        when(rs.getString("character_name")).thenReturn("Knight One");
        when(rs.getLong("character_id")).thenReturn(10L);
        when(rs.getString("world")).thenReturn("Antica");
        when(rs.getString("category")).thenReturn(StatCategory.MAGIC_LEVEL.name());
        when(rs.getInt("vocation_filter_id")).thenReturn(4);
        when(rs.getObject("date", LocalDate.class)).thenReturn(DAY);
        when(rs.getLong("value")).thenReturn(0L);
        when(rs.getTimestamp("scraped_at")).thenReturn(Timestamp.from(NOW));
        when(rs.wasNull()).thenReturn(true, true, false, false, false, false);
        return rs;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void stubSingleRowQuery(JdbcTemplate jdbcTemplate, ResultSet rs) {
        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });
    }
}
