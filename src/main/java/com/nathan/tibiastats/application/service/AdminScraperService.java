package com.nathan.tibiastats.application.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AdminScraperService {
    private final AdminScraperStatusService statusService;
    private final ManualScraperRunCoordinator manualRunCoordinator;
    private final HighscoreService highscoreService;
    private final HighscoreBackoffStatusMapper highscoreBackoffStatusMapper;

    public AdminScraperService(AdminScraperStatusService statusService,
                               ManualScraperRunCoordinator manualRunCoordinator,
                               HighscoreService highscoreService,
                               HighscoreBackoffStatusMapper highscoreBackoffStatusMapper) {
        this.statusService = statusService;
        this.manualRunCoordinator = manualRunCoordinator;
        this.highscoreService = highscoreService;
        this.highscoreBackoffStatusMapper = highscoreBackoffStatusMapper;
    }

    public ScraperStatusResponse status() {
        return statusService.status();
    }

    public HighscoreBackoffStatus highscoreBackoffStatus() {
        return highscoreBackoffStatusMapper.toBackoffStatus(highscoreService.getHttpBackoffState());
    }

    public HighscoreBackoffStatus resetHighscoreBackoff() {
        return highscoreBackoffStatusMapper.toBackoffStatus(highscoreService.resetHttpBackoffManually());
    }

    public ManualRunResponse triggerWorlds() {
        return manualRunCoordinator.triggerWorlds();
    }

    public ManualRunResponse triggerCharacterDetails() {
        return manualRunCoordinator.triggerCharacterDetails();
    }

    public ManualRunResponse triggerGuilds() {
        return manualRunCoordinator.triggerGuilds();
    }

    public ManualRunResponse triggerHighscorePlan(String planName) {
        return manualRunCoordinator.triggerHighscorePlan(planName);
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
            ScrapeJobStatusView latestJob,
            ScrapeJobStatusView latestRunningJob
    ) {}

    public record ScrapeJobStatusView(
            Long id,
            String jobName,
            String status,
            Instant startedAt,
            Instant finishedAt,
            Long durationMs,
            Integer itemsProcessed,
            Integer itemsCreated,
            Integer itemsUpdated,
            Integer itemsFailed,
            String errorMessage
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
