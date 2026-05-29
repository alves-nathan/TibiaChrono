package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.GuildScrapeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GuildScrapeService {
    private static final Logger log = LoggerFactory.getLogger(GuildScrapeService.class);
    private static final String LOG_PREFIX = "[GUILD_SCRAPER]";

    private final GuildScrapeTargetPlanner targets;
    private final GuildCatalogService catalog;
    private final GuildDetailScrapeService details;
    private final GuildScrapeProperties properties;

    @Autowired
    public GuildScrapeService(GuildScrapeTargetPlanner targets,
                              GuildCatalogService catalog,
                              GuildDetailScrapeService details,
                              GuildScrapeProperties properties) {
        this.targets = targets;
        this.catalog = catalog;
        this.details = details;
        this.properties = properties;
    }

    public ScrapeJobResult updateKnownGuilds() {
        int processed = 0;
        int created = 0;
        int updated = 0;
        int failed = 0;

        if (properties.isListEnabled()) {
            for (String worldName : targets.worldNames(properties.getWorldLimit())) {
                try {
                    GuildListResult result = updateGuildListForWorld(worldName);
                    processed += result.processed();
                    created += result.created();
                    updated += result.updated();
                    sleepBetweenPages();
                } catch (Exception e) {
                    failed++;
                    log.warn("{} Failed to update guild list for world={}: {}", LOG_PREFIX, worldName, e.getMessage());
                }
            }
        }

        if (properties.isDetailsEnabled()) {
            for (String guildName : targets.guildNamesForDetailsRefresh(properties.getGuildLimit())) {
                try {
                    GuildDetailResult result = updateGuildDetail(guildName);
                    processed += result.membersSeen();
                    created += result.membershipsOpened();
                    updated += result.membershipsUpdated() + result.membershipsClosed();
                    sleepBetweenPages();
                } catch (Exception e) {
                    failed++;
                    log.warn("{} Failed to update guild detail for guild={}: {}", LOG_PREFIX, guildName, e.getMessage());
                }
            }
        }

        return ScrapeJobResult.of(processed, created, updated, failed);
    }

    public GuildListResult updateGuildListForWorld(String worldName) {
        return catalog.updateGuildListForWorld(worldName);
    }

    public GuildDetailResult updateGuildDetail(String guildName) {
        return details.updateGuildDetail(guildName);
    }

    private void sleepBetweenPages() {
        int delayMs = properties.getPageDelayMs();
        if (delayMs <= 0) return;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record GuildListResult(int processed, int created, int updated) {}

    public record GuildDetailResult(String guildName,
                                    int membersSeen,
                                    int membershipsOpened,
                                    int membershipsUpdated,
                                    int membershipsClosed,
                                    int transfers) {}
}
