package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.GuildScrapeProperties;
import com.nathan.tibiastats.domain.model.Guild;
import com.nathan.tibiastats.domain.model.HighscoreStatRow;
import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.GuildCatalogRepositoryPort;
import com.nathan.tibiastats.domain.port.GuildScrapePort;
import com.nathan.tibiastats.domain.port.HighscoreStatRecordRepositoryPort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationServiceRemainingTailCoverageTest {
    @Test
    void analyticsCurrentOnlineTotalSumsOnlyPresentLatestScrapes() {
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        AnalyticsService service = new AnalyticsService(worlds);
        World antica = new World("Antica", null, null);
        World secura = new World("Secura", null, null);
        World wintera = new World("Wintera", null, null);

        when(worlds.findAll()).thenReturn(List.of(antica, secura, wintera));
        when(worlds.findLatestByWorld(antica)).thenReturn(Optional.of(new Scrape(antica, Instant.parse("2026-06-05T10:00:00Z"), 100, null)));
        when(worlds.findLatestByWorld(secura)).thenReturn(Optional.empty());
        when(worlds.findLatestByWorld(wintera)).thenReturn(Optional.of(new Scrape(wintera, Instant.parse("2026-06-05T10:00:00Z"), 75, null)));

        assertThat(service.getCurrentOnlineTotal()).isEqualTo(175);
    }

    @Test
    void guildScrapeServiceRestoresInterruptFlagWhenPageDelayIsInterrupted() {
        GuildScrapeTargetPlanner targets = mock(GuildScrapeTargetPlanner.class);
        GuildCatalogService catalog = mock(GuildCatalogService.class);
        GuildDetailScrapeService details = mock(GuildDetailScrapeService.class);
        GuildScrapeProperties properties = new GuildScrapeProperties();
        properties.setListEnabled(true);
        properties.setDetailsEnabled(false);
        properties.setWorldLimit(1);
        properties.setPageDelayMs(10);
        GuildScrapeService service = new GuildScrapeService(targets, catalog, details, properties);

        when(targets.worldNames(1)).thenReturn(List.of("Antica"));
        when(catalog.updateGuildListForWorld("Antica")).thenReturn(new GuildScrapeService.GuildListResult(1, 0, 1));

        Thread.currentThread().interrupt();
        try {
            ScrapeJobResult result = service.updateKnownGuilds();

            assertThat(result).isEqualTo(ScrapeJobResult.of(1, 0, 1, 0));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void guildCatalogUpsertUsesExistingWorldAndClearsInvalidStoredLogoWhenNewLogoIsNull() {
        GuildScrapePort scraper = mock(GuildScrapePort.class);
        GuildCatalogRepositoryPort guilds = mock(GuildCatalogRepositoryPort.class);
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-05T12:00:00Z"), ZoneOffset.UTC);
        GuildCatalogService service = new GuildCatalogService(scraper, guilds, worlds, clock);
        World existingWorld = new World("Wintera", null, null);
        Guild existing = new Guild();
        existing.setId(99L);
        existing.setWorld(existingWorld);
        existing.setDescription("Keep description");
        existing.setHomepage("https://keep.test");
        existing.setLogoUrl("https://static.tibia.com/images/global/strings/headline.gif");

        when(guilds.findGuild("Raw Raw")).thenReturn(Optional.of(existing));
        when(worlds.findByName("Wintera")).thenReturn(Optional.of(existingWorld));
        when(guilds.saveGuild(any(Guild.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GuildCatalogService.GuildUpdate update = service.upsertGuild(
                "Raw Raw",
                null,
                " ",
                " ",
                null,
                null,
                false,
                clock.instant()
        );

        assertThat(update.created()).isFalse();
        assertThat(update.guild()).isSameAs(existing);
        assertThat(existing.getWorld()).isSameAs(existingWorld);
        assertThat(existing.getDescription()).isEqualTo("Keep description");
        assertThat(existing.getHomepage()).isEqualTo("https://keep.test");
        assertThat(existing.getLogoUrl()).isNull();
        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getLastSeenAt()).isEqualTo(clock.instant());
    }

    @Test
    void guildCatalogCountsExistingGuildsAsUpdatedDuringListRefresh() {
        GuildScrapePort scraper = mock(GuildScrapePort.class);
        GuildCatalogRepositoryPort guilds = mock(GuildCatalogRepositoryPort.class);
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-05T12:00:00Z"), ZoneOffset.UTC);
        GuildCatalogService service = new GuildCatalogService(scraper, guilds, worlds, clock);
        World antica = new World("Antica", null, null);
        Guild existing = new Guild();
        existing.setId(10L);

        when(worlds.findByName("Antica")).thenReturn(Optional.of(antica));
        when(scraper.fetchGuildList("Antica")).thenReturn(List.of(
                new GuildScrapePort.GuildListItem("Raw Raw", "Antica", true, "Existing guild")
        ));
        when(guilds.findGuild("Raw Raw")).thenReturn(Optional.of(existing));
        when(guilds.saveGuild(any(Guild.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GuildScrapeService.GuildListResult result = service.updateGuildListForWorld("Antica");

        assertThat(result).isEqualTo(new GuildScrapeService.GuildListResult(1, 0, 1));
        assertThat(existing.getWorld()).isSameAs(antica);
    }

    @Test
    void highscoreStatStorageDelegatesBatchUpsertToRepository() {
        HighscoreStatRecordRepositoryPort repository = mock(HighscoreStatRecordRepositoryPort.class);
        HighscoreStatStorageService service = new HighscoreStatStorageService(repository);
        List<HighscoreStatRow> rows = List.of(new HighscoreStatRow(
                1L,
                2,
                StatCategory.EXPERIENCE,
                0,
                LocalDate.parse("2026-06-05"),
                12345L,
                7,
                Instant.parse("2026-06-05T12:00:00Z")
        ));
        when(repository.upsertBatch(rows)).thenReturn(1);

        assertThat(service.upsertBatch(rows)).isEqualTo(1);
    }
}
