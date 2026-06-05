package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.HighscoreService;
import com.nathan.tibiastats.application.service.ScrapeJobResult;
import com.nathan.tibiastats.application.service.ScrapeJobService;
import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HighscoreSchedulerRemainingCoverageTest {
    @Test
    void privateRunPlanSkipsWhenGlobalSchedulingIsDisabled() throws Exception {
        HighscoreService highscoreService = mock(HighscoreService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        properties.setEnabled(false);
        HighscoreScrapeScheduler scheduler = new HighscoreScrapeScheduler(highscoreService, properties, scrapeJobService);

        invokeRunPlan(scheduler, "disabled-global", enabledPlan(), "manual");

        verifyNoInteractions(highscoreService, scrapeJobService);
    }

    @Test
    void privateRunPlanSkipsWhenPlanIsDisabled() throws Exception {
        HighscoreService highscoreService = mock(HighscoreService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        properties.setEnabled(true);
        HighscoreScrapeProperties.Plan plan = enabledPlan();
        plan.setEnabled(false);
        HighscoreScrapeScheduler scheduler = new HighscoreScrapeScheduler(highscoreService, properties, scrapeJobService);

        invokeRunPlan(scheduler, "disabled-plan", plan, "manual");

        verifyNoInteractions(highscoreService, scrapeJobService);
    }

    @Test
    void startupPlanWithDelayCatchesFailureFromVirtualThread() {
        HighscoreService highscoreService = mock(HighscoreService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        properties.setEnabled(true);
        HighscoreScrapeProperties.Plan plan = enabledPlan();
        plan.setRunOnStartup(true);
        plan.setStartupDelayMs(1L);
        properties.setPlans(Map.of("delayed-failure", plan));
        IllegalStateException failure = new IllegalStateException("startup scrape failed");
        when(scrapeJobService.start(ScrapeJobService.HIGHSCORE_SCRAPER)).thenReturn(81L);
        when(highscoreService.updateHighscores("delayed-failure", plan)).thenThrow(failure);
        HighscoreScrapeScheduler scheduler = new HighscoreScrapeScheduler(highscoreService, properties, scrapeJobService);

        scheduler.runStartupPlans();

        verify(highscoreService, timeout(2_000L)).updateHighscores("delayed-failure", plan);
        verify(scrapeJobService, timeout(2_000L)).finishFailure(eq(81L), any(ScrapeJobResult.class), same(failure));
    }

    private static void invokeRunPlan(
            HighscoreScrapeScheduler scheduler,
            String planName,
            HighscoreScrapeProperties.Plan plan,
            String trigger
    ) throws Exception {
        Method method = HighscoreScrapeScheduler.class.getDeclaredMethod(
                "runPlan",
                String.class,
                HighscoreScrapeProperties.Plan.class,
                String.class
        );
        method.setAccessible(true);
        try {
            method.invoke(scheduler, planName, plan, trigger);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }

    private static HighscoreScrapeProperties.Plan enabledPlan() {
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setEnabled(true);
        plan.setCron("0 0 1 * * *");
        plan.setZone("UTC");
        return plan;
    }
}
