package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HighscoreApiReadModelValidationCoverageTest {
    private static final LocalDate DAY = LocalDate.parse("2026-06-05");

    @Test
    void experienceReadModelValidatesRequiredDatesAndWorldButAllowsOptionalCharacterFilters() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        stubEmptyQuery(jdbcTemplate);
        HighscoreExperienceReadModelService service = new HighscoreExperienceReadModelService(jdbcTemplate);

        assertThatThrownBy(() -> service.findExperienceGains("Antica", null, DAY, 0, 25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startDate and endDate are required");
        assertThatThrownBy(() -> service.findExperienceGains("Antica", DAY, DAY.minusDays(1), 0, 25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("endDate must be greater than or equal to startDate");
        assertThatThrownBy(() -> service.findExperienceDaily(" ", DAY, 0, 25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("world is required");

        assertThat(service.findCharacterExperienceDaily(" Knight One ", " Antica ", null, DAY.minusDays(2), DAY, 0))
                .isEmpty();
        assertThat(service.findCharacterExperienceDaily("Knight One", " ", 0, null, null, 10))
                .isEmpty();
    }

    @Test
    void recordReadModelRejectsMissingOrExperienceCategoriesAndAllowsOptionalHistoryFilters() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        stubEmptyQuery(jdbcTemplate);
        HighscoreRecordReadModelService service = new HighscoreRecordReadModelService(jdbcTemplate);

        assertThatThrownBy(() -> service.findCurrent("Antica", null, 0, 25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("category is required");
        assertThatThrownBy(() -> service.findCurrent("Antica", StatCategory.EXPERIENCE, 0, 25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Use /api/highscores/exp/* endpoints for EXPERIENCE");
        assertThatThrownBy(() -> service.findHistory("Antica", StatCategory.EXPERIENCE, null, 0, null, null, 25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Use /api/highscores/exp/daily for EXPERIENCE history");

        assertThat(service.findCurrent(" Antica ", StatCategory.MAGIC_LEVEL, null, 0)).isEmpty();
        assertThat(service.findHistory("Antica", StatCategory.MAGIC_LEVEL, " Knight One ", null, DAY.minusDays(10), DAY, 0))
                .isEmpty();
        assertThat(service.findHistory("Antica", StatCategory.MAGIC_LEVEL, " ", 0, null, null, 10))
                .isEmpty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void stubEmptyQuery(JdbcTemplate jdbcTemplate) {
        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(RowMapper.class))).thenReturn(List.of());
    }
}
