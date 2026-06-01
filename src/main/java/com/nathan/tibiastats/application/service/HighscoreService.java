package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreHttpBackoffState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class HighscoreService {
    private static final Logger log = LoggerFactory.getLogger(HighscoreService.class);

    private final HighscoreScrapeProperties properties;
    private final HighscoreHttpBackoffCoordinator httpBackoffCoordinator;
    private final HighscoreRunCoordinator runCoordinator;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public HighscoreService(
            HighscoreScrapeProperties properties,
            HighscoreHttpBackoffCoordinator httpBackoffCoordinator,
            HighscoreRunCoordinator runCoordinator
    ) {
        this.properties = properties;
        this.httpBackoffCoordinator = httpBackoffCoordinator;
        this.runCoordinator = runCoordinator;
    }

    public ScrapeJobResult updateAllHighscores() {
        return updateHighscores("default", properties.toLegacyPlan());
    }

    public ScrapeJobResult updateHighscores(String planName, HighscoreScrapeProperties.Plan plan) {
        if (!properties.isEnabled()) {
            log.info("[HIGHSCORE_SCRAPER] Skipping run because highscores.enabled=false: plan={}", planName);
            return ScrapeJobResult.empty();
        }
        if (plan == null || !plan.isEnabled()) {
            log.info("[HIGHSCORE_SCRAPER] Skipping disabled highscore plan: plan={}", planName);
            return ScrapeJobResult.empty();
        }

        if (httpBackoffCoordinator.isActive(planName)) {
            return ScrapeJobResult.empty();
        }

        if (!running.compareAndSet(false, true)) {
            log.warn("[HIGHSCORE_SCRAPER] Previous highscore run is still active. Skipping this tick: plan={}", planName);
            return ScrapeJobResult.empty();
        }

        Instant startedAt = Instant.now();
        try {
            return runCoordinator.run(startedAt, planName, plan);
        } finally {
            running.set(false);
        }
    }

    public HighscoreHttpBackoffState getHttpBackoffState() {
        return httpBackoffCoordinator.getState();
    }

    public HighscoreHttpBackoffState resetHttpBackoffManually() {
        return httpBackoffCoordinator.resetManually();
    }

    public boolean isRunning() {
        return running.get();
    }
}
