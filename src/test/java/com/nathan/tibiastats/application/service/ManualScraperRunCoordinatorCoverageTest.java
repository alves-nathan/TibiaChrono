package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualScraperRunCoordinatorCoverageTest {
    @Test
    void triggerWorldsStartsManualJobAndFinishesSuccessAsynchronously() throws Exception {
        ScrapeService scrapeService = mock(ScrapeService.class);
        ScrapeJobService scrapeJobs = mock(ScrapeJobService.class);
        ManualScraperRunCoordinator coordinator = coordinator(scrapeService, scrapeJobs, new HighscoreScrapeProperties());
        CountDownLatch workerCalled = new CountDownLatch(1);
        when(scrapeJobs.start(ScrapeJobService.WORLD_SCRAPER)).thenReturn(100L);
        when(scrapeService.updateAllWorlds()).thenAnswer(invocation -> {
            workerCalled.countDown();
            return ScrapeJobResult.of(3, 1, 2, 0);
        });

        AdminScraperService.ManualRunResponse response = coordinator.triggerWorlds();

        assertThat(response.scraper()).isEqualTo("worlds");
        assertThat(response.planName()).isNull();
        assertThat(response.accepted()).isTrue();
        assertThat(response.message()).contains("accepted");
        assertThat(response.acceptedAt()).isNotNull();
        assertThat(workerCalled.await(2, TimeUnit.SECONDS)).isTrue();
        awaitInactive(coordinator, "worlds");
        ArgumentCaptor<ScrapeJobResult> result = ArgumentCaptor.forClass(ScrapeJobResult.class);
        verify(scrapeJobs).finishSuccess(eq(100L), result.capture());
        assertThat(result.getValue().itemsProcessed()).isEqualTo(3);
        assertThat(result.getValue().itemsCreated()).isEqualTo(1);
        assertThat(result.getValue().itemsUpdated()).isEqualTo(2);
        assertThat(result.getValue().itemsFailed()).isZero();
    }

    @Test
    void triggerWorldsRejectsSecondRunWhileSameManualRunIsActiveAndThenClearsFlag() throws Exception {
        ScrapeService scrapeService = mock(ScrapeService.class);
        ScrapeJobService scrapeJobs = mock(ScrapeJobService.class);
        ManualScraperRunCoordinator coordinator = coordinator(scrapeService, scrapeJobs, new HighscoreScrapeProperties());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(scrapeJobs.start(ScrapeJobService.WORLD_SCRAPER)).thenReturn(101L);
        when(scrapeService.updateAllWorlds()).thenAnswer(invocation -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return ScrapeJobResult.empty();
        });

        coordinator.triggerWorlds();

        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(coordinator.isManualRunActive("worlds")).isTrue();
        assertThatThrownBy(coordinator::triggerWorlds)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Manual scraper run already active: worlds");
        release.countDown();
        awaitInactive(coordinator, "worlds");
    }

    @Test
    void triggerHighscorePlanNormalizesPlanNameTracksActiveHighscoreRunsAndUsesEmptyResultWhenWorkerReturnsNull() throws Exception {
        HighscoreService highscoreService = mock(HighscoreService.class);
        ScrapeJobService scrapeJobs = mock(ScrapeJobService.class);
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setEnabled(true);
        properties.setPlans(Map.of("daily", plan));
        ManualScraperRunCoordinator coordinator = coordinator(highscoreService, scrapeJobs, properties);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(scrapeJobs.start(ScrapeJobService.HIGHSCORE_SCRAPER)).thenReturn(200L);
        when(highscoreService.updateHighscores("daily", plan)).thenAnswer(invocation -> {
            started.countDown();
            release.await(2, TimeUnit.SECONDS);
            return null;
        });

        AdminScraperService.ManualRunResponse response = coordinator.triggerHighscorePlan(" daily ");

        assertThat(response.scraper()).isEqualTo("highscores:daily");
        assertThat(response.planName()).isEqualTo("daily");
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(coordinator.hasAnyHighscoreManualRunActive()).isTrue();
        release.countDown();
        awaitInactive(coordinator, "highscores:daily");
        assertThat(coordinator.hasAnyHighscoreManualRunActive()).isFalse();
        ArgumentCaptor<ScrapeJobResult> result = ArgumentCaptor.forClass(ScrapeJobResult.class);
        verify(scrapeJobs).finishSuccess(eq(200L), result.capture());
        assertThat(result.getValue()).isEqualTo(ScrapeJobResult.empty());
        verify(highscoreService).updateHighscores("daily", plan);
    }

    @Test
    void triggerHighscorePlanRejectsMissingBlankAndUnknownPlanNames() {
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        properties.setPlans(Map.of("daily", new HighscoreScrapeProperties.Plan()));
        ManualScraperRunCoordinator coordinator = coordinator(mock(HighscoreService.class), mock(ScrapeJobService.class), properties);

        assertThatThrownBy(() -> coordinator.triggerHighscorePlan(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Highscore plan name is required");
        assertThatThrownBy(() -> coordinator.triggerHighscorePlan(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Highscore plan name is required");
        assertThatThrownBy(() -> coordinator.triggerHighscorePlan("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown highscore plan: missing");
    }

    @Test
    void manualWorkerFailureIsRecordedAndActiveFlagIsCleared() throws Exception {
        CharacterDetailsService characterDetailsService = mock(CharacterDetailsService.class);
        ScrapeJobService scrapeJobs = mock(ScrapeJobService.class);
        ManualScraperRunCoordinator coordinator = new ManualScraperRunCoordinator(
                mock(ScrapeService.class),
                characterDetailsService,
                mock(GuildScrapeService.class),
                mock(HighscoreService.class),
                scrapeJobs,
                new HighscoreScrapeProperties()
        );
        when(scrapeJobs.start(ScrapeJobService.CHARACTER_DETAILS_SCRAPER)).thenReturn(300L);
        when(characterDetailsService.updateMissingDetailsBatch()).thenThrow(new IllegalStateException("boom"));

        coordinator.triggerCharacterDetails();

        awaitInactive(coordinator, "character-details");
        verify(scrapeJobs).finishFailure(eq(300L), eq(ScrapeJobResult.empty()), any(IllegalStateException.class));
    }

    private static ManualScraperRunCoordinator coordinator(ScrapeService scrapeService,
                                                          ScrapeJobService scrapeJobs,
                                                          HighscoreScrapeProperties properties) {
        return new ManualScraperRunCoordinator(
                scrapeService,
                mock(CharacterDetailsService.class),
                mock(GuildScrapeService.class),
                mock(HighscoreService.class),
                scrapeJobs,
                properties
        );
    }

    private static ManualScraperRunCoordinator coordinator(HighscoreService highscoreService,
                                                          ScrapeJobService scrapeJobs,
                                                          HighscoreScrapeProperties properties) {
        return new ManualScraperRunCoordinator(
                mock(ScrapeService.class),
                mock(CharacterDetailsService.class),
                mock(GuildScrapeService.class),
                highscoreService,
                scrapeJobs,
                properties
        );
    }

    private static void awaitInactive(ManualScraperRunCoordinator coordinator, String runKey) throws InterruptedException {
        waitUntil(() -> !coordinator.isManualRunActive(runKey));
    }

    private static void waitUntil(BooleanSupplierWithException condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    @FunctionalInterface
    private interface BooleanSupplierWithException {
        boolean getAsBoolean() throws InterruptedException;
    }
}
