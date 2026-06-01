package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import com.nathan.tibiastats.domain.model.HighscoreStatRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class HighscoreScopeScraper {
    private static final Logger log = LoggerFactory.getLogger(HighscoreScopeScraper.class);
    private static final ZoneId SNAPSHOT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final HighscorePageFetcher pageFetcher;
    private final HighscoreCharacterResolver characterResolver;
    private final HighscoreScopeStateService scopeStateService;
    private final HighscoreFetchRetryPolicy retryPolicy;
    private final HighscoreStatStorageService statStorageService;

    public HighscoreScopeScraper(
            HighscorePageFetcher pageFetcher,
            HighscoreCharacterResolver characterResolver,
            HighscoreScopeStateService scopeStateService,
            HighscoreFetchRetryPolicy retryPolicy,
            HighscoreStatStorageService statStorageService
    ) {
        this.pageFetcher = pageFetcher;
        this.characterResolver = characterResolver;
        this.scopeStateService = scopeStateService;
        this.retryPolicy = retryPolicy;
        this.statStorageService = statStorageService;
    }

    public HighscoreScopeScrapeResult scrape(
            HighscoreScope scope,
            Map<String, Long> characterIdCache,
            ExecutorService executor,
            Semaphore requestSemaphore,
            String planName,
            HighscoreScrapeProperties.Plan plan,
            AtomicBoolean rateLimited
    ) {
        Instant startedAt = Instant.now();
        scopeStateService.markStarted(scope);
        log.info("[HIGHSCORE_SCRAPER] Scope started: plan={}, scope={}", planName, scope.label());

        int pages = 0;
        int rows = 0;
        try {
            LocalDate snapshotDate = LocalDate.now(SNAPSHOT_ZONE);
            Instant scrapedAt = Instant.now();
            int page = 1;
            boolean shouldStop = false;

            while (page <= plan.getMaxPages() && !shouldStop) {
                int windowStart = page;
                int windowEnd = Math.min(plan.getMaxPages(), windowStart + plan.getPageWindowSize() - 1);
                List<Future<HighscorePageFetcher.PageResult>> pageFutures = new ArrayList<>();

                for (int currentPage = windowStart; currentPage <= windowEnd; currentPage++) {
                    int pageToFetch = currentPage;
                    pageFutures.add(executor.submit(() -> pageFetcher.fetchPage(scope, pageToFetch, requestSemaphore, plan, rateLimited)));
                }

                List<HighscorePageFetcher.PageResult> pageResults = collectPageResults(pageFutures);
                pageResults.sort(Comparator.comparingInt(HighscorePageFetcher.PageResult::page));

                List<HighscoreStatRow> windowStatRows = new ArrayList<>();
                for (HighscorePageFetcher.PageResult pageResult : pageResults) {
                    if (pageResult.rows().isEmpty()) {
                        shouldStop = true;
                        break;
                    }

                    pages++;
                    for (var row : pageResult.rows()) {
                        String characterName = characterResolver.normalizeCharacterName(row.name());
                        if (characterName.isBlank()) {
                            continue;
                        }
                        Long characterId = characterResolver.resolveCharacterId(characterName, characterIdCache);
                        windowStatRows.add(new HighscoreStatRow(
                                characterId,
                                scope.worldId(),
                                scope.category(),
                                scope.vocationFilterId(),
                                snapshotDate,
                                row.value(),
                                row.rank(),
                                scrapedAt
                        ));
                    }
                }

                if (!windowStatRows.isEmpty()) {
                    rows += statStorageService.upsertBatch(windowStatRows);
                }

                if (log.isDebugEnabled()) {
                    log.debug(
                            "[HIGHSCORE_SCRAPER] Scope window saved: scope={}, pages={}..{}, rows={}, stopAfterWindow={}",
                            scope.label(),
                            windowStart,
                            windowEnd,
                            windowStatRows.size(),
                            shouldStop
                    );
                }

                page = windowEnd + 1;
            }

            String status = rows > 0 ? "SUCCESS" : "EMPTY";
            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
            scopeStateService.markFinished(scope, status, pages, rows, durationMs, null);
            log.info("[HIGHSCORE_SCRAPER] Scope finished: plan={}, scope={}, status={}, pages={}, rows={}, durationMs={}",
                    planName, scope.label(), status, pages, rows, durationMs);
            return new HighscoreScopeScrapeResult(status, pages, rows);
        } catch (RateLimitedHighscoreException ex) {
            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
            rateLimited.set(true);
            scopeStateService.markFinished(scope, "RATE_LIMITED", pages, rows, durationMs, retryPolicy.rootMessage(ex));
            log.warn("[HIGHSCORE_SCRAPER] Scope rate-limited: plan={}, scope={}, pages={}, rows={}, durationMs={}, error={}",
                    planName, scope.label(), pages, rows, durationMs, retryPolicy.rootMessage(ex));
            return new HighscoreScopeScrapeResult("RATE_LIMITED", pages, rows);
        } catch (Exception ex) {
            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
            scopeStateService.markFinished(scope, "FAILED", pages, rows, durationMs, retryPolicy.rootMessage(ex));
            log.error("[HIGHSCORE_SCRAPER] Scope failed: plan={}, scope={}, pages={}, rows={}, durationMs={}, error={}",
                    planName, scope.label(), pages, rows, durationMs, retryPolicy.rootMessage(ex), ex);
            return new HighscoreScopeScrapeResult("FAILED", pages, rows);
        }
    }

    private List<HighscorePageFetcher.PageResult> collectPageResults(
            List<Future<HighscorePageFetcher.PageResult>> pageFutures
    ) throws Exception {
        List<HighscorePageFetcher.PageResult> pageResults = new ArrayList<>(pageFutures.size());
        for (Future<HighscorePageFetcher.PageResult> pageFuture : pageFutures) {
            try {
                pageResults.add(pageFuture.get());
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof RateLimitedHighscoreException rateLimitedException) {
                    throw rateLimitedException;
                }
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw ex;
            }
        }
        return pageResults;
    }
}

record HighscoreScopeScrapeResult(String status, int pages, int rows) {}
