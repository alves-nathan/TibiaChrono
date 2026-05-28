package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.GuildScrapeService;
import com.nathan.tibiastats.application.service.ScrapeJobResult;
import com.nathan.tibiastats.application.service.ScrapeJobService;
import com.nathan.tibiastats.config.GuildScrapeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "tibiastats.scrape.guilds", name = "enabled", havingValue = "true", matchIfMissing = false)
public class GuildScrapeScheduler {
    private static final Logger log = LoggerFactory.getLogger(GuildScrapeScheduler.class);
    private static final String LOG_PREFIX = "[GUILD_SCRAPER]";

    private final GuildScrapeService guildScrapeService;
    private final GuildScrapeProperties properties;
    private final ScrapeJobService scrapeJobService;

    public GuildScrapeScheduler(GuildScrapeService guildScrapeService,
                                GuildScrapeProperties properties,
                                ScrapeJobService scrapeJobService) {
        this.guildScrapeService = guildScrapeService;
        this.properties = properties;
        this.scrapeJobService = scrapeJobService;
    }

    @Scheduled(
            fixedDelayString = "${tibiastats.scrape.guilds.rate-ms:3600000}",
            initialDelayString = "${tibiastats.scrape.guilds.initial-delay-ms:30000}"
    )
    public void run() {
        if (!properties.isEnabled()) {
            log.debug("{} Scheduler disabled", LOG_PREFIX);
            return;
        }

        Long jobId = scrapeJobService.start(ScrapeJobService.GUILD_SCRAPER);
        log.info("{} Scheduler tick started. listEnabled={}, detailsEnabled={}, worldLimit={}, guildLimit={}",
                LOG_PREFIX, properties.isListEnabled(), properties.isDetailsEnabled(), properties.getWorldLimit(), properties.getGuildLimit());
        try {
            ScrapeJobResult result = guildScrapeService.updateKnownGuilds();
            scrapeJobService.finishSuccess(jobId, result);
            log.info("{} Scheduler tick finished: {}", LOG_PREFIX, result);
        } catch (Exception e) {
            scrapeJobService.finishFailure(jobId, ScrapeJobResult.empty(), e);
            throw e;
        }
    }
}
