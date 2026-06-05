package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HighscoreApiJdbcSupportCoverageTest {
    private static final Instant NOW = Instant.parse("2026-06-05T12:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2026-06-05");

    @Test
    void mapExperienceDailyMapsNullableIntegerFieldsAndTimestamp() throws Exception {
        Probe support = new Probe();
        ResultSet rs = experienceDailyResultSet();

        HighscoreApiQueryService.ExperienceDailyView view = support.daily(rs);

        assertThat(view.date()).isEqualTo(DAY);
        assertThat(view.rank()).isEqualTo(1);
        assertThat(view.characterName()).isEqualTo("Knight One");
        assertThat(view.characterId()).isEqualTo(10L);
        assertThat(view.world()).isEqualTo("Antica");
        assertThat(view.vocationFilterId()).isEqualTo(0);
        assertThat(view.experience()).isEqualTo(123456L);
        assertThat(view.level()).isEqualTo(300);
        assertThat(view.firstSeenFilter()).isNull();
        assertThat(view.scrapedAt()).isEqualTo(NOW);
    }

    @Test
    void mapExperienceGainMapsNullableRankFields() throws Exception {
        Probe support = new Probe();
        ResultSet rs = experienceGainResultSet();

        HighscoreApiQueryService.ExperienceGainView view = support.gain(rs);

        assertThat(view.characterName()).isEqualTo("Knight One");
        assertThat(view.characterId()).isEqualTo(10L);
        assertThat(view.world()).isEqualTo("Antica");
        assertThat(view.startDate()).isEqualTo(DAY.minusDays(1));
        assertThat(view.endDate()).isEqualTo(DAY);
        assertThat(view.startExperience()).isEqualTo(1000L);
        assertThat(view.endExperience()).isEqualTo(1500L);
        assertThat(view.gain()).isEqualTo(500L);
        assertThat(view.startRank()).isEqualTo(5);
        assertThat(view.endRank()).isNull();
        assertThat(view.vocationFilterId()).isEqualTo(0);
    }

    @Test
    void mapCurrentHighscoreCoversAllKnownCategoryNamesAndUnknownFallback() throws Exception {
        Probe support = new Probe();
        Map<Integer, String> expected = Map.ofEntries(
                entry(1, StatCategory.ACHIEVEMENTS.name()),
                entry(2, StatCategory.AXE_FIGHTING.name()),
                entry(3, StatCategory.CHARM_POINTS.name()),
                entry(4, StatCategory.CLUB_FIGHTING.name()),
                entry(5, StatCategory.DISTANCE_FIGHTING.name()),
                entry(6, StatCategory.EXPERIENCE.name()),
                entry(7, StatCategory.FISHING.name()),
                entry(8, StatCategory.FIST_FIGHTING.name()),
                entry(9, StatCategory.GOSHNARS_TAINT.name()),
                entry(10, StatCategory.LOYALTY_POINTS.name()),
                entry(11, StatCategory.MAGIC_LEVEL.name()),
                entry(12, StatCategory.SHIELDING.name()),
                entry(13, StatCategory.SWORD_FIGHTING.name()),
                entry(14, StatCategory.DROME_SCORE.name()),
                entry(15, StatCategory.BOSS_POINTS.name()),
                entry(16, StatCategory.BOUNTY_POINTS_EARNED.name()),
                entry(17, StatCategory.WEEKLY_TASKS_COMPLETED.name())
        );

        for (Map.Entry<Integer, String> entry : expected.entrySet()) {
            HighscoreApiQueryService.CurrentHighscoreView view = support.current(currentHighscoreResultSet(entry.getKey()));
            assertThat(view.category()).isEqualTo(entry.getValue());
            assertThat(view.categoryId()).isEqualTo(entry.getKey());
            assertThat(view.scrapedAt()).isEqualTo(NOW);
        }

        assertThat(support.current(currentHighscoreResultSet(99)).category()).isEqualTo("UNKNOWN");
    }

    @Test
    void mapPeriodHighscoreMapsDatesAndUnknownCategoryFallback() throws Exception {
        Probe support = new Probe();

        HighscoreApiQueryService.PeriodHighscoreView view = support.period(periodHighscoreResultSet(99));

        assertThat(view.id()).isEqualTo(31L);
        assertThat(view.rank()).isEqualTo(2);
        assertThat(view.characterName()).isEqualTo("Knight One");
        assertThat(view.world()).isEqualTo("Antica");
        assertThat(view.category()).isEqualTo("UNKNOWN");
        assertThat(view.categoryId()).isEqualTo(99);
        assertThat(view.validFrom()).isEqualTo(DAY.minusDays(3));
        assertThat(view.validUntil()).isEqualTo(DAY);
        assertThat(view.createdAt()).isEqualTo(NOW);
    }

    @Test
    void categoryCodeCoversEveryStatCategoryAndNormalizeRequiredRejectsBlankValues() {
        Probe support = new Probe();
        Map<StatCategory, Short> expected = new EnumMap<>(StatCategory.class);
        expected.put(StatCategory.ACHIEVEMENTS, (short) 1);
        expected.put(StatCategory.AXE_FIGHTING, (short) 2);
        expected.put(StatCategory.BOSS_POINTS, (short) 15);
        expected.put(StatCategory.BOUNTY_POINTS_EARNED, (short) 16);
        expected.put(StatCategory.CHARM_POINTS, (short) 3);
        expected.put(StatCategory.CLUB_FIGHTING, (short) 4);
        expected.put(StatCategory.DISTANCE_FIGHTING, (short) 5);
        expected.put(StatCategory.DROME_SCORE, (short) 14);
        expected.put(StatCategory.EXPERIENCE, (short) 6);
        expected.put(StatCategory.FISHING, (short) 7);
        expected.put(StatCategory.FIST_FIGHTING, (short) 8);
        expected.put(StatCategory.GOSHNARS_TAINT, (short) 9);
        expected.put(StatCategory.LOYALTY_POINTS, (short) 10);
        expected.put(StatCategory.MAGIC_LEVEL, (short) 11);
        expected.put(StatCategory.SHIELDING, (short) 12);
        expected.put(StatCategory.SWORD_FIGHTING, (short) 13);
        expected.put(StatCategory.WEEKLY_TASKS_COMPLETED, (short) 17);

        for (StatCategory category : StatCategory.values()) {
            assertThat(support.code(category)).isEqualTo(expected.get(category));
        }
        assertThat(support.required(" Antica ", "world")).isEqualTo("Antica");
        assertThatThrownBy(() -> support.required(" ", "world"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("world is required");
    }

    private static ResultSet experienceDailyResultSet() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("date", LocalDate.class)).thenReturn(DAY);
        when(rs.getInt("rank")).thenReturn(1);
        when(rs.getString("character_name")).thenReturn("Knight One");
        when(rs.getLong("character_id")).thenReturn(10L);
        when(rs.getString("world")).thenReturn("Antica");
        when(rs.getInt("vocation_filter_id")).thenReturn(0);
        when(rs.getLong("experience")).thenReturn(123456L);
        when(rs.getInt("level")).thenReturn(300);
        when(rs.getInt("first_seen_filter")).thenReturn(0);
        when(rs.getTimestamp("scraped_at")).thenReturn(Timestamp.from(NOW));
        when(rs.wasNull()).thenReturn(false, false, false, true);
        return rs;
    }

    private static ResultSet experienceGainResultSet() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("character_name")).thenReturn("Knight One");
        when(rs.getLong("character_id")).thenReturn(10L);
        when(rs.getString("world")).thenReturn("Antica");
        when(rs.getObject("start_date", LocalDate.class)).thenReturn(DAY.minusDays(1));
        when(rs.getObject("end_date", LocalDate.class)).thenReturn(DAY);
        when(rs.getLong("start_experience")).thenReturn(1000L);
        when(rs.getLong("end_experience")).thenReturn(1500L);
        when(rs.getLong("gain")).thenReturn(500L);
        when(rs.getInt("start_rank")).thenReturn(5);
        when(rs.getInt("end_rank")).thenReturn(0);
        when(rs.getInt("vocation_filter_id")).thenReturn(0);
        when(rs.wasNull()).thenReturn(false, true, false);
        return rs;
    }

    private static ResultSet currentHighscoreResultSet(int category) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(21L);
        when(rs.getInt("rank")).thenReturn(1);
        when(rs.getString("character_name")).thenReturn("Knight One");
        when(rs.getLong("character_id")).thenReturn(10L);
        when(rs.getString("world")).thenReturn("Antica");
        when(rs.getInt("category")).thenReturn(category);
        when(rs.getInt("vocation_filter_id")).thenReturn(4);
        when(rs.getLong("value")).thenReturn(999L);
        when(rs.getObject("first_seen_date", LocalDate.class)).thenReturn(DAY.minusDays(2));
        when(rs.getObject("last_seen_date", LocalDate.class)).thenReturn(DAY.minusDays(1));
        when(rs.getObject("last_changed_date", LocalDate.class)).thenReturn(DAY);
        when(rs.getTimestamp("scraped_at")).thenReturn(Timestamp.from(NOW));
        when(rs.wasNull()).thenReturn(false);
        return rs;
    }

    private static ResultSet periodHighscoreResultSet(int category) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(31L);
        when(rs.getInt("rank")).thenReturn(2);
        when(rs.getString("character_name")).thenReturn("Knight One");
        when(rs.getLong("character_id")).thenReturn(10L);
        when(rs.getString("world")).thenReturn("Antica");
        when(rs.getInt("category")).thenReturn(category);
        when(rs.getInt("vocation_filter_id")).thenReturn(4);
        when(rs.getLong("value")).thenReturn(888L);
        when(rs.getObject("valid_from", LocalDate.class)).thenReturn(DAY.minusDays(3));
        when(rs.getObject("valid_until", LocalDate.class)).thenReturn(DAY);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(NOW));
        when(rs.wasNull()).thenReturn(false);
        return rs;
    }

    private static final class Probe extends HighscoreApiJdbcSupport {
        private Probe() {
            super(mock(JdbcTemplate.class));
        }

        private HighscoreApiQueryService.ExperienceDailyView daily(ResultSet rs) throws Exception {
            return mapExperienceDaily(rs, 0);
        }

        private HighscoreApiQueryService.ExperienceGainView gain(ResultSet rs) throws Exception {
            return mapExperienceGain(rs, 0);
        }

        private HighscoreApiQueryService.CurrentHighscoreView current(ResultSet rs) throws Exception {
            return mapCurrentHighscore(rs, 0);
        }

        private HighscoreApiQueryService.PeriodHighscoreView period(ResultSet rs) throws Exception {
            return mapPeriodHighscore(rs, 0);
        }

        private short code(StatCategory category) {
            return categoryCode(category);
        }

        private String required(String value, String name) {
            return normalizeRequired(value, name);
        }
    }
}
