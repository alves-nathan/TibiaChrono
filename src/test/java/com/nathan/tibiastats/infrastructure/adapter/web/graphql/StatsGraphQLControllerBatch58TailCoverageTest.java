package com.nathan.tibiastats.infrastructure.adapter.web.graphql;

import com.nathan.tibiastats.application.query.ApiQueryService;
import com.nathan.tibiastats.application.service.AnalyticsService;
import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.AnalyticsQueryPort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsGraphQLControllerBatch58TailCoverageTest {
    @Test
    void graphqlControllerCoversDefaultBranchesAndDelegations() {
        AnalyticsService analytics = mock(AnalyticsService.class);
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        ApiQueryService queries = mock(ApiQueryService.class);
        StatsGraphQLController controller = new StatsGraphQLController(analytics, worlds, queries);
        World antica = new World("Antica", "Open PvP", "EU");
        Scrape latest = new Scrape(antica, Instant.parse("2026-06-05T12:00:00Z"), 123, null);

        when(analytics.getCurrentOnlineTotal()).thenReturn(321);
        when(worlds.findAll()).thenReturn(List.of(antica));
        when(worlds.findLatestByWorld(antica)).thenReturn(Optional.empty(), Optional.of(latest));
        when(worlds.findByName("Antica")).thenReturn(Optional.of(antica));
        when(analytics.getWorldOnlineHistory(eq("Antica"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new AnalyticsQueryPort.RecordPoint(Instant.parse("2026-06-05T11:00:00Z"), 111)));
        when(queries.findWorlds()).thenReturn(List.of());
        when(queries.findWorld("Antica")).thenReturn(Optional.empty());
        when(queries.findCharacter("Knight")).thenReturn(Optional.empty());
        when(queries.findCharacterNames("Knight")).thenReturn(List.of());
        when(queries.findCharacterHighscores("Knight", StatCategory.EXPERIENCE, "Antica", 0, LocalDate.parse("2026-06-01"), null, 100)).thenReturn(List.of());
        when(queries.findHighscores("Antica", StatCategory.EXPERIENCE, 0, LocalDate.parse("2026-06-05"), 100)).thenReturn(List.of());
        when(queries.findScrapeJobs("WORLD_SCRAPER", "SUCCESS", 50)).thenReturn(List.of());
        when(queries.findCharacterHighscores("Knight", StatCategory.MAGIC_LEVEL, null, null, null, null, 100)).thenReturn(List.of());

        assertThat(controller.onlineTotal()).isEqualTo(321);
        assertThat(controller.worldsOnline()).singleElement()
                .satisfies(world -> assertThat(world).containsEntry("playersOnline", 0));
        assertThat(controller.worldOnlineNow("Antica"))
                .satisfies(world -> assertThat(world).containsEntry("playersOnline", 123));
        assertThat(controller.worldOnlineHistory("Antica", null, null)).singleElement()
                .satisfies(point -> assertThat(point).containsEntry("playersOnline", 111));
        assertThat(controller.worlds()).isEmpty();
        assertThat(controller.world("Antica")).isNull();
        assertThat(controller.character("Knight")).isNull();
        assertThat(controller.characterNames("Knight")).isEmpty();
        assertThat(controller.characterHighscores("Knight", StatCategory.EXPERIENCE, "Antica", 0, "2026-06-01", null, null)).isEmpty();
        assertThat(controller.highscores("Antica", StatCategory.EXPERIENCE, 0, "2026-06-05", null)).isEmpty();
        assertThat(controller.scrapeJobs("WORLD_SCRAPER", "SUCCESS", null)).isEmpty();
        assertThat(controller.characterStatHistory("Knight", StatCategory.MAGIC_LEVEL)).isEmpty();

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(analytics).getWorldOnlineHistory(eq("Antica"), fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getValue()).isBefore(toCaptor.getValue());
    }
}
