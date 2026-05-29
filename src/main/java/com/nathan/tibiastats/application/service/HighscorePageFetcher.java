package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.port.HighscorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class HighscorePageFetcher {
    private static final Logger log = LoggerFactory.getLogger(HighscorePageFetcher.class);

    private final HighscorePort highscorePort;
    private final HighscoreHttpBackoffCoordinator httpBackoffCoordinator;
    private final HighscoreRequestThrottle requestThrottle;
    private final HighscoreFetchRetryPolicy retryPolicy;

    public HighscorePageFetcher(
            HighscorePort highscorePort,
            HighscoreHttpBackoffCoordinator httpBackoffCoordinator,
            HighscoreRequestThrottle requestThrottle,
            HighscoreFetchRetryPolicy retryPolicy
    ) {
        this.highscorePort = highscorePort;
        this.httpBackoffCoordinator = httpBackoffCoordinator;
        this.requestThrottle = requestThrottle;
        this.retryPolicy = retryPolicy;
    }

    public PageResult fetchPage(
            HighscoreScope scope,
            int page,
            Semaphore requestSemaphore,
            HighscoreScrapeProperties.Plan plan,
            AtomicBoolean rateLimited
    ) throws InterruptedException {
        int maxAttempts = plan.getRequestMaxAttempts();
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (rateLimited.get()) {
                throw new RateLimitedHighscoreException("Highscore run already marked as rate-limited");
            }
            requestSemaphore.acquire();
            try {
                httpBackoffCoordinator.awaitCooldown(plan);
                requestThrottle.awaitBeforeRequest(plan);
                List<PageRow> rows = highscorePort.fetchHighscores(
                                scope.worldName(),
                                scope.category(),
                                scope.vocationFilterId(),
                                page
                        ).stream()
                        .map(row -> new PageRow(row.name(), row.value(), row.rank()))
                        .toList();
                return new PageResult(page, rows);
            } catch (RuntimeException ex) {
                lastFailure = ex;
                boolean transientFailure = retryPolicy.isTransientHighscoreFetchFailure(ex);
                boolean shouldRetry = transientFailure && attempt < maxAttempts;

                if (retryPolicy.isForbiddenOrRateLimited(ex)) {
                    httpBackoffCoordinator.activate(plan, retryPolicy.rootMessage(ex));
                    if (plan.isAbortRunOnForbidden()) {
                        rateLimited.set(true);
                        throw new RateLimitedHighscoreException(retryPolicy.rootMessage(ex), ex);
                    }
                }

                if (!shouldRetry) {
                    throw ex;
                }

                long retryDelayMs = retryPolicy.retryDelayMs(plan, attempt, ex);
                log.warn(
                        "[HIGHSCORE_SCRAPER] Transient page fetch failure. Retrying: scope={}, page={}, attempt={}/{}, delayMs={}, error={}",
                        scope.label(),
                        page,
                        attempt,
                        maxAttempts,
                        retryDelayMs,
                        retryPolicy.rootMessage(ex)
                );
                retryPolicy.sleepWithRetryHeartbeat(plan, retryDelayMs, scope, page, attempt, maxAttempts);
            } finally {
                requestSemaphore.release();
            }
        }

        throw lastFailure == null
                ? new IllegalStateException("Failed to fetch highscore page after retries")
                : lastFailure;
    }

    public record PageResult(int page, List<PageRow> rows) {}

    public record PageRow(String name, long value, int rank) {}
}
