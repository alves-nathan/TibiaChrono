package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class HighscoreRequestThrottle {
    private static final Logger log = LoggerFactory.getLogger(HighscoreRequestThrottle.class);

    private final AtomicLong nextAllowedHttpRequestAtMs = new AtomicLong(0);
    private final Object requestBudgetLock = new Object();
    private final ArrayDeque<Long> recentHighscoreRequestStarts = new ArrayDeque<>();
    private final AtomicLong lastRequestBudgetLogAtMs = new AtomicLong(0);

    public void awaitBeforeRequest(HighscoreScrapeProperties.Plan plan) {
        awaitGlobalRequestPace(plan);
        throttleRequestWithJitter(plan);
        awaitGlobalRequestBudget(plan);
    }

    /**
     * Global pacing across all virtual threads. Semaphores cap concurrent requests, but they do not prevent bursts
     * where many workers fire at the same millisecond. Tibia.com responds with 403 when bursts are too aggressive,
     * so we also space out request starts globally.
     */
    private void awaitGlobalRequestPace(HighscoreScrapeProperties.Plan plan) {
        int minIntervalMs = plan.getRequestMinIntervalMs();
        if (minIntervalMs <= 0) {
            return;
        }

        while (true) {
            long now = System.currentTimeMillis();
            long currentNextAllowed = nextAllowedHttpRequestAtMs.get();
            long requestStartAt = Math.max(now, currentNextAllowed);
            long nextAllowed = requestStartAt + minIntervalMs;

            if (nextAllowedHttpRequestAtMs.compareAndSet(currentNextAllowed, nextAllowed)) {
                long waitMs = requestStartAt - now;
                if (waitMs > 0) {
                    sleepMs(waitMs);
                }
                return;
            }
        }
    }

    private void throttleRequestWithJitter(HighscoreScrapeProperties.Plan plan) {
        int baseDelay = plan.getPageDelayMs();
        int jitter = plan.getRequestJitterMs();
        long delay = baseDelay;
        if (jitter > 0) {
            delay += ThreadLocalRandom.current().nextInt(jitter + 1);
        }
        if (delay > 0) {
            sleepMs(delay);
        }
    }

    /**
     * Hard cap for highscore HTTP request starts in a rolling window.
     *
     * <p>The request semaphore limits concurrency and global pacing spaces out bursts, but neither one alone protects
     * against unsafe aggregate volume when a full highscore run spans many worlds, categories, vocations and pages.
     * This in-memory budget is intentionally global to every highscore plan handled by this JVM and defaults to the
     * external safety ceiling of 150,000 requests per 10 minutes.</p>
     */
    private void awaitGlobalRequestBudget(HighscoreScrapeProperties.Plan plan) {
        int maxRequests = plan.getRequestBudgetMaxRequests();
        long windowMs = plan.getRequestBudgetWindowMs();
        if (maxRequests <= 0 || windowMs <= 0) {
            return;
        }

        while (true) {
            long now = System.currentTimeMillis();
            long waitMs;
            synchronized (requestBudgetLock) {
                pruneHighscoreRequestBudget(now, windowMs);
                if (recentHighscoreRequestStarts.size() < maxRequests) {
                    recentHighscoreRequestStarts.addLast(now);
                    return;
                }

                Long oldestRequestStart = recentHighscoreRequestStarts.peekFirst();
                waitMs = oldestRequestStart == null ? 1L : Math.max(1L, oldestRequestStart + windowMs - now);
            }

            logRequestBudgetHeartbeat(plan, waitMs, maxRequests, windowMs);
            sleepMs(Math.min(waitMs, 1000L));
        }
    }

    private void pruneHighscoreRequestBudget(long now, long windowMs) {
        long oldestAllowedRequestStart = now - windowMs;
        while (!recentHighscoreRequestStarts.isEmpty()) {
            Long first = recentHighscoreRequestStarts.peekFirst();
            if (first == null || first > oldestAllowedRequestStart) {
                return;
            }
            recentHighscoreRequestStarts.removeFirst();
        }
    }

    private void logRequestBudgetHeartbeat(HighscoreScrapeProperties.Plan plan, long waitMs, int maxRequests, long windowMs) {
        long now = System.currentTimeMillis();
        long intervalMs = plan.getCooldownLogIntervalMs();
        long lastLog = lastRequestBudgetLogAtMs.get();
        if (intervalMs > 0 && now - lastLog >= intervalMs && lastRequestBudgetLogAtMs.compareAndSet(lastLog, now)) {
            log.warn(
                    "[HIGHSCORE_SCRAPER] Highscore request budget exhausted. Waiting before next request: waitMs={}, maxRequests={}, windowMs={}",
                    Math.max(0L, waitMs),
                    maxRequests,
                    windowMs
            );
        }
    }

    private void sleepMs(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while throttling highscore requests", ex);
        }
    }
}
