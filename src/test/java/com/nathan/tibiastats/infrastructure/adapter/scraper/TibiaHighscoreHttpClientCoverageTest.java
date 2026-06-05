package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

class TibiaHighscoreHttpClientCoverageTest {

    @Test
    void buildHighscoreUrlEncodesWorldAndMapsCategoryWithoutNetworkAccess() throws Exception {
        TibiaHighscoreHttpClient client = new TibiaHighscoreHttpClient();

        String url = invokeBuildHighscoreUrl(client, "Wintera Test", StatCategory.EXPERIENCE, 4, 7);

        assertThat(url)
                .contains("subtopic=highscores")
                .contains("world=Wintera%20Test")
                .contains("beprotection=-1")
                .contains("profession=4")
                .contains("category=6")
                .contains("currentpage=7");
        assertThat(url).doesNotContain("Wintera+Test");
    }

    @Test
    void mapCategoryCoversEverySupportedTibiaHighscoreCategory() throws Exception {
        TibiaHighscoreHttpClient client = new TibiaHighscoreHttpClient();
        Map<StatCategory, Integer> expected = Map.ofEntries(
                entry(StatCategory.ACHIEVEMENTS, 1),
                entry(StatCategory.AXE_FIGHTING, 2),
                entry(StatCategory.BOSS_POINTS, 15),
                entry(StatCategory.BOUNTY_POINTS_EARNED, 16),
                entry(StatCategory.CHARM_POINTS, 3),
                entry(StatCategory.CLUB_FIGHTING, 4),
                entry(StatCategory.DISTANCE_FIGHTING, 5),
                entry(StatCategory.DROME_SCORE, 14),
                entry(StatCategory.EXPERIENCE, 6),
                entry(StatCategory.FISHING, 7),
                entry(StatCategory.FIST_FIGHTING, 8),
                entry(StatCategory.GOSHNARS_TAINT, 9),
                entry(StatCategory.LOYALTY_POINTS, 10),
                entry(StatCategory.MAGIC_LEVEL, 11),
                entry(StatCategory.SHIELDING, 12),
                entry(StatCategory.SWORD_FIGHTING, 13),
                entry(StatCategory.WEEKLY_TASKS_COMPLETED, 17)
        );

        Map<StatCategory, Integer> actual = new EnumMap<>(StatCategory.class);
        for (StatCategory category : StatCategory.values()) {
            actual.put(category, invokeMapCategory(client, category));
        }

        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
    }

    private static String invokeBuildHighscoreUrl(
            TibiaHighscoreHttpClient client,
            String world,
            StatCategory category,
            int vocationId,
            int page
    ) throws Exception {
        Method method = TibiaHighscoreHttpClient.class.getDeclaredMethod(
                "buildHighscoreUrl",
                String.class,
                StatCategory.class,
                int.class,
                int.class
        );
        method.setAccessible(true);
        return (String) method.invoke(client, world, category, vocationId, page);
    }

    private static int invokeMapCategory(TibiaHighscoreHttpClient client, StatCategory category) throws Exception {
        Method method = TibiaHighscoreHttpClient.class.getDeclaredMethod("mapCategory", StatCategory.class);
        method.setAccessible(true);
        return (Integer) method.invoke(client, category);
    }
}
