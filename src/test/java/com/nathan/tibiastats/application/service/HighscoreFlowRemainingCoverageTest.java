package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.port.HighscorePort;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HighscoreFlowRemainingCoverageTest {
    @Test
    void pageFetcherUsesDefensiveFallbackWhenPlanReportsZeroAttempts() {
        HighscoreScrapeProperties.Plan plan = mock(HighscoreScrapeProperties.Plan.class);
        when(plan.getRequestMaxAttempts()).thenReturn(0);
        HighscorePageFetcher fetcher = new HighscorePageFetcher(
                mock(HighscorePort.class),
                mock(HighscoreHttpBackoffCoordinator.class),
                mock(HighscoreRequestThrottle.class),
                new HighscoreFetchRetryPolicy()
        );

        assertThatThrownBy(() -> fetcher.fetchPage(scope("Antica"), 1, new Semaphore(1), plan, new AtomicBoolean(false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to fetch highscore page after retries");
    }

    @Test
    void retryPolicyRestoresInterruptFlagWhenRetrySleepIsInterrupted() {
        HighscoreFetchRetryPolicy policy = new HighscoreFetchRetryPolicy();
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();

        try {
            Thread.currentThread().interrupt();

            assertThatThrownBy(() -> policy.sleepWithRetryHeartbeat(plan, 50, scope("Antica"), 1, 1, 2))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Interrupted while waiting during highscore scrape");

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void scopeWorkerCountsSuccessEmptyAndFailedResultsAndStopsWhenAllScopesWereClaimed() {
        HighscoreScopeScraper scopeScraper = mock(HighscoreScopeScraper.class);
        HighscoreScopeWorker worker = new HighscoreScopeWorker(scopeScraper);
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setProgressLogIntervalScopes(1);
        ExecutorService executor = mock(ExecutorService.class);
        Semaphore requestSemaphore = new Semaphore(1);
        AtomicBoolean rateLimited = new AtomicBoolean(false);
        AtomicInteger nextScopeIndex = new AtomicInteger();
        AtomicInteger completedScopes = new AtomicInteger();
        List<HighscoreScope> scopes = List.of(
                scope("Antica"),
                scope("Secura"),
                scope("Wintera")
        );

        when(scopeScraper.scrape(
                any(HighscoreScope.class),
                anyMap(),
                any(ExecutorService.class),
                any(Semaphore.class),
                anyString(),
                any(HighscoreScrapeProperties.Plan.class),
                any(AtomicBoolean.class)
        )).thenReturn(
                new HighscoreScopeScrapeResult("SUCCESS", 1, 10),
                new HighscoreScopeScrapeResult("EMPTY", 2, 0),
                new HighscoreScopeScrapeResult("FAILED", 3, 30)
        );

        HighscoreScopeWorkerResult result = worker.run(
                1,
                scopes,
                nextScopeIndex,
                completedScopes,
                new HashMap<>(),
                executor,
                requestSemaphore,
                "daily",
                plan,
                rateLimited
        );

        assertThat(result.successScopes()).isEqualTo(1);
        assertThat(result.emptyScopes()).isEqualTo(1);
        assertThat(result.failedScopes()).isEqualTo(1);
        assertThat(result.pages()).isEqualTo(6);
        assertThat(result.rows()).isEqualTo(40);
        assertThat(completedScopes.get()).isEqualTo(3);
        assertThat(nextScopeIndex.get()).isGreaterThanOrEqualTo(scopes.size());
    }

    private static HighscoreScope scope(String world) {
        return new HighscoreScope(1, world, StatCategory.EXPERIENCE, 0);
    }
}
