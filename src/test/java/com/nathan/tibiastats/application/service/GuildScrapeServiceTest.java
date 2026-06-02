package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.GuildScrapeProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuildScrapeServiceTest {
    @Test
    void updateKnownGuildsAggregatesListAndDetailCountersAndFailures() {
        GuildScrapeTargetPlanner targets = mock(GuildScrapeTargetPlanner.class);
        GuildCatalogService catalog = mock(GuildCatalogService.class);
        GuildDetailScrapeService details = mock(GuildDetailScrapeService.class);
        GuildScrapeProperties properties = new GuildScrapeProperties();
        properties.setPageDelayMs(0);
        properties.setWorldLimit(2);
        properties.setGuildLimit(2);
        GuildScrapeService service = new GuildScrapeService(targets, catalog, details, properties);
        when(targets.worldNames(2)).thenReturn(List.of("Antica", "Broken World"));
        when(targets.guildNamesForDetailsRefresh(2)).thenReturn(List.of("Raw Raw", "Broken Guild"));
        when(catalog.updateGuildListForWorld("Antica"))
                .thenReturn(new GuildScrapeService.GuildListResult(3, 1, 2));
        when(catalog.updateGuildListForWorld("Broken World"))
                .thenThrow(new IllegalStateException("list failed"));
        when(details.updateGuildDetail("Raw Raw"))
                .thenReturn(new GuildScrapeService.GuildDetailResult("Raw Raw", 5, 2, 1, 1, 0));
        when(details.updateGuildDetail("Broken Guild"))
                .thenThrow(new IllegalStateException("detail failed"));

        ScrapeJobResult result = service.updateKnownGuilds();

        assertThat(result).isEqualTo(ScrapeJobResult.of(8, 3, 4, 2));
        verify(catalog).updateGuildListForWorld("Antica");
        verify(catalog).updateGuildListForWorld("Broken World");
        verify(details).updateGuildDetail("Raw Raw");
        verify(details).updateGuildDetail("Broken Guild");
    }

    @Test
    void updateKnownGuildsCanRunOnlyDetailsWhenListIsDisabled() {
        GuildScrapeTargetPlanner targets = mock(GuildScrapeTargetPlanner.class);
        GuildCatalogService catalog = mock(GuildCatalogService.class);
        GuildDetailScrapeService details = mock(GuildDetailScrapeService.class);
        GuildScrapeProperties properties = new GuildScrapeProperties();
        properties.setPageDelayMs(0);
        properties.setListEnabled(false);
        properties.setDetailsEnabled(true);
        properties.setGuildLimit(1);
        GuildScrapeService service = new GuildScrapeService(targets, catalog, details, properties);
        when(targets.guildNamesForDetailsRefresh(1)).thenReturn(List.of("Raw Raw"));
        when(details.updateGuildDetail("Raw Raw"))
                .thenReturn(new GuildScrapeService.GuildDetailResult("Raw Raw", 1, 1, 0, 0, 0));

        ScrapeJobResult result = service.updateKnownGuilds();

        assertThat(result).isEqualTo(ScrapeJobResult.of(1, 1, 0, 0));
        verify(targets, never()).worldNames(org.mockito.ArgumentMatchers.anyInt());
        verify(catalog, never()).updateGuildListForWorld(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void updateKnownGuildsReturnsEmptyWhenBothModesAreDisabled() {
        GuildScrapeTargetPlanner targets = mock(GuildScrapeTargetPlanner.class);
        GuildCatalogService catalog = mock(GuildCatalogService.class);
        GuildDetailScrapeService details = mock(GuildDetailScrapeService.class);
        GuildScrapeProperties properties = new GuildScrapeProperties();
        properties.setListEnabled(false);
        properties.setDetailsEnabled(false);
        GuildScrapeService service = new GuildScrapeService(targets, catalog, details, properties);

        ScrapeJobResult result = service.updateKnownGuilds();

        assertThat(result).isEqualTo(ScrapeJobResult.empty());
        verify(targets, never()).worldNames(org.mockito.ArgumentMatchers.anyInt());
        verify(targets, never()).guildNamesForDetailsRefresh(org.mockito.ArgumentMatchers.anyInt());
    }
}
