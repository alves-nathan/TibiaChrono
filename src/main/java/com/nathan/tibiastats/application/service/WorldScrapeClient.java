package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.ScrapePort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorldScrapeClient {
    private final ScrapePort scrapePort;

    public WorldScrapeClient(ScrapePort scrapePort) {
        this.scrapePort = scrapePort;
    }

    public List<WorldScrapeSnapshot.Target> fetchWorldsOverview() {
        return scrapePort.fetchWorldsOverview().stream()
                .map(this::mapTarget)
                .toList();
    }

    public WorldScrapeSnapshot.Page fetchWorldPage(WorldScrapeSnapshot.Target target) {
        ScrapePort.WorldOnline online = scrapePort.fetchWorldPage(target.name(), worldFromTarget(target));
        return mapPage(target, online);
    }

    private WorldScrapeSnapshot.Target mapTarget(ScrapePort.WorldSummary summary) {
        return new WorldScrapeSnapshot.Target(
                summary.name(),
                summary.pvptype(),
                summary.location(),
                summary.playersOnline(),
                summary.transferType(),
                summary.gameWorldType()
        );
    }

    private WorldScrapeSnapshot.Page mapPage(WorldScrapeSnapshot.Target target, ScrapePort.WorldOnline online) {
        List<WorldScrapeSnapshot.OnlineCharacter> players = online.players() == null
                ? List.of()
                : online.players().stream()
                        .map(this::mapPlayer)
                        .toList();

        return new WorldScrapeSnapshot.Page(
                target,
                online.world(),
                online.playersOnline(),
                players,
                online.onlineRecord(),
                online.creationDate(),
                online.transferType(),
                online.gameWorldType()
        );
    }

    private WorldScrapeSnapshot.OnlineCharacter mapPlayer(ScrapePort.OnlineCharacterSnapshot player) {
        return new WorldScrapeSnapshot.OnlineCharacter(player.name(), player.level(), player.vocation());
    }

    private World worldFromTarget(WorldScrapeSnapshot.Target target) {
        World world = new World();
        world.setName(target.name());
        world.setPvpType(target.pvpType());
        world.setLocation(target.location());
        world.setTransferType(target.transferType());
        world.setGameWorldType(target.gameWorldType());
        return world;
    }
}
