package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.AnalyticsService;
import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.AnalyticsQueryPort.RecordPoint;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnlineControllerCoverageTest {

    @Test
    void totalReturnsCurrentOnlineTotalFromAnalyticsService() {
        AnalyticsService analytics = mock(AnalyticsService.class);
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        OnlineController controller = new OnlineController(analytics, worlds);
        when(analytics.getCurrentOnlineTotal()).thenReturn(123);

        assertThat(controller.total()).containsEntry("total", 123);
    }

    @Test
    void worldsNowMapsLatestScrapeWhenPresentAndDefaultsToZeroWhenMissing() {
        AnalyticsService analytics = mock(AnalyticsService.class);
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        OnlineController controller = new OnlineController(analytics, worlds);
        World antica = new World("Antica", "Optional PvP", "Europe");
        World bona = new World("Bona", "Optional PvP", "South America");
        when(worlds.findAll()).thenReturn(List.of(antica, bona));
        when(worlds.findLatestByWorld(antica)).thenReturn(Optional.of(scrape(antica, 42)));
        when(worlds.findLatestByWorld(bona)).thenReturn(Optional.empty());

        List<java.util.Map<String, Object>> result = controller.worldsNow();

        assertThat(result).hasSize(2);
        assertThat(result.get(0))
                .containsEntry("world", "Antica")
                .containsEntry("playersOnline", 42);
        assertThat(result.get(1))
                .containsEntry("world", "Bona")
                .containsEntry("playersOnline", 0);
    }

    @Test
    void worldNowMapsLatestScrapeForNamedWorld() {
        AnalyticsService analytics = mock(AnalyticsService.class);
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        OnlineController controller = new OnlineController(analytics, worlds);
        World antica = new World("Antica", "Optional PvP", "Europe");
        when(worlds.findByName("Antica")).thenReturn(Optional.of(antica));
        when(worlds.findLatestByWorld(antica)).thenReturn(Optional.of(scrape(antica, 77)));

        assertThat(controller.worldNow("Antica"))
                .containsEntry("world", "Antica")
                .containsEntry("playersOnline", 77);
    }

    @Test
    void historyUsesProvidedEpochMillisRangeAndMapsRecordPoints() {
        AnalyticsService analytics = mock(AnalyticsService.class);
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        OnlineController controller = new OnlineController(analytics, worlds);
        Instant from = Instant.parse("2026-06-04T10:00:00Z");
        Instant to = Instant.parse("2026-06-04T11:00:00Z");
        Instant pointTime = Instant.parse("2026-06-04T10:30:00Z");
        when(analytics.getWorldOnlineHistory("Antica", from, to))
                .thenReturn(List.of(new RecordPoint(pointTime, 88)));

        List<java.util.Map<String, Object>> result = controller.history("Antica", from.toEpochMilli(), to.toEpochMilli());

        assertThat(result).singleElement()
                .satisfies(point -> assertThat(point)
                        .containsEntry("timestamp", pointTime.toString())
                        .containsEntry("playersOnline", 88));
        verify(analytics).getWorldOnlineHistory(eq("Antica"), eq(from), eq(to));
    }

    private static Scrape scrape(World world, int playersOnline) {
        return new Scrape(world, Instant.parse("2026-06-04T12:00:00Z"), playersOnline, "");
    }
}
