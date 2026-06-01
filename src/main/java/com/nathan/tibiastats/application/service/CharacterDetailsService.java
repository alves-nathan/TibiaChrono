package com.nathan.tibiastats.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CharacterDetailsService {
    private static final Logger log = LoggerFactory.getLogger(CharacterDetailsService.class);
    private static final String LOG_PREFIX = "[CHARACTER_DETAILS_SCRAPER]";

    private final CharacterDetailsBatchSelector batchSelector;
    private final CharacterDetailsBatchProcessor batchProcessor;

    public CharacterDetailsService(CharacterDetailsBatchSelector batchSelector,
                                   CharacterDetailsBatchProcessor batchProcessor) {
        this.batchSelector = batchSelector;
        this.batchProcessor = batchProcessor;
    }

    /**
     * Kept with the old name for compatibility with the existing scheduler.
     * The selection, remote fetch and persistence concerns live in focused collaborators.
     */
    public ScrapeJobResult updateMissingDetailsBatch() {
        CharacterDetailsBatchSelector.Selection selection = batchSelector.select();

        if (selection.names().isEmpty()) {
            log.info("{} No active characters available for detail scrape", LOG_PREFIX);
            return ScrapeJobResult.empty();
        }

        log.info(
                "{} Starting character detail scrape batch: selectedNames={}, batchSize={}",
                LOG_PREFIX,
                selection.names().size(),
                selection.batchSize()
        );

        return batchProcessor.process(selection.names());
    }
}
