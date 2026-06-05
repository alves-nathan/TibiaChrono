package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.Guild;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.GuildCatalogRepositoryPort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuildScrapeTargetPlannerCoverageTest {

    @Test
    void worldNamesReturnsEveryNonBlankWorldWhenLimitIsDisabled() {
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        GuildCatalogRepositoryPort guilds = mock(GuildCatalogRepositoryPort.class);
        GuildScrapeTargetPlanner planner = new GuildScrapeTargetPlanner(worlds, guilds);
        when(worlds.findAll()).thenReturn(List.of(
                new World("Antica", null, "Europe"),
                new World(" ", null, "Europe"),
                new World(null, null, "Europe"),
                new World("Bona", null, "South America")
        ));

        assertThat(planner.worldNames(0)).containsExactly("Antica", "Bona");
    }

    @Test
    void worldNamesAppliesPositiveLimitBeforeFilteringBlankWorlds() {
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        GuildCatalogRepositoryPort guilds = mock(GuildCatalogRepositoryPort.class);
        GuildScrapeTargetPlanner planner = new GuildScrapeTargetPlanner(worlds, guilds);
        when(worlds.findAll()).thenReturn(List.of(
                new World("Antica", null, "Europe"),
                new World(" ", null, "Europe"),
                new World("Bona", null, "South America")
        ));

        assertThat(planner.worldNames(2)).containsExactly("Antica");
    }

    @Test
    void guildNamesForDetailsRefreshDelegatesLimitAndFiltersBlankNames() {
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        GuildCatalogRepositoryPort guilds = mock(GuildCatalogRepositoryPort.class);
        GuildScrapeTargetPlanner planner = new GuildScrapeTargetPlanner(worlds, guilds);
        when(guilds.findActiveForDetailsRefresh(3)).thenReturn(List.of(
                guild("Raw Raw"),
                guild(" "),
                guild(null),
                guild("Other Guild")
        ));

        assertThat(planner.guildNamesForDetailsRefresh(3)).containsExactly("Raw Raw", "Other Guild");
        verify(guilds).findActiveForDetailsRefresh(3);
    }

    private static Guild guild(String name) {
        Guild guild = new Guild();
        guild.setName(name);
        return guild;
    }
}
