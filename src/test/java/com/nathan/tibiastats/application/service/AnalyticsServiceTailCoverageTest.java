package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceTailCoverageTest {
    @Test
    void getWorldOnlineHistoryMapsScrapesIntoRecordPoints() {
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        AnalyticsService service = new AnalyticsService(worlds);
        World world = new World("Antica", null, "Europe");
        Instant from = Instant.parse("2026-06-05T10:00:00Z");
        Instant to = Instant.parse("2026-06-05T12:00:00Z");
        Scrape first = new Scrape(world, from.plusSeconds(60), 100, null);
        Scrape second = new Scrape(world, from.plusSeconds(120), 150, null);

        when(worlds.findByName("Antica")).thenReturn(Optional.of(world));
        when(worlds.findScrapesByWorldAndRange(world, from, to)).thenReturn(List.of(first, second));

        var history = service.getWorldOnlineHistory("Antica", from, to);

        assertThat(history).hasSize(2);
        assertThat(history.getFirst().timestamp()).isEqualTo(first.getScrapeTime());
        assertThat(history.getFirst().playersOnline()).isEqualTo(100);
        assertThat(history.get(1).timestamp()).isEqualTo(second.getScrapeTime());
        assertThat(history.get(1).playersOnline()).isEqualTo(150);
    }
}
