package com.nathan.tibiastats.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScrapeService {
    private static final Logger log = LoggerFactory.getLogger(ScrapeService.class);

    private final WorldScrapeClient scrapeClient;
    private final WorldScrapePersistenceService persistence;

    public ScrapeService(WorldScrapeClient scrapeClient,
                         WorldScrapePersistenceService persistence) {
        this.scrapeClient = scrapeClient;
        this.persistence = persistence;
    }

    public ScrapeJobResult updateAllWorlds() {
        List<WorldScrapeSnapshot.Target> worlds = scrapeClient.fetchWorldsOverview();

        if (worlds.isEmpty()) {
            log.warn("Worlds overview returned no worlds. No scrape records will be created.");
            return ScrapeJobResult.empty();
        }

        log.info("Starting world scrape for {} worlds", worlds.size());

        int processed = 0;
        int updated = 0;
        int failed = 0;

        for (WorldScrapeSnapshot.Target world : worlds) {
            processed++;
            try {
                WorldScrapeSnapshot.Page page = scrapeClient.fetchWorldPage(world);
                persistence.saveWorldScrape(page);
                updated++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to scrape world {}. Continuing with next world.", world.name(), e);
            }
        }

        log.info("Finished world scrape cycle: processed={}, updated={}, failed={}", processed, updated, failed);
        return ScrapeJobResult.of(processed, 0, updated, failed);
    }
}
