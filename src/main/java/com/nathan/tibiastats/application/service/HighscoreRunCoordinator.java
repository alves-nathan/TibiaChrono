package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class HighscoreRunCoordinator {
    private static final Logger log = LoggerFactory.getLogger(HighscoreRunCoordinator.class);

    private final HighscoreScopePlanner scopePlanner;
    private final HighscoreHttpBackoffCoordinator httpBackoffCoordinator;
    private final HighscoreScopeWorker scopeWorker;

    public HighscoreRunCoordinator(
            HighscoreScopePlanner scopePlanner,
            HighscoreHttpBackoffCoordinator httpBackoffCoordinator,
            HighscoreScopeWorker scopeWorker
    ) {
        this.scopePlanner = scopePlanner;
        this.httpBackoffCoordinator = httpBackoffCoordinator;
        this.scopeWorker = scopeWorker;
    }

    public ScrapeJobResult run(Instant startedAt, String planName, HighscoreScrapeProperties.Plan plan) {
        HighscoreScopePlanner.HighscoreScopeSelection scopeSelection = scopePlanner.selectScopes(plan);
        if (!scopeSelection.hasWorlds()) {
            log.warn("[HIGHSCORE_SCRAPER] No worlds found. Run the world scraper first.");
            return ScrapeJobResult.empty();
        }

        if (!scopeSelection.hasScopes()) {
            log.info("[HIGHSCORE_SCRAPER] No eligible highscore scopes found.");
            return ScrapeJobResult.empty();
        }

        List<HighscoreScope> scopes = scopeSelection.scopes();

        log.info(
                "[HIGHSCORE_SCRAPER] Starting run: plan={}, selectedScopes={}, scopesPerRun={}, allScopesPerRun={}, scopeWorkers={}, requestParallelism={}, pageWindowSize={}, maxPages={}, pageDelayMs={}, requestMaxAttempts={}, retryBaseDelayMs={}, retryMaxDelayMs={}, forbiddenCooldownMs={}, forbiddenInitialCooldownMs={}, forbiddenMaxCooldownMs={}, forbiddenCooldownMultiplier={}, requestJitterMs={}, requestMinIntervalMs={}, cooldownLogIntervalMs={}, progressLogIntervalScopes={}, worlds={}, categories={}, vocations={}, abortRunOnForbidden={}",
                planName,
                scopes.size(),
                plan.getScopesPerRun(),
                plan.isAllScopesPerRun(),
                Math.min(plan.getParallelism(), scopes.size()),
                plan.getRequestParallelism(),
                plan.getPageWindowSize(),
                plan.getMaxPages(),
                plan.getPageDelayMs(),
                plan.getRequestMaxAttempts(),
                plan.getRetryBaseDelayMs(),
                plan.getRetryMaxDelayMs(),
                plan.getForbiddenCooldownMs(),
                plan.getForbiddenInitialCooldownMs(),
                plan.getForbiddenMaxCooldownMs(),
                plan.getForbiddenCooldownMultiplier(),
                plan.getRequestJitterMs(),
                plan.getRequestMinIntervalMs(),
                plan.getCooldownLogIntervalMs(),
                plan.getProgressLogIntervalScopes(),
                scopeSelection.worldCount(),
                scopeSelection.categoryCount(),
                scopeSelection.vocationCount(),
                plan.isAbortRunOnForbidden()
        );

        Semaphore requestSemaphore = new Semaphore(plan.getRequestParallelism());
        Map<String, Long> characterIdCache = new ConcurrentHashMap<>();
        AtomicInteger nextScopeIndex = new AtomicInteger(0);
        AtomicInteger completedScopes = new AtomicInteger(0);
        AtomicBoolean rateLimited = new AtomicBoolean(false);
        int workerCount = Math.min(plan.getParallelism(), scopes.size());
        List<Future<HighscoreScopeWorkerResult>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int workerId = 1; workerId <= workerCount; workerId++) {
                int currentWorkerId = workerId;
                futures.add(executor.submit(() -> scopeWorker.run(
                        currentWorkerId,
                        scopes,
                        nextScopeIndex,
                        completedScopes,
                        characterIdCache,
                        executor,
                        requestSemaphore,
                        planName,
                        plan,
                        rateLimited
                )));
            }

            int success = 0;
            int empty = 0;
            int failed = 0;
            int totalRows = 0;
            int totalPages = 0;

            for (Future<HighscoreScopeWorkerResult> future : futures) {
                try {
                    HighscoreScopeWorkerResult result = future.get();
                    success += result.successScopes();
                    empty += result.emptyScopes();
                    failed += result.failedScopes();
                    totalRows += result.rows();
                    totalPages += result.pages();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    failed++;
                    log.warn("[HIGHSCORE_SCRAPER] Run interrupted while waiting for worker results", ex);
                    break;
                } catch (Exception ex) {
                    failed++;
                    log.error("[HIGHSCORE_SCRAPER] Unexpected highscore worker failure", ex);
                }
            }

            if (!rateLimited.get() && (success > 0 || empty > 0)) {
                httpBackoffCoordinator.resetAfterSuccessfulRun(planName, success, empty);
            }

            log.info(
                    "[HIGHSCORE_SCRAPER] Finished run: plan={}, successScopes={}, emptyScopes={}, failedScopes={}, completedScopes={}, selectedScopes={}, pages={}, rows={}, durationMs={}, cacheSize={}, rateLimited={}",
                    planName,
                    success,
                    empty,
                    failed,
                    completedScopes.get(),
                    scopes.size(),
                    totalPages,
                    totalRows,
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    characterIdCache.size(),
                    rateLimited.get()
            );
            return ScrapeJobResult.of(success + empty + failed, 0, totalRows, failed);
        }
    }
}
