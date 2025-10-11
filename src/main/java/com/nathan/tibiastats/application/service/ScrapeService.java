package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.ScrapeRecord;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.ScrapePort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.nathan.tibiastats.util.JsonConverter;
import java.time.Instant;

@Service
public class ScrapeService {
    private final ScrapePort scrapePort; private final WorldRepositoryPort worldRepo;
    public ScrapeService(ScrapePort p, WorldRepositoryPort w){this.scrapePort=p; this.worldRepo=w;}

    @Transactional
    public void updateAllWorlds(){
        // Fetch overview (ensures worlds exist / get pvptype/location)
        for (var ws : scrapePort.fetchWorldsOverview()){
            var world = worldRepo.findByName(ws.name()).orElseGet(() -> worldRepo.save(new World(ws.name(), ws.pvptype(), ws.location())));
            // Update per-world online and player list
            var online = scrapePort.fetchWorldPage(world.getName());
            var record = new ScrapeRecord(world, Instant.now(), online.playersOnline(), JsonConverter.toJson(online.playerNames()));
            worldRepo.saveScrape(record);
        }
    }
}