package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.ScrapePlayer;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Service
public class WorldScrapePersistenceService {
    private static final Logger log = LoggerFactory.getLogger(WorldScrapePersistenceService.class);

    private final WorldRepositoryPort worldRepo;
    private final OnlineCharacterSnapshotService onlineCharacters;
    private final TransactionTemplate transactionTemplate;

    public WorldScrapePersistenceService(WorldRepositoryPort worldRepo,
                                         OnlineCharacterSnapshotService onlineCharacters,
                                         TransactionTemplate transactionTemplate) {
        this.worldRepo = worldRepo;
        this.onlineCharacters = onlineCharacters;
        this.transactionTemplate = transactionTemplate;
    }

    public void saveWorldScrape(WorldScrapeSnapshot.Page page) {
        transactionTemplate.executeWithoutResult(status -> persistWorldScrape(page));
    }

    private void persistWorldScrape(WorldScrapeSnapshot.Page page) {
        WorldScrapeSnapshot.Target target = page.target();
        World world = worldRepo.findByName(target.name())
                .orElseGet(() -> worldRepo.save(worldFromTarget(target)));

        world.setPvpType(firstNonBlank(target.pvpType(), world.getPvpType()));
        world.setLocation(firstNonBlank(target.location(), world.getLocation()));
        world.setOnlineRecord(firstNonBlank(page.onlineRecord(), world.getOnlineRecord()));
        world.setCreationDate(page.creationDate() != null ? page.creationDate() : world.getCreationDate());
        world.setTransferType(firstNonBlank(page.transferType(), target.transferType(), world.getTransferType()));
        world.setGameWorldType(firstNonBlank(page.gameWorldType(), target.gameWorldType(), world.getGameWorldType()));
        worldRepo.save(world);

        Scrape scrape = new Scrape();
        scrape.setWorld(world);
        scrape.setScrapeTime(Instant.now());
        scrape.setPlayersOnline(page.playersOnline());

        int addedPlayers = 0;
        for (WorldScrapeSnapshot.OnlineCharacter player : page.players()) {
            if (player == null || player.name() == null || player.name().isBlank()) {
                continue;
            }

            CharacterEntity character = onlineCharacters.resolveAndUpdate(player);

            ScrapePlayer scrapePlayer = new ScrapePlayer();
            scrapePlayer.setCharacter(character);
            scrape.addPlayer(scrapePlayer);
            addedPlayers++;
        }

        worldRepo.saveScrape(scrape);
        log.info("Saved scrape for world {}: playersOnline={}, listedPlayers={}",
                world.getName(), scrape.getPlayersOnline(), addedPlayers);
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
