package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.port.HighscorePort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HighscorePageFetcherTest {
    @Test
    void fetchPageMapsRowsAndCoordinatesCooldownThrottleAndSemaphoreRelease() throws Exception {
        HighscorePort port = mock(HighscorePort.class);
        HighscoreHttpBackoffCoordinator backoff = mock(HighscoreHttpBackoffCoordinator.class);
        HighscoreRequestThrottle throttle = mock(HighscoreRequestThrottle.class);
        HighscoreFetchRetryPolicy retryPolicy = new HighscoreFetchRetryPolicy();
        HighscoreScope scope = scope();
        HighscoreScrapeProperties.Plan plan = plan(1, true);
        Semaphore semaphore = new Semaphore(1);
        AtomicBoolean rateLimited = new AtomicBoolean(false);
        when(port.fetchHighscores("Antica", StatCategory.EXPERIENCE, 0, 2))
                .thenReturn(List.of(
                        new HighscorePort.HighscoreRow(1, "Knight One", 123_456L),
                        new HighscorePort.HighscoreRow(2, "Mage Two", 99_999L)
                ));
        HighscorePageFetcher fetcher = new HighscorePageFetcher(port, backoff, throttle, retryPolicy);

        HighscorePageFetcher.PageResult result = fetcher.fetchPage(scope, 2, semaphore, plan, rateLimited);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.rows()).extracting(HighscorePageFetcher.PageRow::name)
                .containsExactly("Knight One", "Mage Two");
        assertThat(result.rows()).extracting(HighscorePageFetcher.PageRow::value)
                .containsExactly(123_456L, 99_999L);
        assertThat(result.rows()).extracting(HighscorePageFetcher.PageRow::rank)
                .containsExactly(1, 2);
        assertThat(semaphore.availablePermits()).isEqualTo(1);
        verify(backoff).awaitCooldown(plan);
        verify(throttle).awaitBeforeRequest(plan);
    }

    @Test
    void fetchPageFailsFastWhenRunIsAlreadyRateLimited() {
        HighscorePort port = mock(HighscorePort.class);
        HighscoreHttpBackoffCoordinator backoff = mock(HighscoreHttpBackoffCoordinator.class);
        HighscoreRequestThrottle throttle = mock(HighscoreRequestThrottle.class);
        HighscorePageFetcher fetcher = new HighscorePageFetcher(port, backoff, throttle, new HighscoreFetchRetryPolicy());
        Semaphore semaphore = new Semaphore(1);

        assertThatThrownBy(() -> fetcher.fetchPage(scope(), 1, semaphore, plan(1, true), new AtomicBoolean(true)))
                .isInstanceOf(RateLimitedHighscoreException.class)
                .hasMessageContaining("already marked as rate-limited");

        assertThat(semaphore.availablePermits()).isEqualTo(1);
        verifyNoInteractions(port, backoff, throttle);
    }

    @Test
    void fetchPageActivatesBackoffAndMarksRunRateLimitedWhenForbiddenShouldAbort() {
        HighscorePort port = mock(HighscorePort.class);
        HighscoreHttpBackoffCoordinator backoff = mock(HighscoreHttpBackoffCoordinator.class);
        HighscoreRequestThrottle throttle = mock(HighscoreRequestThrottle.class);
        HighscoreFetchRetryPolicy retryPolicy = new HighscoreFetchRetryPolicy();
        HighscoreScrapeProperties.Plan plan = plan(1, true);
        HighscoreScope scope = scope();
        AtomicBoolean rateLimited = new AtomicBoolean(false);
        RuntimeException forbidden = new RuntimeException("HTTP 403 from Tibia highscores");
        when(port.fetchHighscores("Antica", StatCategory.EXPERIENCE, 0, 1)).thenThrow(forbidden);
        HighscorePageFetcher fetcher = new HighscorePageFetcher(port, backoff, throttle, retryPolicy);

        assertThatThrownBy(() -> fetcher.fetchPage(scope, 1, new Semaphore(1), plan, rateLimited))
                .isInstanceOf(RateLimitedHighscoreException.class)
                .hasMessageContaining("HTTP 403");

        assertThat(rateLimited.get()).isTrue();
        verify(backoff).activate(plan, "HTTP 403 from Tibia highscores");
    }

    @Test
    void fetchPageRetriesTransientFailureWhenAttemptsRemain() throws Exception {
        HighscorePort port = mock(HighscorePort.class);
        HighscoreHttpBackoffCoordinator backoff = mock(HighscoreHttpBackoffCoordinator.class);
        HighscoreRequestThrottle throttle = mock(HighscoreRequestThrottle.class);
        HighscoreFetchRetryPolicy retryPolicy = new HighscoreFetchRetryPolicy();
        HighscoreScrapeProperties.Plan plan = plan(2, true);
        HighscoreScope scope = scope();
        when(port.fetchHighscores("Antica", StatCategory.EXPERIENCE, 0, 1))
                .thenThrow(new RuntimeException("HTTP 500 temporary"))
                .thenReturn(List.of(new HighscorePort.HighscoreRow(9, "Recovered", 77L)));
        HighscorePageFetcher fetcher = new HighscorePageFetcher(port, backoff, throttle, retryPolicy);

        HighscorePageFetcher.PageResult result = fetcher.fetchPage(scope, 1, new Semaphore(1), plan, new AtomicBoolean(false));

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().name()).isEqualTo("Recovered");
    }

    @Test
    void fetchPageActivatesBackoffButKeepsRunAliveWhenForbiddenDoesNotAbort() {
        HighscorePort port = mock(HighscorePort.class);
        HighscoreHttpBackoffCoordinator backoff = mock(HighscoreHttpBackoffCoordinator.class);
        HighscoreRequestThrottle throttle = mock(HighscoreRequestThrottle.class);
        HighscoreScrapeProperties.Plan plan = plan(1, false);
        AtomicBoolean rateLimited = new AtomicBoolean(false);
        RuntimeException forbidden = new RuntimeException("HTTP 429 too many requests");
        when(port.fetchHighscores("Antica", StatCategory.EXPERIENCE, 0, 1)).thenThrow(forbidden);
        HighscorePageFetcher fetcher = new HighscorePageFetcher(port, backoff, throttle, new HighscoreFetchRetryPolicy());

        assertThatThrownBy(() -> fetcher.fetchPage(scope(), 1, new Semaphore(1), plan, rateLimited))
                .isSameAs(forbidden);

        assertThat(rateLimited.get()).isFalse();
        verify(backoff).activate(plan, "HTTP 429 too many requests");
    }

    private static HighscoreScope scope() {
        return new HighscoreScope(1, "Antica", StatCategory.EXPERIENCE, 0);
    }

    private static HighscoreScrapeProperties.Plan plan(int maxAttempts, boolean abortRunOnForbidden) {
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setRequestMaxAttempts(maxAttempts);
        plan.setRetryBaseDelayMs(0);
        plan.setRetryMaxDelayMs(0);
        plan.setForbiddenInitialCooldownMs(0);
        plan.setAbortRunOnForbidden(abortRunOnForbidden);
        return plan;
    }
}
