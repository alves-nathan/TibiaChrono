package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.application.query.ApiQueryService;
import com.nathan.tibiastats.config.AppProperties;
import com.nathan.tibiastats.config.GuildScrapeProperties;
import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreHttpBackoffState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminScraperStatusServiceTest {
    @Test
    void statusBuildsScraperAndHighscorePlanViewsFromRuntimeStateAndProperties() {
        HighscoreService highscoreService = mock(HighscoreService.class);
        ManualScraperRunCoordinator manualRuns = mock(ManualScraperRunCoordinator.class);
        ApiQueryService queries = mock(ApiQueryService.class);
        HighscoreBackoffStatusMapper backoffMapper = mock(HighscoreBackoffStatusMapper.class);
        AppProperties appProperties = new AppProperties();
        appProperties.getWorlds().setEnabled(true);
        appProperties.getWorlds().setRateMs(15_000L);
        appProperties.getCharacterDetails().setEnabled(false);
        appProperties.getCharacterDetails().setRateMs(30_000L);
        appProperties.getCharacterDetails().setInitialDelayMs(5_000L);
        appProperties.getCharacterDetails().setBatchSize(12);
        GuildScrapeProperties guildProperties = new GuildScrapeProperties();
        guildProperties.setEnabled(true);
        guildProperties.setRateMs(20_000L);
        guildProperties.setInitialDelayMs(2_000L);
        guildProperties.setListEnabled(true);
        guildProperties.setDetailsEnabled(false);
        HighscoreScrapeProperties highscoreProperties = new HighscoreScrapeProperties();
        highscoreProperties.setEnabled(true);
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setEnabled(true);
        plan.setCron("0 0 8 * * *");
        plan.setZone("UTC");
        plan.setRunOnStartup(true);
        plan.setStartupDelayMs(123L);
        plan.setCategories("EXPERIENCE,MAGIC_LEVEL");
        plan.setVocations("0,4");
        plan.setWorldLimit(3);
        plan.setScopesPerRun(4);
        plan.setMaxPages(5);
        plan.setParallelism(2);
        plan.setRequestParallelism(1);
        plan.setRequestMinIntervalMs(250);
        plan.setForbiddenInitialCooldownMs(10_000L);
        plan.setForbiddenMaxCooldownMs(20_000L);
        plan.setForbiddenCooldownMultiplier(3.0D);
        Map<String, HighscoreScrapeProperties.Plan> plans = new LinkedHashMap<>();
        plans.put("daily-exp", plan);
        highscoreProperties.setPlans(plans);
        Instant now = Instant.parse("2026-06-02T12:00:00Z");
        ApiQueryService.ScrapeJobView latest = new ApiQueryService.ScrapeJobView(
                1L,
                ScrapeJobService.WORLD_SCRAPER,
                "SUCCESS",
                now.minusSeconds(120),
                now.minusSeconds(60),
                60_000L,
                10,
                1,
                8,
                1,
                null
        );
        ApiQueryService.ScrapeJobView running = new ApiQueryService.ScrapeJobView(
                2L,
                ScrapeJobService.WORLD_SCRAPER,
                "RUNNING",
                now.minusSeconds(30),
                null,
                null,
                0,
                0,
                0,
                0,
                null
        );
        when(queries.findScrapeJobs(anyString(), isNull(), eq(1))).thenReturn(List.of(latest));
        when(queries.findScrapeJobs(anyString(), eq("RUNNING"), eq(1))).thenReturn(List.of(running));
        when(manualRuns.isManualRunActive("worlds")).thenReturn(false);
        when(manualRuns.isManualRunActive("character-details")).thenReturn(true);
        when(manualRuns.isManualRunActive("guilds")).thenReturn(false);
        when(manualRuns.isManualRunActive("highscores:daily-exp")).thenReturn(true);
        when(manualRuns.hasAnyHighscoreManualRunActive()).thenReturn(true);
        when(highscoreService.isRunning()).thenReturn(false);
        HighscoreHttpBackoffState state = new HighscoreHttpBackoffState(
                now.plusSeconds(60),
                2,
                60_000L,
                "ACTIVE",
                "HTTP 403",
                now.minusSeconds(60),
                now.minusSeconds(3600)
        );
        when(highscoreService.getHttpBackoffState()).thenReturn(state);
        AdminScraperService.HighscoreBackoffStatus backoff = new AdminScraperService.HighscoreBackoffStatus(
                true,
                now.plusSeconds(60),
                60_000L,
                2,
                60_000L,
                "ACTIVE",
                "HTTP 403",
                now.minusSeconds(60),
                now.minusSeconds(3600)
        );
        when(backoffMapper.toBackoffStatus(state)).thenReturn(backoff);
        AdminScraperStatusService service = new AdminScraperStatusService(
                highscoreService,
                manualRuns,
                queries,
                appProperties,
                guildProperties,
                highscoreProperties,
                backoffMapper
        );

        AdminScraperService.ScraperStatusResponse response = service.status();

        assertThat(response.scrapers()).hasSize(4);
        assertThat(response.scrapers()).extracting(AdminScraperService.ScraperStatus::name)
                .containsExactly("worlds", "character-details", "guilds", "highscores");
        AdminScraperService.ScraperStatus worlds = response.scrapers().getFirst();
        assertThat(worlds.enabled()).isTrue();
        assertThat(worlds.schedule()).isEqualTo("fixedRateMs=15000");
        assertThat(worlds.manualRunActive()).isFalse();
        assertThat(worlds.running()).isTrue();
        assertThat(worlds.latestJob().id()).isEqualTo(1L);
        assertThat(worlds.latestRunningJob().id()).isEqualTo(2L);
        AdminScraperService.ScraperStatus characterDetails = response.scrapers().get(1);
        assertThat(characterDetails.enabled()).isFalse();
        assertThat(characterDetails.schedule()).contains("fixedDelayMs=30000", "initialDelayMs=5000", "batchSize=12");
        assertThat(characterDetails.manualRunActive()).isTrue();
        AdminScraperService.ScraperStatus guilds = response.scrapers().get(2);
        assertThat(guilds.schedule()).contains("fixedDelayMs=20000", "initialDelayMs=2000", "listEnabled=true", "detailsEnabled=false");
        AdminScraperService.ScraperStatus highscores = response.scrapers().get(3);
        assertThat(highscores.enabled()).isTrue();
        assertThat(highscores.schedule()).isEqualTo("plans=1");
        assertThat(highscores.manualRunActive()).isTrue();
        assertThat(highscores.running()).isTrue();
        assertThat(response.highscoreBackoff()).isSameAs(backoff);
        assertThat(response.highscorePlans()).hasSize(1);
        AdminScraperService.HighscorePlanStatus planStatus = response.highscorePlans().getFirst();
        assertThat(planStatus.name()).isEqualTo("daily-exp");
        assertThat(planStatus.enabled()).isTrue();
        assertThat(planStatus.cron()).isEqualTo("0 0 8 * * *");
        assertThat(planStatus.zone()).isEqualTo("UTC");
        assertThat(planStatus.runOnStartup()).isTrue();
        assertThat(planStatus.startupDelayMs()).isEqualTo(123L);
        assertThat(planStatus.categories()).containsExactly("EXPERIENCE", "MAGIC_LEVEL");
        assertThat(planStatus.vocationFilterIds()).containsExactly(0, 4);
        assertThat(planStatus.worldLimit()).isEqualTo(3);
        assertThat(planStatus.scopesPerRun()).isEqualTo(4);
        assertThat(planStatus.maxPages()).isEqualTo(5);
        assertThat(planStatus.parallelism()).isEqualTo(2);
        assertThat(planStatus.requestParallelism()).isEqualTo(1);
        assertThat(planStatus.requestMinIntervalMs()).isEqualTo(250);
        assertThat(planStatus.forbiddenInitialCooldownMs()).isEqualTo(10_000L);
        assertThat(planStatus.forbiddenMaxCooldownMs()).isEqualTo(20_000L);
        assertThat(planStatus.forbiddenCooldownMultiplier()).isEqualTo(3.0D);
        assertThat(planStatus.manualRunActive()).isTrue();
    }

    @Test
    void statusUsesNullLatestJobWhenReadModelReturnsNoRows() {
        HighscoreService highscoreService = mock(HighscoreService.class);
        ManualScraperRunCoordinator manualRuns = mock(ManualScraperRunCoordinator.class);
        ApiQueryService queries = mock(ApiQueryService.class);
        HighscoreBackoffStatusMapper backoffMapper = mock(HighscoreBackoffStatusMapper.class);
        when(queries.findScrapeJobs(anyString(), isNull(), eq(1))).thenReturn(List.of());
        when(queries.findScrapeJobs(anyString(), eq("RUNNING"), eq(1))).thenReturn(List.of());
        when(backoffMapper.toBackoffStatus(isNull())).thenReturn(new AdminScraperService.HighscoreBackoffStatus(
                false,
                null,
                0L,
                0,
                0L,
                null,
                null,
                null,
                null
        ));
        AdminScraperStatusService service = new AdminScraperStatusService(
                highscoreService,
                manualRuns,
                queries,
                new AppProperties(),
                new GuildScrapeProperties(),
                new HighscoreScrapeProperties(),
                backoffMapper
        );

        AdminScraperService.ScraperStatusResponse response = service.status();

        assertThat(response.scrapers()).allSatisfy(status -> {
            assertThat(status.latestJob()).isNull();
            assertThat(status.latestRunningJob()).isNull();
        });
        assertThat(response.highscorePlans()).hasSize(1);
        assertThat(response.highscorePlans().getFirst().name()).isEqualTo("default");
    }
}
