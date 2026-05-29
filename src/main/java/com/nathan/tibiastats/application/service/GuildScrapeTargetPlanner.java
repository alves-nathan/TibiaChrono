package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.Guild;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.GuildCatalogRepositoryPort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GuildScrapeTargetPlanner {
    private final WorldRepositoryPort worlds;
    private final GuildCatalogRepositoryPort guilds;

    public GuildScrapeTargetPlanner(WorldRepositoryPort worlds, GuildCatalogRepositoryPort guilds) {
        this.worlds = worlds;
        this.guilds = guilds;
    }

    public List<String> worldNames(int worldLimit) {
        List<World> allWorlds = worlds.findAll();
        if (worldLimit > 0 && allWorlds.size() > worldLimit) {
            allWorlds = allWorlds.subList(0, worldLimit);
        }

        return allWorlds.stream()
                .map(World::getName)
                .filter(name -> !isBlank(name))
                .toList();
    }

    public List<String> guildNamesForDetailsRefresh(int guildLimit) {
        return guilds.findActiveForDetailsRefresh(guildLimit).stream()
                .map(Guild::getName)
                .filter(name -> !isBlank(name))
                .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
