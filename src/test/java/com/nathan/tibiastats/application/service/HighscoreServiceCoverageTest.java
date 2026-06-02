package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreHttpBackoffState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HighscoreServiceCoverageTest {
    @Test
    void updateAllHighscoresDelegatesDefaultLegacyPlanAndClearsRunningFlag() {
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        HighscoreHttpBackoffCoordinator backoff = mock(HighscoreHttpBackoffCoordinator.class);
        HighscoreRunCoordinator runCoordinator = mock(HighscoreRunCoordinator.class);
        HighscoreService service = new HighscoreService(properties, backoff, runCoordinator);
        ScrapeJobResult expected = ScrapeJobResult.of(2, 0, 10, 0);
        when(runCoordinator.run(any(Instant.class), eq("default"), any(HighscoreScrapeProperties.Plan.class)))
                .thenReturn(expected);

        ScrapeJobResult result = service.updateAllHighscores();

        assertThat(result).isEqualTo(expected);
        assertThat(service.isRunning()).isFalse();
        ArgumentCaptor<HighscoreScrapeProperties.Plan> plan = ArgumentCaptor.forClass(HighscoreScrapeProperties.Plan.class);
        verify(backoff).isActive("default");
        verify(runCoordinator).run(any(Instant.class), eq("default"), plan.capture());
        assertThat(plan.getValue().isEnabled()).isTrue();
        assertThat(plan.getValue().getMaxPages()).isEqualTo(properties.getMaxPages());
    }

    @Test
    void updateHighscoresSkipsDisabledGlobalConfigDisabledPlanAndActiveBackoff() {
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        HighscoreHttpBackoffCoordinator backoff = mock(HighscoreHttpBackoffCoordinator.class);
        HighscoreRunCoordinator runCoordinator = mock(HighscoreRunCoordinator.class);
        HighscoreService service = new HighscoreService(properties, backoff, runCoordinator);
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();

        properties.setEnabled(false);
        assertThat(service.updateHighscores("daily", plan)).isEqualTo(ScrapeJobResult.empty());
        verifyNoInteractions(backoff, runCoordinator);

        properties.setEnabled(true);
        plan.setEnabled(false);
        assertThat(service.updateHighscores("daily", plan)).isEqualTo(ScrapeJobResult.empty());
        verifyNoInteractions(backoff, runCoordinator);

        plan.setEnabled(true);
        when(backoff.isActive("daily")).thenReturn(true);
        assertThat(service.updateHighscores("daily", plan)).isEqualTo(ScrapeJobResult.empty());
        verify(backoff).isActive("daily");
        verify(runCoordinator, never()).run(any(), eq("daily"), same(plan));
    }

    @Test
    void updateHighscoresSkipsOverlappingRunAndAllowsNextRunAfterCompletion() throws Exception {
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        HighscoreHttpBackoffCoordinator backoff = mock(HighscoreHttpBackoffCoordinator.class);
        HighscoreRunCoordinator runCoordinator = mock(HighscoreRunCoordinator.class);
        HighscoreService service = new HighscoreService(properties, backoff, runCoordinator);
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        CountDownLatch enteredRun = new CountDownLatch(1);
        CountDownLatch releaseRun = new CountDownLatch(1);
        AtomicReference<ScrapeJobResult> firstResult = new AtomicReference<>();
        when(backoff.isActive("daily")).thenReturn(false);
        when(runCoordinator.run(any(Instant.class), eq("daily"), same(plan))).thenAnswer(invocation -> {
            enteredRun.countDown();
            releaseRun.await(2, TimeUnit.SECONDS);
            return ScrapeJobResult.of(1, 0, 1, 0);
        });

        Thread worker = Thread.startVirtualThread(() -> firstResult.set(service.updateHighscores("daily", plan)));

        assertThat(enteredRun.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(service.isRunning()).isTrue();
        assertThat(service.updateHighscores("daily", plan)).isEqualTo(ScrapeJobResult.empty());

        releaseRun.countDown();
        worker.join(2_000L);

        assertThat(firstResult.get()).isEqualTo(ScrapeJobResult.of(1, 0, 1, 0));
        assertThat(service.isRunning()).isFalse();
        verify(runCoordinator).run(any(Instant.class), eq("daily"), same(plan));
    }

    @Test
    void httpBackoffStateAccessorsDelegateToCoordinator() {
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        HighscoreHttpBackoffCoordinator backoff = mock(HighscoreHttpBackoffCoordinator.class);
        HighscoreRunCoordinator runCoordinator = mock(HighscoreRunCoordinator.class);
        HighscoreService service = new HighscoreService(properties, backoff, runCoordinator);
        HighscoreHttpBackoffState current = new HighscoreHttpBackoffState(
                Instant.now().plusSeconds(60),
                2,
                60_000L,
                "RATE_LIMITED",
                "HTTP 403",
                Instant.now(),
                null
        );
        HighscoreHttpBackoffState reset = new HighscoreHttpBackoffState(null, 0, 0L, "OK", null, null, Instant.now());
        when(backoff.getState()).thenReturn(current);
        when(backoff.resetManually()).thenReturn(reset);

        assertThat(service.getHttpBackoffState()).isSameAs(current);
        assertThat(service.resetHttpBackoffManually()).isSameAs(reset);
    }
}
