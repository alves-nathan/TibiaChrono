package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TibiaHighscoreHttpClientTailCoverageTest {
    @Test
    void buildHighscoreUrlEncodesWorldAndMapsCategoryVocationAndPage() throws Exception {
        TibiaHighscoreHttpClient client = new TibiaHighscoreHttpClient();

        String url = invokeBuildHighscoreUrl(
                client,
                "Wintera Space",
                StatCategory.EXPERIENCE,
                4,
                7
        );

        assertThat(url)
                .contains("subtopic=highscores")
                .contains("world=Wintera%20Space")
                .contains("beprotection=-1")
                .contains("profession=4")
                .contains("category=6")
                .contains("currentpage=7");
    }

    @Test
    void mapCategoryCoversEveryTibiaHighscoreCategoryCode() throws Exception {
        TibiaHighscoreHttpClient client = new TibiaHighscoreHttpClient();
        Map<StatCategory, Integer> expected = new EnumMap<>(StatCategory.class);
        expected.put(StatCategory.ACHIEVEMENTS, 1);
        expected.put(StatCategory.AXE_FIGHTING, 2);
        expected.put(StatCategory.CHARM_POINTS, 3);
        expected.put(StatCategory.CLUB_FIGHTING, 4);
        expected.put(StatCategory.DISTANCE_FIGHTING, 5);
        expected.put(StatCategory.EXPERIENCE, 6);
        expected.put(StatCategory.FISHING, 7);
        expected.put(StatCategory.FIST_FIGHTING, 8);
        expected.put(StatCategory.GOSHNARS_TAINT, 9);
        expected.put(StatCategory.LOYALTY_POINTS, 10);
        expected.put(StatCategory.MAGIC_LEVEL, 11);
        expected.put(StatCategory.SHIELDING, 12);
        expected.put(StatCategory.SWORD_FIGHTING, 13);
        expected.put(StatCategory.DROME_SCORE, 14);
        expected.put(StatCategory.BOSS_POINTS, 15);
        expected.put(StatCategory.BOUNTY_POINTS_EARNED, 16);
        expected.put(StatCategory.WEEKLY_TASKS_COMPLETED, 17);

        for (Map.Entry<StatCategory, Integer> entry : expected.entrySet()) {
            assertThat(invokeMapCategory(client, entry.getKey()))
                    .as(entry.getKey().name())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    void highscorePageRecordKeepsHtmlAndSourceUrl() {
        TibiaHighscoreHttpClient.HighscorePage page =
                new TibiaHighscoreHttpClient.HighscorePage("<html>ok</html>", "https://example.test/highscores");

        assertThat(page.html()).isEqualTo("<html>ok</html>");
        assertThat(page.sourceUrl()).isEqualTo("https://example.test/highscores");
        assertThat(page.toString()).contains("HighscorePage");
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
