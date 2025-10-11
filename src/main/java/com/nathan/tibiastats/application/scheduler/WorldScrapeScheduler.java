package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.config.AppProperties;
import com.nathan.tibiastats.application.service.ScrapeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorldScrapeScheduler {
    private final ScrapeService scrapeService; private final AppProperties props;
    public WorldScrapeScheduler(ScrapeService s, AppProperties p){this.scrapeService=s; this.props=p;}

    @Scheduled(fixedRateString = "${tibiastats.scrape.worlds.rate-ms:60000}")
    public void run(){ scrapeService.updateAllWorlds(); }
}