package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.ScrapePlayer;
import com.nathan.tibiastats.domain.model.Vocation;
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
import java.util.Locale;
import java.util.Objects;

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

    public ScrapeJobResult updateAllWorlds() {
        List<ScrapePort.WorldSummary> worlds = scrapePort.fetchWorldsOverview();

        if (worlds.isEmpty()) {
            log.warn("Worlds overview returned no worlds. No scrape records will be created.");
            return ScrapeJobResult.empty();
        }

        log.info("Starting world scrape for {} worlds", worlds.size());

        int processed = 0;
        int updated = 0;
        int failed = 0;

        for (ScrapePort.WorldSummary ws : worlds) {
            processed++;
            try {
                World pageWorld = new WorldBuilder()
                        .name(ws.name())
                        .pvpType(ws.pvptype())
                        .location(ws.location())
                        .transferType(ws.transferType())
                        .gameWorldType(ws.gameWorldType())
                        .build();

                ScrapePort.WorldOnline online = scrapePort.fetchWorldPage(ws.name(), pageWorld);
                saveWorldScrape(ws, online);
                updated++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to scrape world {}. Continuing with next world.", ws.name(), e);
            }
        }

        log.info("Finished world scrape cycle: processed={}, updated={}, failed={}", processed, updated, failed);
        return ScrapeJobResult.of(processed, 0, updated, failed);
    }

    private void saveWorldScrape(ScrapePort.WorldSummary ws, ScrapePort.WorldOnline online) {
        transactionTemplate.executeWithoutResult(status -> {
            World world = worldRepo.findByName(ws.name())
                    .orElseGet(() -> worldRepo.save(new WorldBuilder()
                            .name(ws.name())
                            .pvpType(ws.pvptype())
                            .location(ws.location())
                            .transferType(ws.transferType())
                            .gameWorldType(ws.gameWorldType())
                            .build()));

            world.setPvpType(firstNonBlank(ws.pvptype(), world.getPvpType()));
            world.setLocation(firstNonBlank(ws.location(), world.getLocation()));
            world.setOnlineRecord(firstNonBlank(online.onlineRecord(), world.getOnlineRecord()));
            world.setCreationDate(online.creationDate() != null ? online.creationDate() : world.getCreationDate());
            world.setTransferType(firstNonBlank(online.transferType(), ws.transferType(), world.getTransferType()));
            world.setGameWorldType(firstNonBlank(online.gameWorldType(), ws.gameWorldType(), world.getGameWorldType()));
            worldRepo.save(world);

            Scrape scrape = new Scrape();
            scrape.setWorld(world);
            scrape.setScrapeTime(Instant.now());
            scrape.setPlayersOnline(online.playersOnline());

            int addedPlayers = 0;
            for (ScrapePort.OnlineCharacterSnapshot player : online.players()) {
                if (player == null || player.name() == null || player.name().isBlank()) {
                    continue;
                }

                String normalizedPlayerName = player.name().trim();

                // Avoid one HTTP request per online character during the scheduled world scrape.
                // Rename reconciliation can be done later by a dedicated character-detail job.
                var character = namingService.ensureCharacterForName(normalizedPlayerName, normalizedPlayerName);

                characterRepo.findCharacterActiveName(character.getId()).ifPresent(name -> {
                    if (!name.getName().equals(normalizedPlayerName)) {
                        namingService.handleRenamed(character, normalizedPlayerName, name);
                    }
                });

                boolean characterChanged = false;
                if (player.level() != null && !Objects.equals(character.getLevel(), player.level())) {
                    character.setLevel(player.level());
                    characterChanged = true;
                }

                if (player.vocation() != null && !player.vocation().isBlank()) {
                    var vocation = characterRepo.findVocationByNameOrPromotionName(player.vocation().trim());
                    if (vocation.isPresent() && !sameVocation(character.getVocation(), vocation.get())) {
                        character.setVocation(vocation.get());
                        characterChanged = true;
                    }
                }

                if (characterChanged) {
                    characterRepo.save(character);
                }

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

    private boolean sameVocation(Vocation current, Vocation scraped) {
        return current != null && scraped != null && Objects.equals(current.getId(), scraped.getId());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static class WorldBuilder {
        private String name, pvpType, location, transferType, gameWorldType;

        WorldBuilder name(String v) { this.name = v; return this; }
        WorldBuilder pvpType(String v) { this.pvpType = v; return this; }
        WorldBuilder location(String v) { this.location = v; return this; }
        WorldBuilder transferType(String v) { this.transferType = v; return this; }
        WorldBuilder gameWorldType(String v) { this.gameWorldType = v; return this; }

        World build() {
            World w = new World();
            w.setName(name);
            w.setPvpType(pvpType);
            w.setLocation(location);
            w.setTransferType(transferType);
            w.setGameWorldType(gameWorldType);
            return w;
        }
    }
}
