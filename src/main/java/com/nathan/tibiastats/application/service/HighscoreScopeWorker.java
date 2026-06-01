package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class HighscoreScopeWorker {
    private static final Logger log = LoggerFactory.getLogger(HighscoreScopeWorker.class);

    private final HighscoreScopeScraper scopeScraper;

    public HighscoreScopeWorker(HighscoreScopeScraper scopeScraper) {
        this.scopeScraper = scopeScraper;
    }

    public HighscoreScopeWorkerResult run(
            int workerId,
            List<HighscoreScope> scopes,
            AtomicInteger nextScopeIndex,
            AtomicInteger completedScopes,
            Map<String, Long> characterIdCache,
            ExecutorService executor,
            Semaphore requestSemaphore,
            String planName,
            HighscoreScrapeProperties.Plan plan,
            AtomicBoolean rateLimited
    ) {
        int success = 0;
        int empty = 0;
        int failed = 0;
        int rows = 0;
        int pages = 0;

        while (!Thread.currentThread().isInterrupted() && !rateLimited.get()) {
            int index = nextScopeIndex.getAndIncrement();
            if (index >= scopes.size()) {
                break;
            }

            HighscoreScope scope = scopes.get(index);
            HighscoreScopeScrapeResult result = scopeScraper.scrape(
                    scope,
                    characterIdCache,
                    executor,
                    requestSemaphore,
                    planName,
                    plan,
                    rateLimited
            );
            rows += result.rows();
            pages += result.pages();
            if ("SUCCESS".equals(result.status())) {
                success++;
            } else if ("EMPTY".equals(result.status())) {
                empty++;
            } else {
                failed++;
            }

            int done = completedScopes.incrementAndGet();
            if (done == scopes.size() || done % plan.getProgressLogIntervalScopes() == 0) {
                log.info(
                        "[HIGHSCORE_SCRAPER] Run progress: plan={}, completedScopes={}/{}, workerId={}, lastScope={}, lastStatus={}, pagesSoFar={}, rowsSoFar={}, rateLimited={}",
                        planName,
                        done,
                        scopes.size(),
                        workerId,
                        scope.label(),
                        result.status(),
                        pages,
                        rows,
                        rateLimited.get()
                );
            }
        }

        return new HighscoreScopeWorkerResult(success, empty, failed, pages, rows);
    }
}

record HighscoreScopeWorkerResult(int successScopes, int emptyScopes, int failedScopes, int pages, int rows) {}
