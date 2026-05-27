package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.CharacterDetailsService;
import com.nathan.tibiastats.application.service.ScrapeJobResult;
import com.nathan.tibiastats.application.service.ScrapeJobService;
import com.nathan.tibiastats.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CharacterDetailsScrapeScheduler {
    private static final Logger log = LoggerFactory.getLogger(CharacterDetailsScrapeScheduler.class);
    private static final String LOG_PREFIX = "[CHARACTER_DETAILS_SCRAPER]";

    private final CharacterDetailsService characterDetailsService;
    private final AppProperties appProperties;
    private final ScrapeJobService scrapeJobService;

    public CharacterDetailsScrapeScheduler(CharacterDetailsService characterDetailsService,
                                           AppProperties appProperties,
                                           ScrapeJobService scrapeJobService) {
        this.characterDetailsService = characterDetailsService;
        this.appProperties = appProperties;
        this.scrapeJobService = scrapeJobService;
    }

    @Scheduled(
            fixedDelayString = "${tibiastats.scrape.character-details.rate-ms:300000}",
            initialDelayString = "${tibiastats.scrape.character-details.initial-delay-ms:15000}"
    )
    public void run() {
        if (!appProperties.getCharacterDetails().isEnabled()) {
            log.debug("{} Scheduler disabled", LOG_PREFIX);
            return;
        }

        Long jobId = scrapeJobService.start(ScrapeJobService.CHARACTER_DETAILS_SCRAPER);
        int batchSize = Math.max(1, appProperties.getCharacterDetails().getBatchSize());
        log.info("{} Scheduler tick started. batchSize={}", LOG_PREFIX, batchSize);
        try {
            ScrapeJobResult result = characterDetailsService.updateMissingDetailsBatch();
            scrapeJobService.finishSuccess(jobId, result);
            log.info("{} Scheduler tick finished", LOG_PREFIX);
        } catch (Exception e) {
            scrapeJobService.finishFailure(jobId, ScrapeJobResult.empty(), e);
            throw e;
        }
    }
}
