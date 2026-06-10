package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.HighscoreStatRow;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HighscoreCompactStatRecordWriterRemainingCoverageTest {
    @Test
    void compactWriterFallsBackToPeriodInsertWhenCurrentRecordDisappearsAfterConflict() {
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate(0, 1);
        jdbc.queryResults.addLast(new EmptyResultDataAccessException(1));
        HighscoreCompactStatRecordWriter writer =
                new HighscoreCompactStatRecordWriter(jdbc, new HighscoreStatCategoryCodeMapper());

        assertThat(writer.upsert(List.of(row(LocalDate.parse("2026-06-05"), 10, 100L)))).isEqualTo(1);

        assertThat(jdbc.updateSqls).anySatisfy(sql -> assertThat(sql).contains("insert into highscore_record_periods"));
    }

    @Test
    void compactWriterUpdatesOpenPeriodForSameDayCorrection() throws Exception {
        LocalDate day = LocalDate.parse("2026-06-05");
        FakeJdbcTemplate jdbc = new FakeJdbcTemplate(0, 1, 1);
        jdbc.queryResults.addLast(currentRow(2, 200L));
        jdbc.queryResults.addLast(periodRow(day));
        HighscoreCompactStatRecordWriter writer =
                new HighscoreCompactStatRecordWriter(jdbc, new HighscoreStatCategoryCodeMapper());

        assertThat(writer.upsert(List.of(row(day, 1, 300L)))).isEqualTo(1);

        assertThat(jdbc.updateSqls).anySatisfy(sql -> assertThat(sql)
                .contains("update highscore_record_periods")
                .contains("set rank = ?"));
        assertThat(jdbc.updateSqls).noneSatisfy(sql -> assertThat(sql).contains("set valid_until = ?"));
    }

    private static HighscoreStatRow row(LocalDate day, int rank, long value) {
        return new HighscoreStatRow(
                10L,
                20,
                StatCategory.MAGIC_LEVEL,
                4,
                day,
                value,
                rank,
                Instant.parse("2026-06-05T12:00:00Z")
        );
    }

    private static ResultSet currentRow(int rank, long value) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("rank")).thenReturn(rank);
        when(rs.getLong("value")).thenReturn(value);
        return rs;
    }

    private static ResultSet periodRow(LocalDate validFrom) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getDate("valid_from")).thenReturn(Date.valueOf(validFrom));
        return rs;
    }

    private static final class FakeJdbcTemplate extends JdbcTemplate {
        private final ArrayDeque<Integer> updateResults = new ArrayDeque<>();
        private final ArrayDeque<Object> queryResults = new ArrayDeque<>();
        private final List<String> updateSqls = new ArrayList<>();

        private FakeJdbcTemplate(Integer... updateResults) {
            this.updateResults.addAll(List.of(updateResults));
        }

        @Override
        public int update(String sql, Object... args) {
            updateSqls.add(sql);
            return updateResults.isEmpty() ? 1 : updateResults.removeFirst();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            Object result = queryResults.removeFirst();
            if (result instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            try {
                return rowMapper.mapRow((ResultSet) result, 0);
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
