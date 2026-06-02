package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.Guild;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.GuildCatalogRepositoryPort;
import com.nathan.tibiastats.domain.port.GuildScrapePort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuildCatalogServiceTest {
    @Test
    void updateGuildListForWorldSkipsBlankNamesAndPersistsNormalizedGuilds() {
        GuildScrapePort scraper = mock(GuildScrapePort.class);
        GuildCatalogRepositoryPort guilds = mock(GuildCatalogRepositoryPort.class);
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC);
        GuildCatalogService service = new GuildCatalogService(scraper, guilds, worlds, clock);
        World antica = world("Antica");
        when(worlds.findByName("Antica")).thenReturn(Optional.of(antica));
        when(scraper.fetchGuildList("Antica")).thenReturn(List.of(
                new GuildScrapePort.GuildListItem(" ", "Antica", true, "ignored"),
                new GuildScrapePort.GuildListItem(" Raw   Raw ", "Antica", true, " Brazilian guild "),
                new GuildScrapePort.GuildListItem("Other Guild", "Antica", false, null)
        ));
        when(guilds.findGuild(" Raw   Raw ")).thenReturn(Optional.empty());
        when(guilds.findGuild("Other Guild")).thenReturn(Optional.empty());
        when(guilds.saveGuild(any(Guild.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GuildScrapeService.GuildListResult result = service.updateGuildListForWorld("Antica");

        assertThat(result).isEqualTo(new GuildScrapeService.GuildListResult(2, 2, 0));
        ArgumentCaptor<Guild> guildCaptor = ArgumentCaptor.forClass(Guild.class);
        verify(guilds, org.mockito.Mockito.atLeast(2)).saveGuild(guildCaptor.capture());
        assertThat(guildCaptor.getAllValues())
                .extracting(Guild::getName)
                .contains("Raw Raw", "Other Guild");
        Guild rawRaw = guildCaptor.getAllValues().stream()
                .filter(guild -> "Raw Raw".equals(guild.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(rawRaw.getNormalizedName()).isEqualTo("raw raw");
        assertThat(rawRaw.getWorld()).isSameAs(antica);
        assertThat(rawRaw.getDescription()).isEqualTo("Brazilian guild");
        assertThat(rawRaw.isActive()).isTrue();
        assertThat(rawRaw.getLastSeenAt()).isEqualTo(clock.instant());
    }

    @Test
    void upsertGuildUpdatesExistingGuildAndTrimsOptionalFields() {
        GuildScrapePort scraper = mock(GuildScrapePort.class);
        GuildCatalogRepositoryPort guilds = mock(GuildCatalogRepositoryPort.class);
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC);
        GuildCatalogService service = new GuildCatalogService(scraper, guilds, worlds, clock);
        World world = world("Wintera");
        Guild existing = new Guild();
        existing.setId(5L);
        existing.setLogoUrl("https://static.tibia.com/images/global/header/headline-guilds.gif");
        when(guilds.findGuild(" Raw   Raw ")).thenReturn(Optional.of(existing));
        when(worlds.findByName("Wintera")).thenReturn(Optional.of(world));
        when(guilds.saveGuild(any(Guild.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDate foundedAt = LocalDate.parse("2024-01-01");

        GuildCatalogService.GuildUpdate update = service.upsertGuild(
                " Raw   Raw ",
                " Wintera ",
                " Brazilian neutral guild ",
                " https://guild.example ",
                " ",
                foundedAt,
                true,
                clock.instant()
        );

        assertThat(update.created()).isFalse();
        assertThat(update.guild()).isSameAs(existing);
        assertThat(existing.getName()).isEqualTo("Raw Raw");
        assertThat(existing.getNormalizedName()).isEqualTo("raw raw");
        assertThat(existing.getWorld()).isSameAs(world);
        assertThat(existing.getDescription()).isEqualTo("Brazilian neutral guild");
        assertThat(existing.getHomepage()).isEqualTo("https://guild.example");
        assertThat(existing.getLogoUrl()).isNull();
        assertThat(existing.getFoundedAt()).isEqualTo(foundedAt);
        assertThat(existing.getLastSeenAt()).isEqualTo(clock.instant());
    }

    @Test
    void ensureWorldCreatesUnknownWorldWhenNameIsBlank() {
        GuildCatalogRepositoryPort guilds = mock(GuildCatalogRepositoryPort.class);
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        GuildCatalogService service = new GuildCatalogService(
                mock(GuildScrapePort.class),
                guilds,
                worlds,
                Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC)
        );
        when(worlds.findByName("Unknown")).thenReturn(Optional.empty());
        when(worlds.save(any(World.class))).thenAnswer(invocation -> invocation.getArgument(0));

        World world = service.ensureWorld(" ");

        assertThat(world.getName()).isEqualTo("Unknown");
        verify(worlds).save(world);
    }

    private World world(String name) {
        World world = new World(name, null, null);
        world.setId(1);
        return world;
    }
}
