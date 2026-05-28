package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.AppProperties;
import com.nathan.tibiastats.config.GuildScrapeProperties;
import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.infrastructure.persistence.HighscoreScrapeStateRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Service
public class AdminScraperService {
    private static final String WORLDS_KEY = "worlds";
    private static final String CHARACTER_DETAILS_KEY = "character-details";
    private static final String GUILDS_KEY = "guilds";
    private static final String HIGHSCORE_KEY_PREFIX = "highscores:";

    private final ScrapeService scrapeService;
    private final CharacterDetailsService characterDetailsService;
    private final GuildScrapeService guildScrapeService;
    private final HighscoreService highscoreService;
    private final ScrapeJobService scrapeJobService;
    private final ApiQueryService queries;
    private final AppProperties appProperties;
    private final GuildScrapeProperties guildProperties;
    private final HighscoreScrapeProperties highscoreProperties;
    private final Map<String, AtomicBoolean> manualRuns = new ConcurrentHashMap<>();

    public AdminScraperService(ScrapeService scrapeService,
                               CharacterDetailsService characterDetailsService,
                               GuildScrapeService guildScrapeService,
                               HighscoreService highscoreService,
                               ScrapeJobService scrapeJobService,
                               ApiQueryService queries,
                               AppProperties appProperties,
                               GuildScrapeProperties guildProperties,
                               HighscoreScrapeProperties highscoreProperties) {
        this.scrapeService = scrapeService;
        this.characterDetailsService = characterDetailsService;
        this.guildScrapeService = guildScrapeService;
        this.highscoreService = highscoreService;
        this.scrapeJobService = scrapeJobService;
        this.queries = queries;
        this.appProperties = appProperties;
        this.guildProperties = guildProperties;
        this.highscoreProperties = highscoreProperties;
    }

    public ScraperStatusResponse status() {
        List<ScraperStatus> scrapers = new ArrayList<>();
        scrapers.add(new ScraperStatus(
                WORLDS_KEY,
                appProperties.getWorlds().isEnabled(),
                "fixedRateMs=" + appProperties.getWorlds().getRateMs(),
                isManualRunActive(WORLDS_KEY),
                hasRunningJob(ScrapeJobService.WORLD_SCRAPER),
                latestJob(ScrapeJobService.WORLD_SCRAPER),
                latestRunningJob(ScrapeJobService.WORLD_SCRAPER)
        ));
        scrapers.add(new ScraperStatus(
                CHARACTER_DETAILS_KEY,
                appProperties.getCharacterDetails().isEnabled(),
                "fixedDelayMs=" + appProperties.getCharacterDetails().getRateMs()
                        + ", initialDelayMs=" + appProperties.getCharacterDetails().getInitialDelayMs()
                        + ", batchSize=" + appProperties.getCharacterDetails().getBatchSize(),
                isManualRunActive(CHARACTER_DETAILS_KEY),
                hasRunningJob(ScrapeJobService.CHARACTER_DETAILS_SCRAPER),
                latestJob(ScrapeJobService.CHARACTER_DETAILS_SCRAPER),
                latestRunningJob(ScrapeJobService.CHARACTER_DETAILS_SCRAPER)
        ));
        scrapers.add(new ScraperStatus(
                GUILDS_KEY,
                guildProperties.isEnabled(),
                "fixedDelayMs=" + guildProperties.getRateMs()
                        + ", initialDelayMs=" + guildProperties.getInitialDelayMs()
                        + ", listEnabled=" + guildProperties.isListEnabled()
                        + ", detailsEnabled=" + guildProperties.isDetailsEnabled(),
                isManualRunActive(GUILDS_KEY),
                hasRunningJob(ScrapeJobService.GUILD_SCRAPER),
                latestJob(ScrapeJobService.GUILD_SCRAPER),
                latestRunningJob(ScrapeJobService.GUILD_SCRAPER)
        ));
        scrapers.add(new ScraperStatus(
                "highscores",
                highscoreProperties.isEnabled(),
                "plans=" + highscoreProperties.effectivePlans().size(),
                hasAnyHighscoreManualRunActive(),
                highscoreService.isRunning() || hasRunningJob(ScrapeJobService.HIGHSCORE_SCRAPER),
                latestJob(ScrapeJobService.HIGHSCORE_SCRAPER),
                latestRunningJob(ScrapeJobService.HIGHSCORE_SCRAPER)
        ));

        List<HighscorePlanStatus> highscorePlans = highscoreProperties.effectivePlans().entrySet().stream()
                .map(entry -> toHighscorePlanStatus(entry.getKey(), entry.getValue()))
                .toList();

        return new ScraperStatusResponse(scrapers, highscorePlans, highscoreBackoffStatus());
    }

    public HighscoreBackoffStatus highscoreBackoffStatus() {
        return toBackoffStatus(highscoreService.getHttpBackoffState());
    }

    public HighscoreBackoffStatus resetHighscoreBackoff() {
        return toBackoffStatus(highscoreService.resetHttpBackoffManually());
    }

