package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CharacterTimelineHighscoreCategoryCoverageTest {
    @Test
    void categoryNameCoversAllTibiaHighscoreCategoryCodesAndUnknownFallback() throws Exception {
        CharacterTimelineHighscoreReadModelService service =
                new CharacterTimelineHighscoreReadModelService(mock(JdbcTemplate.class));
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
            assertThat(categoryName(service, entry.getKey())).isEqualTo(entry.getValue());
        }
        assertThat(categoryName(service, 99)).isEqualTo("UNKNOWN");
    }

    private static String categoryName(CharacterTimelineHighscoreReadModelService service, int code) throws Exception {
        Method method = CharacterTimelineHighscoreReadModelService.class.getDeclaredMethod("categoryName", int.class);
        method.setAccessible(true);
        return (String) method.invoke(service, code);
    }
}
