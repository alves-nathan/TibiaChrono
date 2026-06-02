package com.nathan.tibiastats.application.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScrapeServiceTest {
    @Test
    void updateAllWorldsReturnsEmptyWhenOverviewHasNoWorlds() {
        WorldScrapeClient client = mock(WorldScrapeClient.class);
        WorldScrapePersistenceService persistence = mock(WorldScrapePersistenceService.class);
        when(client.fetchWorldsOverview()).thenReturn(List.of());
        ScrapeService service = new ScrapeService(client, persistence);

        ScrapeJobResult result = service.updateAllWorlds();

        assertThat(result).isEqualTo(ScrapeJobResult.empty());
        verify(persistence, never()).saveWorldScrape(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateAllWorldsPersistsSuccessfulWorldPages() {
        WorldScrapeClient client = mock(WorldScrapeClient.class);
        WorldScrapePersistenceService persistence = mock(WorldScrapePersistenceService.class);
        WorldScrapeSnapshot.Target antica = target("Antica");
        WorldScrapeSnapshot.Target bona = target("Bona");
        WorldScrapeSnapshot.Page anticaPage = page(antica, 100);
        WorldScrapeSnapshot.Page bonaPage = page(bona, 20);
        when(client.fetchWorldsOverview()).thenReturn(List.of(antica, bona));
        when(client.fetchWorldPage(antica)).thenReturn(anticaPage);
        when(client.fetchWorldPage(bona)).thenReturn(bonaPage);
        ScrapeService service = new ScrapeService(client, persistence);

        ScrapeJobResult result = service.updateAllWorlds();

        assertThat(result).isEqualTo(ScrapeJobResult.of(2, 0, 2, 0));
        verify(persistence).saveWorldScrape(anticaPage);
        verify(persistence).saveWorldScrape(bonaPage);
    }

    @Test
    void updateAllWorldsContinuesAfterOneWorldFails() {
        WorldScrapeClient client = mock(WorldScrapeClient.class);
        WorldScrapePersistenceService persistence = mock(WorldScrapePersistenceService.class);
        WorldScrapeSnapshot.Target antica = target("Antica");
        WorldScrapeSnapshot.Target broken = target("Broken");
        WorldScrapeSnapshot.Target bona = target("Bona");
        WorldScrapeSnapshot.Page anticaPage = page(antica, 100);
        WorldScrapeSnapshot.Page bonaPage = page(bona, 20);
        when(client.fetchWorldsOverview()).thenReturn(List.of(antica, broken, bona));
        when(client.fetchWorldPage(antica)).thenReturn(anticaPage);
        when(client.fetchWorldPage(broken)).thenThrow(new IllegalStateException("remote error"));
        when(client.fetchWorldPage(bona)).thenReturn(bonaPage);
        ScrapeService service = new ScrapeService(client, persistence);

        ScrapeJobResult result = service.updateAllWorlds();

        assertThat(result).isEqualTo(ScrapeJobResult.of(3, 0, 2, 1));
        verify(persistence).saveWorldScrape(anticaPage);
        verify(persistence).saveWorldScrape(bonaPage);
    }

    @Test
    void updateAllWorldsCountsPersistenceFailureAsFailedWorld() {
        WorldScrapeClient client = mock(WorldScrapeClient.class);
        WorldScrapePersistenceService persistence = mock(WorldScrapePersistenceService.class);
        WorldScrapeSnapshot.Target antica = target("Antica");
        WorldScrapeSnapshot.Page anticaPage = page(antica, 100);
        when(client.fetchWorldsOverview()).thenReturn(List.of(antica));
        when(client.fetchWorldPage(antica)).thenReturn(anticaPage);
        doThrow(new IllegalStateException("db error")).when(persistence).saveWorldScrape(anticaPage);
        ScrapeService service = new ScrapeService(client, persistence);

        ScrapeJobResult result = service.updateAllWorlds();

        assertThat(result).isEqualTo(ScrapeJobResult.of(1, 0, 0, 1));
    }

    private WorldScrapeSnapshot.Target target(String name) {
        return new WorldScrapeSnapshot.Target(name, "Open PvP", "EU", 0, "regular", "regular");
    }

    private WorldScrapeSnapshot.Page page(WorldScrapeSnapshot.Target target, int playersOnline) {
        return new WorldScrapeSnapshot.Page(
                target,
                target.name(),
                playersOnline,
                List.of(),
                "record",
                LocalDate.parse("1997-01-01"),
                "regular",
                "regular"
        );
    }
}