    public ManualRunResponse triggerWorlds() {
        return triggerManualRun(
                WORLDS_KEY,
                ScrapeJobService.WORLD_SCRAPER,
                null,
                scrapeService::updateAllWorlds
        );
    }

    public ManualRunResponse triggerCharacterDetails() {
        return triggerManualRun(
                CHARACTER_DETAILS_KEY,
                ScrapeJobService.CHARACTER_DETAILS_SCRAPER,
                null,
                characterDetailsService::updateMissingDetailsBatch
        );
    }

    public ManualRunResponse triggerGuilds() {
        return triggerManualRun(
                GUILDS_KEY,
                ScrapeJobService.GUILD_SCRAPER,
                null,
                guildScrapeService::updateKnownGuilds
        );
    }

    public ManualRunResponse triggerHighscorePlan(String planName) {
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

    private ManualRunResponse triggerManualRun(String runKey,
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

        return new ManualRunResponse(runKey, planName, true, "Manual scraper run accepted", acceptedAt);
    }

    private HighscorePlanStatus toHighscorePlanStatus(String planName, HighscoreScrapeProperties.Plan plan) {
        return new HighscorePlanStatus(
                planName,
                highscoreProperties.isEnabled() && plan.isEnabled(),
                plan.getCron(),
                plan.getZone(),
                plan.isRunOnStartup(),
                plan.getStartupDelayMs(),
                plan.categoryList().stream().map(Enum::name).toList(),
                plan.vocationFilterIds(),
                plan.getWorldLimit(),
                plan.getScopesPerRun(),
                plan.getMaxPages(),
                plan.getParallelism(),
                plan.getRequestParallelism(),
                plan.getRequestMinIntervalMs(),
                plan.getForbiddenInitialCooldownMs(),
                plan.getForbiddenMaxCooldownMs(),
                plan.getForbiddenCooldownMultiplier(),
                isManualRunActive(HIGHSCORE_KEY_PREFIX + planName)
        );
    }

    private HighscoreBackoffStatus toBackoffStatus(HighscoreScrapeStateRepository.HighscoreHttpBackoffState state) {
        Instant now = Instant.now();
        if (state == null) {
            return new HighscoreBackoffStatus(false, null, 0, 0, 0, null, null, null, null);
        }
        return new HighscoreBackoffStatus(
                state.isActive(now),
                state.cooldownUntil(),
                state.remainingMs(now),
                state.consecutiveFailures(),
                state.currentCooldownMs(),
                state.lastStatus(),
                state.lastReason(),
                state.lastFailureAt(),
                state.lastSuccessAt()
        );
    }

    private boolean hasAnyHighscoreManualRunActive() {
        return manualRuns.entrySet().stream()
                .anyMatch(entry -> entry.getKey().startsWith(HIGHSCORE_KEY_PREFIX) && entry.getValue().get());
    }

    private boolean isManualRunActive(String runKey) {
        AtomicBoolean running = manualRuns.get(runKey);
        return running != null && running.get();
    }

    private boolean hasRunningJob(String jobName) {
        return latestRunningJob(jobName) != null;
    }

    private ApiQueryService.ScrapeJobView latestJob(String jobName) {
        return queries.findScrapeJobs(jobName, null, 1).stream().findFirst().orElse(null);
    }

    private ApiQueryService.ScrapeJobView latestRunningJob(String jobName) {
        return queries.findScrapeJobs(jobName, "RUNNING", 1).stream().findFirst().orElse(null);
    }

    private String normalizePlanName(String planName) {
        if (planName == null || planName.isBlank()) {
            throw new IllegalArgumentException("Highscore plan name is required");
        }
        return planName.trim();
    }

    public record ScraperStatusResponse(
            List<ScraperStatus> scrapers,
            List<HighscorePlanStatus> highscorePlans,
            HighscoreBackoffStatus highscoreBackoff
    ) {}

    public record ScraperStatus(
            String name,
            boolean enabled,
            String schedule,
            boolean manualRunActive,
            boolean running,
            ApiQueryService.ScrapeJobView latestJob,
            ApiQueryService.ScrapeJobView latestRunningJob
    ) {}

    public record HighscorePlanStatus(
            String name,
            boolean enabled,
            String cron,
            String zone,
            boolean runOnStartup,
            long startupDelayMs,
            List<String> categories,
            List<Integer> vocationFilterIds,
            int worldLimit,
            int scopesPerRun,
            int maxPages,
            int parallelism,
            int requestParallelism,
            int requestMinIntervalMs,
            long forbiddenInitialCooldownMs,
            long forbiddenMaxCooldownMs,
            double forbiddenCooldownMultiplier,
            boolean manualRunActive
    ) {}

    public record HighscoreBackoffStatus(
            boolean active,
            Instant cooldownUntil,
            long remainingMs,
            int consecutiveFailures,
            long currentCooldownMs,
            String lastStatus,
            String lastReason,
            Instant lastFailureAt,
            Instant lastSuccessAt
    ) {}

    public record ManualRunResponse(
            String scraper,
            String planName,
            boolean accepted,
            String message,
            Instant acceptedAt
    ) {}
}
