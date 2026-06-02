package com.nathan.tibiastats.infrastructure.adapter.web.graphql;

import com.nathan.tibiastats.application.query.ApiQueryService;
import com.nathan.tibiastats.application.service.AnalyticsService;
import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.AnalyticsQueryPort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsGraphQLControllerTest {
    private final AnalyticsService analytics = mock(AnalyticsService.class);
    private final WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
    private final ApiQueryService queries = mock(ApiQueryService.class);
    private final StatsGraphQLController controller = new StatsGraphQLController(analytics, worlds, queries);

    @Test
    void onlineTotalDelegatesToAnalyticsService() {
        when(analytics.getCurrentOnlineTotal()).thenReturn(1234);

        assertThat(controller.onlineTotal()).isEqualTo(1234);
    }

    @Test
    void worldsOnlineMapsLatestScrapeAndDefaultsMissingLatestToZero() {
        World antica = new World("Antica", "Open PvP", "EU");
        World empty = new World("Noctera", "Optional PvP", "NA");
        Scrape latest = new Scrape(antica, Instant.parse("2026-01-01T00:00:00Z"), 321, null);
        when(worlds.findAll()).thenReturn(List.of(antica, empty));
        when(worlds.findLatestByWorld(antica)).thenReturn(Optional.of(latest));
        when(worlds.findLatestByWorld(empty)).thenReturn(Optional.empty());

        List<Map<String, Object>> result = controller.worldsOnline();

        assertThat(result).hasSize(2);
        assertThat(result.get(0))
                .containsEntry("name", "Antica")
                .containsEntry("playersOnline", 321);
        assertThat(result.get(1))
                .containsEntry("name", "Noctera")
                .containsEntry("playersOnline", 0);
    }

    @Test
    void worldOnlineNowUsesWorldLookupAndDefaultsMissingLatestToZero() {
        World antica = new World("Antica", "Open PvP", "EU");
        when(worlds.findByName("Antica")).thenReturn(Optional.of(antica));
        when(worlds.findLatestByWorld(antica)).thenReturn(Optional.empty());

        Map<String, Object> result = controller.worldOnlineNow("Antica");

        assertThat(result)
                .containsEntry("name", "Antica")
                .containsEntry("playersOnline", 0);
    }

    @Test
    void worldOnlineNowPropagatesMissingWorldAsNoSuchElementException() {
        when(worlds.findByName("Missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.worldOnlineNow("Missing"))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void worldOnlineHistoryParsesExplicitRangeAndMapsRecordPoints() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-02T00:00:00Z");
        when(analytics.getWorldOnlineHistory("Antica", from, to))
                .thenReturn(List.of(new AnalyticsQueryPort.RecordPoint(from.plusSeconds(60), 200)));

        List<Map<String, Object>> result = controller.worldOnlineHistory(
                "Antica",
                "2026-01-01T00:00:00Z",
                "2026-01-02T00:00:00Z"
        );

        assertThat(result).singleElement()
                .satisfies(point -> assertThat(point)
                        .containsEntry("timestamp", "2026-01-01T00:01:00Z")
                        .containsEntry("playersOnline", 200));
        verify(analytics).getWorldOnlineHistory("Antica", from, to);
    }

    @Test
    void queryFacadeMethodsDelegateToApiQueryServiceAndApplyDefaults() {
        ApiQueryService.WorldView worldView = new ApiQueryService.WorldView(
                1,
                "Antica",
                "Open PvP",
                "EU",
                "1,234 players",
                LocalDate.parse("1997-01-01"),
                "blocked",
                "regular",
                321,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        ApiQueryService.CharacterView characterView = new ApiQueryService.CharacterView(
                10L,
                "Knight Hero",
                250,
                "male",
                "Knight",
                "Elite Knight",
                123,
                "Thais",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                "Premium Account",
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                "SUCCESS"
        );
        ApiQueryService.CharacterNameView nameView = new ApiQueryService.CharacterNameView(
                100L,
                10L,
                "Knight Hero",
                true,
                null
        );
        ApiQueryService.HighscoreView highscoreView = new ApiQueryService.HighscoreView(
                99L,
                1,
                "Knight Hero",
                10L,
                "Antica",
                "EXPERIENCE",
                0,
                LocalDate.parse("2026-01-01"),
                123_456L,
                Instant.parse("2026-01-01T01:00:00Z")
        );
        ApiQueryService.ScrapeJobView scrapeJobView = new ApiQueryService.ScrapeJobView(
                7L,
                "HIGHSCORE_SCRAPER",
                "SUCCESS",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:01:00Z"),
                60_000L,
                10,
                2,
                8,
                0,
                null
        );

        when(queries.findWorlds()).thenReturn(List.of(worldView));
        when(queries.findWorld("Antica")).thenReturn(Optional.of(worldView));
        when(queries.findWorld("Missing")).thenReturn(Optional.empty());
        when(queries.findCharacter("Knight Hero")).thenReturn(Optional.of(characterView));
        when(queries.findCharacter("Missing")).thenReturn(Optional.empty());
        when(queries.findCharacterNames("Knight Hero")).thenReturn(List.of(nameView));
        when(queries.findCharacterHighscores(
                "Knight Hero",
                StatCategory.EXPERIENCE,
                "Antica",
                0,
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                25
        )).thenReturn(List.of(highscoreView));
        when(queries.findCharacterHighscores(
                "Knight Hero",
                StatCategory.MAGIC_LEVEL,
                null,
                null,
                null,
                null,
                100
        )).thenReturn(List.of(highscoreView));
        when(queries.findHighscores(
                "Antica",
                StatCategory.EXPERIENCE,
                0,
                LocalDate.parse("2026-01-01"),
                100
        )).thenReturn(List.of(highscoreView));
        when(queries.findScrapeJobs("HIGHSCORE_SCRAPER", "SUCCESS", 50)).thenReturn(List.of(scrapeJobView));

        assertThat(controller.worlds()).containsExactly(worldView);
        assertThat(controller.world("Antica")).isSameAs(worldView);
        assertThat(controller.world("Missing")).isNull();
        assertThat(controller.character("Knight Hero")).isSameAs(characterView);
        assertThat(controller.character("Missing")).isNull();
        assertThat(controller.characterNames("Knight Hero")).containsExactly(nameView);
        assertThat(controller.characterHighscores(
                "Knight Hero",
                StatCategory.EXPERIENCE,
                "Antica",
                0,
                "2026-01-01",
                "2026-01-31",
                25
        )).containsExactly(highscoreView);
        assertThat(controller.characterStatHistory("Knight Hero", StatCategory.MAGIC_LEVEL))
                .containsExactly(highscoreView);
        assertThat(controller.highscores("Antica", StatCategory.EXPERIENCE, 0, "2026-01-01", null))
                .containsExactly(highscoreView);
        assertThat(controller.scrapeJobs("HIGHSCORE_SCRAPER", "SUCCESS", null))
                .containsExactly(scrapeJobView);
        assertThat(highscoreView.valueText()).isEqualTo("123456");
    }
}
