package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.ScrapeJobResult;
import com.nathan.tibiastats.application.service.ScrapeJobService;
import com.nathan.tibiastats.application.service.ScrapeService;
import com.nathan.tibiastats.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "tibiastats.scrape.worlds", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorldScrapeScheduler {
    private final ScrapeService scrapeService;
    private final AppProperties props;
    private final ScrapeJobService scrapeJobService;

    public WorldScrapeScheduler(ScrapeService scrapeService,
                                AppProperties props,
                                ScrapeJobService scrapeJobService) {
        this.scrapeService = scrapeService;
        this.props = props;
        this.scrapeJobService = scrapeJobService;
    }

    @Scheduled(fixedRateString = "${tibiastats.scrape.worlds.rate-ms:60000}")
    public void run() {
        Long jobId = scrapeJobService.start(ScrapeJobService.WORLD_SCRAPER);
        try {
            ScrapeJobResult result = scrapeService.updateAllWorlds();
            scrapeJobService.finishSuccess(jobId, result);
        } catch (Exception e) {
            scrapeJobService.finishFailure(jobId, ScrapeJobResult.empty(), e);
            throw e;
        }
    }
}
