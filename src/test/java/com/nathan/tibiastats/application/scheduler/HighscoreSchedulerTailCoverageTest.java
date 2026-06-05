package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.HighscoreService;
import com.nathan.tibiastats.application.service.ScrapeJobResult;
import com.nathan.tibiastats.application.service.ScrapeJobService;
import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HighscoreSchedulerTailCoverageTest {
    @Test
    void registeredHighscorePlanMarksJobAsFailedAndRethrowsWhenServiceFails() {
        HighscoreService highscoreService = mock(HighscoreService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        properties.setEnabled(true);
        HighscoreScrapeProperties.Plan plan = enabledPlan();
        properties.setPlans(Map.of("failure-plan", plan));
        IllegalStateException failure = new IllegalStateException("scrape failed");
        when(scrapeJobService.start(ScrapeJobService.HIGHSCORE_SCRAPER)).thenReturn(50L);
        when(highscoreService.updateHighscores("failure-plan", plan)).thenThrow(failure);
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        HighscoreScrapeScheduler scheduler = new HighscoreScrapeScheduler(highscoreService, properties, scrapeJobService);

        scheduler.configureTasks(registrar);

        assertThat(registrar.getTriggerTaskList()).hasSize(1);
        assertThatThrownBy(() -> registrar.getTriggerTaskList().getFirst().getRunnable().run()).isSameAs(failure);
        verify(scrapeJobService).finishFailure(50L, ScrapeJobResult.empty(), failure);
    }

    @Test
    void startupPlanRunsOnVirtualThreadAndFinishesSuccessfulJob() throws Exception {
        HighscoreService highscoreService = mock(HighscoreService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        properties.setEnabled(true);
        HighscoreScrapeProperties.Plan plan = enabledPlan();
        plan.setRunOnStartup(true);
        plan.setStartupDelayMs(0L);
        Map<String, HighscoreScrapeProperties.Plan> plans = new LinkedHashMap<>();
        plans.put("startup-plan", plan);
        properties.setPlans(plans);
        ScrapeJobResult result = ScrapeJobResult.of(2, 0, 20, 0);
        when(scrapeJobService.start(ScrapeJobService.HIGHSCORE_SCRAPER)).thenReturn(51L);
        when(highscoreService.updateHighscores("startup-plan", plan)).thenReturn(result);
        HighscoreScrapeScheduler scheduler = new HighscoreScrapeScheduler(highscoreService, properties, scrapeJobService);

        scheduler.runStartupPlans();

        verify(highscoreService, timeout(2_000L)).updateHighscores("startup-plan", plan);
        verify(scrapeJobService, timeout(2_000L)).finishSuccess(51L, result);
    }

    @Test
    void startupPlansAreSkippedWhenGlobalSchedulingIsDisabledOrPlanIsNotStartupEnabled() throws Exception {
        HighscoreService highscoreService = mock(HighscoreService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        HighscoreScrapeProperties disabledProperties = new HighscoreScrapeProperties();
        disabledProperties.setEnabled(false);
        HighscoreScrapeProperties.Plan startupPlan = enabledPlan();
        startupPlan.setRunOnStartup(true);
        disabledProperties.setPlans(Map.of("startup-plan", startupPlan));
        new HighscoreScrapeScheduler(highscoreService, disabledProperties, scrapeJobService).runStartupPlans();

        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        properties.setEnabled(true);
        properties.setPlans(Map.of("cron-only", enabledPlan()));
        new HighscoreScrapeScheduler(highscoreService, properties, scrapeJobService).runStartupPlans();

        Thread.sleep(50L);
        verifyNoInteractions(highscoreService, scrapeJobService);
    }

    @Test
    void logConfigurationEnumeratesEnabledConfigurationWithoutSchedulingTasks() {
        HighscoreService highscoreService = mock(HighscoreService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        properties.setEnabled(true);
        properties.setPlans(Map.of("daily", enabledPlan()));
        HighscoreScrapeScheduler scheduler = new HighscoreScrapeScheduler(highscoreService, properties, scrapeJobService);

        scheduler.logConfiguration();

        verifyNoInteractions(highscoreService, scrapeJobService);
    }

    private static HighscoreScrapeProperties.Plan enabledPlan() {
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setEnabled(true);
        plan.setCron("0 0 1 * * *");
        plan.setZone("UTC");
        return plan;
    }
}
