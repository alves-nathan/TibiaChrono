package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.ScrapePlayer;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import com.nathan.tibiastats.domain.port.ScrapePort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Service
public class ScrapeService {
    private static final Logger log = LoggerFactory.getLogger(ScrapeService.class);

    private final ScrapePort scrapePort;
    private final WorldRepositoryPort worldRepo;
    private final CharacterRepositoryPort characterRepo;
    private final CharacterNamingService namingService;
    private final TransactionTemplate transactionTemplate;

    public ScrapeService(ScrapePort scrapePort,
                         WorldRepositoryPort worldRepo,
                         CharacterRepositoryPort characterRepo,
                         CharacterNamingService namingService,
                         TransactionTemplate transactionTemplate) {
        this.scrapePort = scrapePort;
        this.worldRepo = worldRepo;
        this.characterRepo = characterRepo;
        this.namingService = namingService;
        this.transactionTemplate = transactionTemplate;
    }

    public void updateAllWorlds() {
        List<ScrapePort.WorldSummary> worlds = scrapePort.fetchWorldsOverview();

        if (worlds.isEmpty()) {
            log.warn("Worlds overview returned no worlds. No scrape records will be created.");
            return;
        }

        log.info("Starting world scrape for {} worlds", worlds.size());

        for (ScrapePort.WorldSummary ws : worlds) {
            try {
                World pageWorld = new WorldBuilder()
                        .name(ws.name())
                        .pvpType(ws.pvptype())
                        .location(ws.location())
                        .build();

                ScrapePort.WorldOnline online = scrapePort.fetchWorldPage(ws.name(), pageWorld);
                saveWorldScrape(ws, online);
            } catch (Exception e) {
                log.error("Failed to scrape world {}. Continuing with next world.", ws.name(), e);
            }
        }

        log.info("Finished world scrape cycle");
    }

    private void saveWorldScrape(ScrapePort.WorldSummary ws, ScrapePort.WorldOnline online) {
        transactionTemplate.executeWithoutResult(status -> {
            World world = worldRepo.findByName(ws.name())
                    .orElseGet(() -> worldRepo.save(new WorldBuilder()
                            .name(ws.name())
                            .pvpType(ws.pvptype())
                            .location(ws.location())
                            .build()));

            world.setPvpType(ws.pvptype());
            world.setLocation(ws.location());
            world.setOnlineRecord(online.onlineRecord());
            world.setCreationDate(online.creationDate());
            world.setTransferType(online.transferType());
            world.setGameWorldType(online.gameWorldType());
            worldRepo.save(world);

            Scrape scrape = new Scrape();
            scrape.setWorld(world);
            scrape.setScrapeTime(Instant.now());
            scrape.setPlayersOnline(online.playersOnline());

            int addedPlayers = 0;
            for (String playerName : online.playerNames()) {
                if (playerName == null || playerName.isBlank()) {
                    continue;
                }

                String normalizedPlayerName = playerName.trim();

                // Avoid one HTTP request per online character during the scheduled world scrape.
                // Rename reconciliation can be done later by a dedicated character-detail job.
                var character = namingService.ensureCharacterForName(normalizedPlayerName, normalizedPlayerName);

                characterRepo.findCharacterActiveName(character.getId()).ifPresent(name -> {
                    if (!name.getName().equals(normalizedPlayerName)) {
                        namingService.handleRenamed(character, normalizedPlayerName, name);
                    }
                });

                ScrapePlayer sp = new ScrapePlayer();
                sp.setCharacter(character);
                scrape.addPlayer(sp);
                addedPlayers++;
            }

            worldRepo.saveScrape(scrape);
            log.info("Saved scrape for world {}: playersOnline={}, listedPlayers={}",
                    world.getName(), scrape.getPlayersOnline(), addedPlayers);
        });
    }

    private static class WorldBuilder {
        private String name, pvpType, location;
        WorldBuilder name(String v) { this.name = v; return this; }
        WorldBuilder pvpType(String v) { this.pvpType = v; return this; }
        WorldBuilder location(String v) { this.location = v; return this; }
        World build() {
            World w = new World();
            w.setName(name);
            w.setPvpType(pvpType);
            w.setLocation(location);
            return w;
        }
    }
}
