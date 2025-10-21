package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.Character;
import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.ScrapePlayer;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.ScrapePort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScrapeService {
    private final ScrapePort scrapePort; private final WorldRepositoryPort worldRepo;
    public ScrapeService(ScrapePort p, WorldRepositoryPort w){this.scrapePort=p; this.worldRepo=w;}

    @Transactional
    public void updateAllWorlds(){
        List<ScrapePort.WorldSummary> summaries = scrapePort.fetchWorldsOverview();
        for (var ws : summaries) {
            World world = worldRepo.findByName(ws.name())
                    .orElseGet(() -> worldRepo.save(new World(ws.name(), ws.pvptype(), ws.location())));
            ScrapePort.WorldOnline online = scrapePort.fetchWorldPage(ws.name());
            Scrape scrape = new Scrape();
            scrape.setWorld(world);
            scrape.setScrapetime(Instant.now());
            scrape.setPlayersOnline(online.playersOnline());

            List<ScrapePlayer> spList = new ArrayList<>();
            for (String name : online.playerNames()) {
                Character character = new Character();
                ScrapePlayer sp = new ScrapePlayer();
                sp.setScrape(scrape);
                sp.setCharacter(character);
                spList.add(sp);
            }
            scrape.setPlayers(spList);
            worldRepo.saveScrape(scrape);
        }
    }
}