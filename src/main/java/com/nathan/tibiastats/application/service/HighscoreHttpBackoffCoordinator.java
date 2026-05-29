package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.infrastructure.persistence.HighscoreScrapeStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class HighscoreHttpBackoffCoordinator {
    private static final Logger log = LoggerFactory.getLogger(HighscoreHttpBackoffCoordinator.class);

    private final HighscoreScrapeStateRepository stateRepository;
    private final AtomicLong globalHttpCooldownUntilMs = new AtomicLong(0);
    private final AtomicLong lastCooldownLogAtMs = new AtomicLong(0);
    private final Object httpBackoffLock = new Object();

    public HighscoreHttpBackoffCoordinator(HighscoreScrapeStateRepository stateRepository) {
        this.stateRepository = stateRepository;
    }

    public HighscoreScrapeStateRepository.HighscoreHttpBackoffState getState() {
        return stateRepository.getHttpBackoffState();
    }

    public HighscoreScrapeStateRepository.HighscoreHttpBackoffState resetManually() {
        stateRepository.resetHttpBackoffAfterSuccess();
        globalHttpCooldownUntilMs.set(0);
        lastCooldownLogAtMs.set(0);
        return stateRepository.getHttpBackoffState();
    }

    public boolean isActive(String planName) {
        HighscoreScrapeStateRepository.HighscoreHttpBackoffState backoff = stateRepository.getHttpBackoffState();
        Instant now = Instant.now();
        if (backoff == null || !backoff.isActive(now)) {
            return false;
        }

        long untilMs = backoff.cooldownUntil() == null ? 0L : backoff.cooldownUntil().toEpochMilli();
        globalHttpCooldownUntilMs.getAndUpdate(value -> Math.max(value, untilMs));
        log.warn(
                "[HIGHSCORE_SCRAPER] Skipping highscore plan because global HTTP backoff is active: plan={}, remainingMs={}, cooldownUntil={}, consecutiveFailures={}, currentCooldownMs={}, lastReason={}",
                planName,
                backoff.remainingMs(now),
                backoff.cooldownUntil(),
                backoff.consecutiveFailures(),
                backoff.currentCooldownMs(),
                backoff.lastReason()
        );
        return true;
    }

    public void resetAfterSuccessfulRun(String planName, int successScopes, int emptyScopes) {
        HighscoreScrapeStateRepository.HighscoreHttpBackoffState backoff = stateRepository.getHttpBackoffState();
        if (backoff == null || (backoff.consecutiveFailures() <= 0 && backoff.cooldownUntil() == null)) {
            return;
        }

        stateRepository.resetHttpBackoffAfterSuccess();
        globalHttpCooldownUntilMs.set(0);
        log.info(
                "[HIGHSCORE_SCRAPER] Global highscore HTTP backoff reset after successful run: plan={}, successScopes={}, emptyScopes={}",
                planName,
                successScopes,
                emptyScopes
        );
    }

    public void awaitCooldown(HighscoreScrapeProperties.Plan plan) {
        while (true) {
            long now = System.currentTimeMillis();
            long until = globalHttpCooldownUntilMs.get();
            long waitMs = until - now;
            if (waitMs <= 0) {
                return;
            }
            logCooldownHeartbeat(plan, waitMs);
            sleepMs(Math.min(waitMs, 1000));
        }
    }

    public void activate(HighscoreScrapeProperties.Plan plan, String reason) {
        long initialCooldownMs = plan.getForbiddenInitialCooldownMs();
        if (initialCooldownMs <= 0) {
            return;
        }

        synchronized (httpBackoffLock) {
            HighscoreScrapeStateRepository.HighscoreHttpBackoffState current = stateRepository.getHttpBackoffState();
            Instant now = Instant.now();
            if (current != null && current.isActive(now)) {
                globalHttpCooldownUntilMs.getAndUpdate(value -> Math.max(value, current.cooldownUntil().toEpochMilli()));
                log.warn(
                        "[HIGHSCORE_SCRAPER] HTTP backoff already active after Tibia response. remainingMs={}, consecutiveFailures={}, currentCooldownMs={}, reason={}",
                        current.remainingMs(now),
                        current.consecutiveFailures(),
                        current.currentCooldownMs(),
                        reason
                );
                return;
            }

            HighscoreScrapeStateRepository.HighscoreHttpBackoffState backoff = stateRepository.activateHttpBackoff(
                    initialCooldownMs,
                    plan.getForbiddenMaxCooldownMs(),
                    plan.getForbiddenCooldownMultiplier(),
                    reason
            );
            long untilMs = backoff.cooldownUntil() == null ? 0L : backoff.cooldownUntil().toEpochMilli();
            globalHttpCooldownUntilMs.getAndUpdate(value -> Math.max(value, untilMs));
            log.warn(
                    "[HIGHSCORE_SCRAPER] Global highscore backoff activated. All highscore plans disabled until cooldown expires: cooldownMs={}, cooldownUntil={}, consecutiveFailures={}, maxCooldownMs={}, multiplier={}, reason={}",
                    backoff.currentCooldownMs(),
                    backoff.cooldownUntil(),
                    backoff.consecutiveFailures(),
                    plan.getForbiddenMaxCooldownMs(),
                    plan.getForbiddenCooldownMultiplier(),
                    reason
            );
        }
    }

    private void logCooldownHeartbeat(HighscoreScrapeProperties.Plan plan, long remainingMs) {
        long now = System.currentTimeMillis();
        long intervalMs = plan.getCooldownLogIntervalMs();
        long lastLog = lastCooldownLogAtMs.get();
        if (intervalMs > 0 && now - lastLog >= intervalMs && lastCooldownLogAtMs.compareAndSet(lastLog, now)) {
            log.info("[HIGHSCORE_SCRAPER] HTTP cooldown still active: remainingMs={}", Math.max(0, remainingMs));
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
            throw new IllegalStateException("Interrupted while waiting during highscore HTTP backoff", ex);
        }
    }
}
