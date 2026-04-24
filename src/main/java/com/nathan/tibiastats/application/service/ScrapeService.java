package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.ScrapePlayer;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import com.nathan.tibiastats.domain.port.ScrapePort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScrapeService {
    private final ScrapePort scrapePort;
    private final WorldRepositoryPort worldRepo;
    private final CharacterRepositoryPort characterRepo;
    private final CharacterNamingService namingService;

    public ScrapeService(ScrapePort scrapePort,
                         WorldRepositoryPort worldRepo,
                         CharacterRepositoryPort characterRepo,
                         CharacterNamingService namingService) {
        this.scrapePort = scrapePort;
        this.worldRepo = worldRepo;
        this.characterRepo = characterRepo;
        this.namingService = namingService;
    }

    @Transactional
    public void updateAllWorlds(){
        for (var ws : scrapePort.fetchWorldsOverview()) {
            World world = worldRepo.findByName(ws.name())
                    .orElseGet(() -> worldRepo.save(new WorldBuilder()
                            .name(ws.name())
                            .pvpType(ws.pvptype())
                            .location(ws.location())
                            .build()));

            ScrapePort.WorldOnline online = scrapePort.fetchWorldPage(world.getName(), world);

            world.setOnlineRecord(online.onlineRecord());
            world.setCreationDate(online.creationDate());
            world.setTransferType(online.transferType());
            world.setGameWorldType(online.gameWorldType());
            worldRepo.save(world);

            Scrape scrape = new Scrape();
            scrape.setWorld(world);
            scrape.setScrapeTime(Instant.now());
            scrape.setPlayersOnline(online.playersOnline());
            scrape = worldRepo.saveScrape(scrape);

            List<ScrapePlayer> playerList = new ArrayList<>();

            for (String playerName : online.playerNames()) {
                String formerNames = scrapePort.getFormerName(playerName);
                var character = namingService.ensureCharacterForName(playerName, formerNames);

                if (character.getCreationDate() == null) {
                    //TODO: implementar fetchCharacterDetails() para pegar os dados de character no site
                }
                characterRepo.findCharacterActiveName(character.getId()).ifPresent(name -> {
                    if(!name.getName().equals(playerName)) {
                        namingService.handleRenamed(character, playerName, name);
                    }
                });


                ScrapePlayer sp = new ScrapePlayer();
                sp.setScrape(scrape);
                sp.setCharacter(character);
                playerList.add(sp);
                worldRepo.saveScrapePlayer(sp);
            }
            scrape.setPlayers(playerList);
            worldRepo.saveScrape(scrape);
        }
    }

    private static class WorldBuilder {
        private String name, pvpType, location;
        WorldBuilder name(String v){ this.name=v; return this; }
        WorldBuilder pvpType(String v){ this.pvpType=v; return this; }
        WorldBuilder location(String v){ this.location=v; return this; }
        World build(){
            World w = new World();
            w.setName(name);
            w.setPvpType(pvpType);
            w.setLocation(location);
            return w;
        }
    }
}