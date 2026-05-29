package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Service
public class ManualScraperRunCoordinator {
    private static final String WORLDS_KEY = "worlds";
    private static final String CHARACTER_DETAILS_KEY = "character-details";
    private static final String GUILDS_KEY = "guilds";
    private static final String HIGHSCORE_KEY_PREFIX = "highscores:";

    private final ScrapeService scrapeService;
    private final CharacterDetailsService characterDetailsService;
    private final GuildScrapeService guildScrapeService;
    private final HighscoreService highscoreService;
    private final ScrapeJobService scrapeJobService;
    private final HighscoreScrapeProperties highscoreProperties;
    private final Map<String, AtomicBoolean> manualRuns = new ConcurrentHashMap<>();

    public ManualScraperRunCoordinator(ScrapeService scrapeService,
                                       CharacterDetailsService characterDetailsService,
                                       GuildScrapeService guildScrapeService,
                                       HighscoreService highscoreService,
                                       ScrapeJobService scrapeJobService,
                                       HighscoreScrapeProperties highscoreProperties) {
        this.scrapeService = scrapeService;
        this.characterDetailsService = characterDetailsService;
        this.guildScrapeService = guildScrapeService;
        this.highscoreService = highscoreService;
        this.scrapeJobService = scrapeJobService;
        this.highscoreProperties = highscoreProperties;
    }

    public AdminScraperService.ManualRunResponse triggerWorlds() {
        return triggerManualRun(
                WORLDS_KEY,
                ScrapeJobService.WORLD_SCRAPER,
                null,
                scrapeService::updateAllWorlds
        );
    }

    public AdminScraperService.ManualRunResponse triggerCharacterDetails() {
        return triggerManualRun(
                CHARACTER_DETAILS_KEY,
                ScrapeJobService.CHARACTER_DETAILS_SCRAPER,
                null,
                characterDetailsService::updateMissingDetailsBatch
        );
    }

    public AdminScraperService.ManualRunResponse triggerGuilds() {
        return triggerManualRun(
                GUILDS_KEY,
                ScrapeJobService.GUILD_SCRAPER,
                null,
                guildScrapeService::updateKnownGuilds
        );
    }

    public AdminScraperService.ManualRunResponse triggerHighscorePlan(String planName) {
        String normalizedPlanName = normalizePlanName(planName);
        HighscoreScrapeProperties.Plan plan = highscoreProperties.effectivePlans().get(normalizedPlanName);
        if (plan == null) {
            throw new IllegalArgumentException("Unknown highscore plan: " + normalizedPlanName);
        }

        return triggerManualRun(
                HIGHSCORE_KEY_PREFIX + normalizedPlanName,
                ScrapeJobService.HIGHSCORE_SCRAPER,
                normalizedPlanName,
                () -> highscoreService.updateHighscores(normalizedPlanName, plan)
        );
    }

    public boolean hasAnyHighscoreManualRunActive() {
        return manualRuns.entrySet().stream()
                .anyMatch(entry -> entry.getKey().startsWith(HIGHSCORE_KEY_PREFIX) && entry.getValue().get());
    }

    public boolean isManualRunActive(String runKey) {
        AtomicBoolean running = manualRuns.get(runKey);
        return running != null && running.get();
    }

    private AdminScraperService.ManualRunResponse triggerManualRun(String runKey,
                                                                  String jobName,
                                                                  String planName,
                                                                  Supplier<ScrapeJobResult> worker) {
        AtomicBoolean running = manualRuns.computeIfAbsent(runKey, ignored -> new AtomicBoolean(false));
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Manual scraper run already active: " + runKey);
        }

        Instant acceptedAt = Instant.now();
        Thread.startVirtualThread(() -> {
            Long jobId = null;
            try {
                jobId = scrapeJobService.start(jobName);
                ScrapeJobResult result = worker.get();
                scrapeJobService.finishSuccess(jobId, result == null ? ScrapeJobResult.empty() : result);
            } catch (Exception ex) {
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                }
                if (jobId != null) {
                    scrapeJobService.finishFailure(jobId, ScrapeJobResult.empty(), ex);
                }
            } finally {
                running.set(false);
            }
        });

        return new AdminScraperService.ManualRunResponse(runKey, planName, true, "Manual scraper run accepted", acceptedAt);
    }

    private String normalizePlanName(String planName) {
        if (planName == null || planName.isBlank()) {
            throw new IllegalArgumentException("Highscore plan name is required");
        }
        return planName.trim();
    }
}
