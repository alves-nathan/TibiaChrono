package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.application.query.ApiQueryService;
import com.nathan.tibiastats.config.AppProperties;
import com.nathan.tibiastats.config.GuildScrapeProperties;
import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminScraperStatusService {
    private static final String WORLDS_KEY = "worlds";
    private static final String CHARACTER_DETAILS_KEY = "character-details";
    private static final String GUILDS_KEY = "guilds";
    private static final String HIGHSCORE_KEY_PREFIX = "highscores:";

    private final HighscoreService highscoreService;
    private final ManualScraperRunCoordinator manualRunCoordinator;
    private final ApiQueryService queries;
    private final AppProperties appProperties;
    private final GuildScrapeProperties guildProperties;
    private final HighscoreScrapeProperties highscoreProperties;
    private final HighscoreBackoffStatusMapper highscoreBackoffStatusMapper;

    public AdminScraperStatusService(HighscoreService highscoreService,
                                     ManualScraperRunCoordinator manualRunCoordinator,
                                     ApiQueryService queries,
                                     AppProperties appProperties,
                                     GuildScrapeProperties guildProperties,
                                     HighscoreScrapeProperties highscoreProperties,
                                     HighscoreBackoffStatusMapper highscoreBackoffStatusMapper) {
        this.highscoreService = highscoreService;
        this.manualRunCoordinator = manualRunCoordinator;
        this.queries = queries;
        this.appProperties = appProperties;
        this.guildProperties = guildProperties;
        this.highscoreProperties = highscoreProperties;
        this.highscoreBackoffStatusMapper = highscoreBackoffStatusMapper;
    }

    public AdminScraperService.ScraperStatusResponse status() {
        List<AdminScraperService.ScraperStatus> scrapers = new ArrayList<>();
        scrapers.add(new AdminScraperService.ScraperStatus(
                WORLDS_KEY,
                appProperties.getWorlds().isEnabled(),
                "fixedRateMs=" + appProperties.getWorlds().getRateMs(),
                manualRunCoordinator.isManualRunActive(WORLDS_KEY),
                hasRunningJob(ScrapeJobService.WORLD_SCRAPER),
                latestJob(ScrapeJobService.WORLD_SCRAPER),
                latestRunningJob(ScrapeJobService.WORLD_SCRAPER)
        ));
        scrapers.add(new AdminScraperService.ScraperStatus(
                CHARACTER_DETAILS_KEY,
                appProperties.getCharacterDetails().isEnabled(),
                "fixedDelayMs=" + appProperties.getCharacterDetails().getRateMs()
                        + ", initialDelayMs=" + appProperties.getCharacterDetails().getInitialDelayMs()
                        + ", batchSize=" + appProperties.getCharacterDetails().getBatchSize(),
                manualRunCoordinator.isManualRunActive(CHARACTER_DETAILS_KEY),
                hasRunningJob(ScrapeJobService.CHARACTER_DETAILS_SCRAPER),
                latestJob(ScrapeJobService.CHARACTER_DETAILS_SCRAPER),
                latestRunningJob(ScrapeJobService.CHARACTER_DETAILS_SCRAPER)
        ));
        scrapers.add(new AdminScraperService.ScraperStatus(
                GUILDS_KEY,
                guildProperties.isEnabled(),
                "fixedDelayMs=" + guildProperties.getRateMs()
                        + ", initialDelayMs=" + guildProperties.getInitialDelayMs()
                        + ", listEnabled=" + guildProperties.isListEnabled()
                        + ", detailsEnabled=" + guildProperties.isDetailsEnabled(),
                manualRunCoordinator.isManualRunActive(GUILDS_KEY),
                hasRunningJob(ScrapeJobService.GUILD_SCRAPER),
                latestJob(ScrapeJobService.GUILD_SCRAPER),
                latestRunningJob(ScrapeJobService.GUILD_SCRAPER)
        ));
        scrapers.add(new AdminScraperService.ScraperStatus(
                "highscores",
                highscoreProperties.isEnabled(),
                "plans=" + highscoreProperties.effectivePlans().size(),
                manualRunCoordinator.hasAnyHighscoreManualRunActive(),
                highscoreService.isRunning() || hasRunningJob(ScrapeJobService.HIGHSCORE_SCRAPER),
                latestJob(ScrapeJobService.HIGHSCORE_SCRAPER),
                latestRunningJob(ScrapeJobService.HIGHSCORE_SCRAPER)
        ));

        List<AdminScraperService.HighscorePlanStatus> highscorePlans = highscoreProperties.effectivePlans().entrySet().stream()
                .map(entry -> toHighscorePlanStatus(entry.getKey(), entry.getValue()))
                .toList();

        return new AdminScraperService.ScraperStatusResponse(
                scrapers,
                highscorePlans,
                highscoreBackoffStatusMapper.toBackoffStatus(highscoreService.getHttpBackoffState())
        );
    }

    private AdminScraperService.HighscorePlanStatus toHighscorePlanStatus(String planName, HighscoreScrapeProperties.Plan plan) {
        return new AdminScraperService.HighscorePlanStatus(
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
                manualRunCoordinator.isManualRunActive(HIGHSCORE_KEY_PREFIX + planName)
        );
    }

    private boolean hasRunningJob(String jobName) {
        return latestRunningJob(jobName) != null;
    }

    private AdminScraperService.ScrapeJobStatusView latestJob(String jobName) {
        return queries.findScrapeJobs(jobName, null, 1).stream()
                .findFirst()
                .map(this::toScrapeJobStatusView)
                .orElse(null);
    }

    private AdminScraperService.ScrapeJobStatusView latestRunningJob(String jobName) {
        return queries.findScrapeJobs(jobName, "RUNNING", 1).stream()
                .findFirst()
                .map(this::toScrapeJobStatusView)
                .orElse(null);
    }

    private AdminScraperService.ScrapeJobStatusView toScrapeJobStatusView(ApiQueryService.ScrapeJobView view) {
        return new AdminScraperService.ScrapeJobStatusView(
                view.id(),
                view.jobName(),
                view.status(),
                view.startedAt(),
                view.finishedAt(),
                view.durationMs(),
                view.itemsProcessed(),
                view.itemsCreated(),
                view.itemsUpdated(),
                view.itemsFailed(),
                view.errorMessage()
        );
    }
}
