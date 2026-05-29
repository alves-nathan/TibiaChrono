#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
if [ ! -f "$ROOT/pom.xml" ] || [ ! -d "$ROOT/src/main/java" ]; then
  echo "Run this script from the TibiaChrono project root." >&2
  exit 1
fi

BACKUP_DIR="$ROOT/.tibiachrono-highscore-safe-budget-v3-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"

backup_file() {
  local path="$1"
  if [ -f "$ROOT/$path" ]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$path")"
    cp "$ROOT/$path" "$BACKUP_DIR/$path"
  fi
}

write_file() {
  local path="$1"
  mkdir -p "$ROOT/$(dirname "$path")"
  cat > "$ROOT/$path"
}

backup_file "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java"
backup_file "src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java"
backup_file "src/main/resources/application.yml"
backup_file "src/main/resources/application-dev.yml"
backup_file "src/test/java/com/nathan/tibiastats/config/HighscorePlanConfigurationTest.java"

write_file "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java" <<'EOF_TIBIACHRONO_V3_0'
package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.HighscorePort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import com.nathan.tibiastats.infrastructure.persistence.HighscoreScrapeStateRepository;
import com.nathan.tibiastats.infrastructure.persistence.HighscoreStatRecordWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Service
public class HighscoreService {
    private static final Logger log = LoggerFactory.getLogger(HighscoreService.class);
    private static final ZoneId SNAPSHOT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Pattern TRADED_TAG = Pattern.compile("\\s*\\(\\s*traded\\s*\\)\\s*$", Pattern.CASE_INSENSITIVE);

    private final HighscorePort highscorePort;
    private final WorldRepositoryPort worldRepository;
    private final CharacterNamingService namingService;
    private final HighscoreScrapeProperties properties;
    private final HighscoreScrapeStateRepository stateRepository;
    private final HighscoreStatRecordWriter statRecordWriter;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong globalHttpCooldownUntilMs = new AtomicLong(0);
    private final AtomicLong nextAllowedHttpRequestAtMs = new AtomicLong(0);
    private final Object requestBudgetLock = new Object();
    private final ArrayDeque<Long> recentHighscoreRequestStarts = new ArrayDeque<>();
    private final AtomicLong lastRequestBudgetLogAtMs = new AtomicLong(0);
    private final AtomicLong lastCooldownLogAtMs = new AtomicLong(0);
    private final AtomicLong lastRetrySleepLogAtMs = new AtomicLong(0);
    private final Object httpBackoffLock = new Object();
    private final Map<String, Object> nameLocks = new ConcurrentHashMap<>();

    public HighscoreService(
            HighscorePort highscorePort,
            WorldRepositoryPort worldRepository,
            CharacterNamingService namingService,
            HighscoreScrapeProperties properties,
            HighscoreScrapeStateRepository stateRepository,
            HighscoreStatRecordWriter statRecordWriter
    ) {
        this.highscorePort = highscorePort;
        this.worldRepository = worldRepository;
        this.namingService = namingService;
        this.properties = properties;
        this.stateRepository = stateRepository;
        this.statRecordWriter = statRecordWriter;
    }

    public ScrapeJobResult updateAllHighscores() {
        return updateHighscores("default", properties.toLegacyPlan());
    }

    public ScrapeJobResult updateHighscores(String planName, HighscoreScrapeProperties.Plan plan) {
        if (!properties.isEnabled()) {
            log.info("[HIGHSCORE_SCRAPER] Skipping run because highscores.enabled=false: plan={}", planName);
            return ScrapeJobResult.empty();
        }
        if (plan == null || !plan.isEnabled()) {
            log.info("[HIGHSCORE_SCRAPER] Skipping disabled highscore plan: plan={}", planName);
            return ScrapeJobResult.empty();
        }

        if (isHttpBackoffActive(planName)) {
            return ScrapeJobResult.empty();
        }

        if (!running.compareAndSet(false, true)) {
            log.warn("[HIGHSCORE_SCRAPER] Previous highscore run is still active. Skipping this tick: plan={}", planName);
            return ScrapeJobResult.empty();
        }

        Instant startedAt = Instant.now();
        try {
            return runIncrementalHighscoreScrape(startedAt, planName, plan);
        } finally {
            running.set(false);
        }
    }

    public HighscoreScrapeStateRepository.HighscoreHttpBackoffState getHttpBackoffState() {
        return stateRepository.getHttpBackoffState();
    }

    public HighscoreScrapeStateRepository.HighscoreHttpBackoffState resetHttpBackoffManually() {
        stateRepository.resetHttpBackoffAfterSuccess();
        globalHttpCooldownUntilMs.set(0);
        lastCooldownLogAtMs.set(0);
        return stateRepository.getHttpBackoffState();
    }

    public boolean isRunning() {
        return running.get();
    }

    private ScrapeJobResult runIncrementalHighscoreScrape(Instant startedAt, String planName, HighscoreScrapeProperties.Plan plan) {
        List<World> worlds = worldRepository.findAll().stream()
                .sorted(Comparator.comparing(World::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(plan.getWorldLimit() > 0 ? plan.getWorldLimit() : Long.MAX_VALUE)
                .toList();
        List<StatCategory> categories = plan.categoryList();
        List<Integer> vocationFilterIds = plan.vocationFilterIds();

        if (worlds.isEmpty()) {
            log.warn("[HIGHSCORE_SCRAPER] No worlds found. Run the world scraper first.");
            return ScrapeJobResult.empty();
        }

        stateRepository.registerScopes(worlds, categories, vocationFilterIds);
        List<HighscoreScope> scopes = stateRepository.findNextScopes(
                worlds,
                categories,
                vocationFilterIds,
                plan.getScopesPerRun()
        );

        if (scopes.isEmpty()) {
            log.info("[HIGHSCORE_SCRAPER] No eligible highscore scopes found.");
            return ScrapeJobResult.empty();
        }

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
                worlds.size(),
                categories.size(),
                vocationFilterIds.size(),
                plan.isAbortRunOnForbidden()
        );

        Semaphore requestSemaphore = new Semaphore(plan.getRequestParallelism());
        Map<String, Long> characterIdCache = new ConcurrentHashMap<>();
        AtomicInteger nextScopeIndex = new AtomicInteger(0);
        AtomicInteger completedScopes = new AtomicInteger(0);
        AtomicBoolean rateLimited = new AtomicBoolean(false);
        int workerCount = Math.min(plan.getParallelism(), scopes.size());
        List<Future<WorkerResult>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int workerId = 1; workerId <= workerCount; workerId++) {
                int currentWorkerId = workerId;
                futures.add(executor.submit(() -> runScopeWorker(
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

            for (Future<WorkerResult> future : futures) {
                try {
                    WorkerResult result = future.get();
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
                resetHttpBackoffAfterSuccessfulRun(planName, success, empty);
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

    private WorkerResult runScopeWorker(
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
            ScopeResult result = scrapeScope(scope, characterIdCache, executor, requestSemaphore, planName, plan, rateLimited);
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

        return new WorkerResult(success, empty, failed, pages, rows);
    }

    private ScopeResult scrapeScope(
            HighscoreScope scope,
            Map<String, Long> characterIdCache,
            ExecutorService executor,
            Semaphore requestSemaphore,
            String planName,
            HighscoreScrapeProperties.Plan plan,
            AtomicBoolean rateLimited
    ) {
        Instant startedAt = Instant.now();
        stateRepository.markStarted(scope);
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
                List<Future<PageResult>> pageFutures = new ArrayList<>();

                for (int currentPage = windowStart; currentPage <= windowEnd; currentPage++) {
                    int pageToFetch = currentPage;
                    pageFutures.add(executor.submit(() -> fetchPage(scope, pageToFetch, requestSemaphore, plan, rateLimited)));
                }

                List<PageResult> pageResults = new ArrayList<>(pageFutures.size());
                for (Future<PageResult> pageFuture : pageFutures) {
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
                pageResults.sort(Comparator.comparingInt(PageResult::page));

                List<HighscoreStatRecordWriter.HighscoreStatRow> windowStatRows = new ArrayList<>();
                for (PageResult pageResult : pageResults) {
                    if (pageResult.rows().isEmpty()) {
                        shouldStop = true;
                        break;
                    }

                    pages++;
                    for (HighscorePort.HighscoreRow row : pageResult.rows()) {
                        String normalizedName = normalizeCharacterName(row.name());
                        if (normalizedName.isBlank()) {
                            continue;
                        }
                        Long characterId = resolveCharacterId(normalizedName, characterIdCache);
                        windowStatRows.add(new HighscoreStatRecordWriter.HighscoreStatRow(
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
                    rows += statRecordWriter.upsertBatch(windowStatRows);
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
            stateRepository.markFinished(scope, status, pages, rows, durationMs, null);
            log.info("[HIGHSCORE_SCRAPER] Scope finished: plan={}, scope={}, status={}, pages={}, rows={}, durationMs={}",
                    planName, scope.label(), status, pages, rows, durationMs);
            return new ScopeResult(status, pages, rows);
        } catch (RateLimitedHighscoreException ex) {
            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
            rateLimited.set(true);
            stateRepository.markFinished(scope, "RATE_LIMITED", pages, rows, durationMs, rootMessage(ex));
            log.warn("[HIGHSCORE_SCRAPER] Scope rate-limited: plan={}, scope={}, pages={}, rows={}, durationMs={}, error={}",
                    planName, scope.label(), pages, rows, durationMs, rootMessage(ex));
            return new ScopeResult("RATE_LIMITED", pages, rows);
        } catch (Exception ex) {
            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
            stateRepository.markFinished(scope, "FAILED", pages, rows, durationMs, rootMessage(ex));
            log.error("[HIGHSCORE_SCRAPER] Scope failed: plan={}, scope={}, pages={}, rows={}, durationMs={}, error={}",
                    planName, scope.label(), pages, rows, durationMs, rootMessage(ex), ex);
            return new ScopeResult("FAILED", pages, rows);
        }
    }

    private PageResult fetchPage(HighscoreScope scope, int page, Semaphore requestSemaphore, HighscoreScrapeProperties.Plan plan, AtomicBoolean rateLimited) throws InterruptedException {
        int maxAttempts = plan.getRequestMaxAttempts();
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (rateLimited.get()) {
                throw new RateLimitedHighscoreException("Highscore run already marked as rate-limited");
            }
            requestSemaphore.acquire();
            try {
                awaitGlobalHttpCooldown(plan);
                awaitGlobalRequestPace(plan);
                throttleRequestWithJitter(plan);
                awaitGlobalRequestBudget(plan);
                List<HighscorePort.HighscoreRow> rows = highscorePort.fetchHighscores(
                        scope.worldName(),
                        scope.category(),
                        scope.vocationFilterId(),
                        page
                );
                return new PageResult(page, rows);
            } catch (RuntimeException ex) {
                lastFailure = ex;
                boolean transientFailure = isTransientHighscoreFetchFailure(ex);
                boolean shouldRetry = transientFailure && attempt < maxAttempts;

                if (isForbiddenOrRateLimited(ex)) {
                    activateGlobalHttpCooldown(plan, rootMessage(ex));
                    if (plan.isAbortRunOnForbidden()) {
                        rateLimited.set(true);
                        throw new RateLimitedHighscoreException(rootMessage(ex), ex);
                    }
                }

                if (!shouldRetry) {
                    throw ex;
                }

                long retryDelayMs = retryDelayMs(plan, attempt, ex);
                log.warn(
                        "[HIGHSCORE_SCRAPER] Transient page fetch failure. Retrying: scope={}, page={}, attempt={}/{}, delayMs={}, error={}",
                        scope.label(),
                        page,
                        attempt,
                        maxAttempts,
                        retryDelayMs,
                        rootMessage(ex)
                );
                sleepWithRetryHeartbeat(plan, retryDelayMs, scope, page, attempt, maxAttempts);
            } finally {
                requestSemaphore.release();
            }
        }

        throw lastFailure == null
                ? new IllegalStateException("Failed to fetch highscore page after retries")
                : lastFailure;
    }

    private Long resolveCharacterId(String characterName, Map<String, Long> characterIdCache) {
        String key = normalizeLookupKey(characterName);
        return characterIdCache.computeIfAbsent(key, ignored -> {
            Object lock = nameLocks.computeIfAbsent(key, unused -> new Object());
            synchronized (lock) {
                CharacterEntity character = namingService.ensureCharacterForName(characterName, characterName);
                if (character.getId() == null) {
                    throw new IllegalStateException("Character was resolved without id: " + characterName);
                }
                return character.getId();
            }
        });
    }

    private String normalizeCharacterName(String value) {
        if (value == null) {
            return "";
        }
        return TRADED_TAG.matcher(value).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    private String normalizeLookupKey(String value) {
        String cleaned = normalizeCharacterName(value).toLowerCase(Locale.ROOT);
        return Normalizer.normalize(cleaned, Normalizer.Form.NFKC);
    }


    private boolean isHttpBackoffActive(String planName) {
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

    private void resetHttpBackoffAfterSuccessfulRun(String planName, int successScopes, int emptyScopes) {
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

    /**
     * Hard cap for highscore HTTP request starts in a rolling window.
     *
     * <p>The request semaphore limits concurrency and {@link #awaitGlobalRequestPace(HighscoreScrapeProperties.Plan)}
     * spaces out bursts, but neither one alone protects against unsafe aggregate volume when a full highscore run spans
     * many worlds, categories, vocations and pages. This in-memory budget is intentionally global to every highscore
     * plan handled by this JVM and defaults to the external safety ceiling of 150,000 requests per 10 minutes.</p>
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

    private void awaitGlobalHttpCooldown(HighscoreScrapeProperties.Plan plan) {
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

    private void activateGlobalHttpCooldown(HighscoreScrapeProperties.Plan plan, String reason) {
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

    private void sleepWithRetryHeartbeat(HighscoreScrapeProperties.Plan plan, long delayMs, HighscoreScope scope, int page, int attempt, int maxAttempts) {
        if (delayMs <= 0) {
            return;
        }

        long deadline = System.currentTimeMillis() + delayMs;
        while (true) {
            long now = System.currentTimeMillis();
            long remainingMs = deadline - now;
            if (remainingMs <= 0) {
                return;
            }

            long intervalMs = plan.getCooldownLogIntervalMs();
            long lastLog = lastRetrySleepLogAtMs.get();
            if (intervalMs > 0 && now - lastLog >= intervalMs && lastRetrySleepLogAtMs.compareAndSet(lastLog, now)) {
                log.info(
                        "[HIGHSCORE_SCRAPER] Waiting before retry: scope={}, page={}, attempt={}/{}, remainingMs={}",
                        scope.label(),
                        page,
                        attempt,
                        maxAttempts,
                        remainingMs
                );
            }
            sleepMs(Math.min(remainingMs, 1000));
        }
    }

    private long retryDelayMs(HighscoreScrapeProperties.Plan plan, int attempt, RuntimeException ex) {
        long base = Math.max(0, plan.getRetryBaseDelayMs());
        long max = Math.max(base, plan.getRetryMaxDelayMs());
        long exponential = base <= 0 ? 0 : base * (1L << Math.min(attempt - 1, 5));
        long delay = Math.min(max, exponential);

        if (isForbiddenOrRateLimited(ex)) {
            delay = Math.max(delay, Math.min(max, plan.getForbiddenInitialCooldownMs()));
        }

        int jitter = plan.getRequestJitterMs();
        if (jitter > 0) {
            delay += ThreadLocalRandom.current().nextInt(jitter + 1);
        }
        return delay;
    }

    private boolean isTransientHighscoreFetchFailure(Throwable throwable) {
        String message = rootMessage(throwable).toLowerCase(Locale.ROOT);
        return message.contains("http 403")
                || message.contains("http 429")
                || message.contains("http 500")
                || message.contains("http 502")
                || message.contains("http 503")
                || message.contains("http 504")
                || message.contains("timed out")
                || message.contains("timeout")
                || message.contains("connection reset")
                || message.contains("connection refused");
    }

    private boolean isForbiddenOrRateLimited(Throwable throwable) {
        String message = rootMessage(throwable).toLowerCase(Locale.ROOT);
        return message.contains("http 403") || message.contains("http 429");
    }

    private void sleepMs(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting during highscore scrape", ex);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    private static class RateLimitedHighscoreException extends RuntimeException {
        RateLimitedHighscoreException(String message) {
            super(message);
        }

        RateLimitedHighscoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record ScopeResult(String status, int pages, int rows) {}
    private record PageResult(int page, List<HighscorePort.HighscoreRow> rows) {}
    private record WorkerResult(int successScopes, int emptyScopes, int failedScopes, int pages, int rows) {}
}
EOF_TIBIACHRONO_V3_0

write_file "src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java" <<'EOF_TIBIACHRONO_V3_1'
package com.nathan.tibiastats.config;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "tibiastats.scrape.highscores")
public class HighscoreScrapeProperties {
    private static final Logger log = LoggerFactory.getLogger(HighscoreScrapeProperties.class);
    private static final int HIGHSCORE_REQUEST_BUDGET_HARD_LIMIT = 150_000;
    private static final long HIGHSCORE_REQUEST_BUDGET_WINDOW_MS = 600_000L;

    private boolean enabled = true;
    private String cron = "0 0 7 * * *";
    private String zone = "America/Sao_Paulo";
    private boolean runOnStartup = false;
    private long startupDelayMs = 0;
    private String categories = "EXPERIENCE";
    private String vocations = "0";
    private int maxPages = 100;
    private int pageDelayMs = 0;
    private int worldLimit = 0;
    private int scopesPerRun = 0;
    private int parallelism = 4;
    private int pageWindowSize = 1;
    private int requestParallelism = 4;
    private int requestMaxAttempts = 1;
    private int retryBaseDelayMs = 5000;
    private int retryMaxDelayMs = 300000;
    /**
     * Deprecated compatibility field. Use forbiddenInitialCooldownMs/forbiddenMaxCooldownMs instead.
     */
    private int forbiddenCooldownMs = 259200000;
    private long forbiddenInitialCooldownMs = 259200000L; // 72h
    private long forbiddenMaxCooldownMs = 1209600000L; // 14d
    private double forbiddenCooldownMultiplier = 2.0D;
    private int requestJitterMs = 300;
    private int requestMinIntervalMs = 750;
    private int requestBudgetMaxRequests = HIGHSCORE_REQUEST_BUDGET_HARD_LIMIT;
    private long requestBudgetWindowMs = HIGHSCORE_REQUEST_BUDGET_WINDOW_MS;
    private int cooldownLogIntervalMs = 30000;
    private int progressLogIntervalScopes = 10;
    private boolean abortRunOnForbidden = true;
    private Map<String, Plan> plans = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getZone() {
        return normalizedZone(zone);
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public boolean isRunOnStartup() {
        return runOnStartup;
    }

    public void setRunOnStartup(boolean runOnStartup) {
        this.runOnStartup = runOnStartup;
    }

    public long getStartupDelayMs() {
        return Math.max(0, startupDelayMs);
    }

    public void setStartupDelayMs(long startupDelayMs) {
        this.startupDelayMs = startupDelayMs;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }

    public String getVocations() {
        return vocations;
    }

    public void setVocations(String vocations) {
        this.vocations = vocations;
    }

    public int getMaxPages() {
        return Math.max(1, maxPages);
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public int getPageDelayMs() {
        return Math.max(0, pageDelayMs);
    }

    public void setPageDelayMs(int pageDelayMs) {
        this.pageDelayMs = pageDelayMs;
    }

    public int getWorldLimit() {
        return Math.max(0, worldLimit);
    }

    public void setWorldLimit(int worldLimit) {
        this.worldLimit = worldLimit;
    }

    /**
     * Maximum number of highscore scopes processed in one scheduled run.
     * A value of 0 means: process every configured scope in the same run.
     */
    public int getScopesPerRun() {
        return Math.max(0, scopesPerRun);
    }

    public boolean isAllScopesPerRun() {
        return getScopesPerRun() == 0;
    }

    public void setScopesPerRun(int scopesPerRun) {
        this.scopesPerRun = scopesPerRun;
    }

    public int getParallelism() {
        return Math.max(1, parallelism);
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    public int getPageWindowSize() {
        return Math.max(1, pageWindowSize);
    }

    public void setPageWindowSize(int pageWindowSize) {
        this.pageWindowSize = pageWindowSize;
    }

    public int getRequestParallelism() {
        return Math.max(1, requestParallelism);
    }

    public void setRequestParallelism(int requestParallelism) {
        this.requestParallelism = requestParallelism;
    }

    public int getRequestMaxAttempts() {
        return Math.max(1, requestMaxAttempts);
    }

    public void setRequestMaxAttempts(int requestMaxAttempts) {
        this.requestMaxAttempts = requestMaxAttempts;
    }

    public int getRetryBaseDelayMs() {
        return Math.max(0, retryBaseDelayMs);
    }

    public void setRetryBaseDelayMs(int retryBaseDelayMs) {
        this.retryBaseDelayMs = retryBaseDelayMs;
    }

    public int getRetryMaxDelayMs() {
        return Math.max(getRetryBaseDelayMs(), retryMaxDelayMs);
    }

    public void setRetryMaxDelayMs(int retryMaxDelayMs) {
        this.retryMaxDelayMs = retryMaxDelayMs;
    }

    public int getForbiddenCooldownMs() {
        return Math.max(0, forbiddenCooldownMs);
    }

    public void setForbiddenCooldownMs(int forbiddenCooldownMs) {
        this.forbiddenCooldownMs = forbiddenCooldownMs;
        if (forbiddenInitialCooldownMs <= 0) {
            this.forbiddenInitialCooldownMs = forbiddenCooldownMs;
        }
    }

    public long getForbiddenInitialCooldownMs() {
        return Math.max(0L, forbiddenInitialCooldownMs);
    }

    public void setForbiddenInitialCooldownMs(long forbiddenInitialCooldownMs) {
        this.forbiddenInitialCooldownMs = forbiddenInitialCooldownMs;
    }

    public long getForbiddenMaxCooldownMs() {
        return Math.max(getForbiddenInitialCooldownMs(), forbiddenMaxCooldownMs);
    }

    public void setForbiddenMaxCooldownMs(long forbiddenMaxCooldownMs) {
        this.forbiddenMaxCooldownMs = forbiddenMaxCooldownMs;
    }

    public double getForbiddenCooldownMultiplier() {
        return forbiddenCooldownMultiplier < 1.0D ? 1.0D : forbiddenCooldownMultiplier;
    }

    public void setForbiddenCooldownMultiplier(double forbiddenCooldownMultiplier) {
        this.forbiddenCooldownMultiplier = forbiddenCooldownMultiplier;
    }

    public int getRequestJitterMs() {
        return Math.max(0, requestJitterMs);
    }

    public void setRequestJitterMs(int requestJitterMs) {
        this.requestJitterMs = requestJitterMs;
    }

    public int getRequestMinIntervalMs() {
        return Math.max(0, requestMinIntervalMs);
    }

    public void setRequestMinIntervalMs(int requestMinIntervalMs) {
        this.requestMinIntervalMs = requestMinIntervalMs;
    }

    public int getRequestBudgetMaxRequests() {
        if (requestBudgetMaxRequests <= 0) {
            return HIGHSCORE_REQUEST_BUDGET_HARD_LIMIT;
        }
        return Math.min(requestBudgetMaxRequests, HIGHSCORE_REQUEST_BUDGET_HARD_LIMIT);
    }

    public void setRequestBudgetMaxRequests(int requestBudgetMaxRequests) {
        this.requestBudgetMaxRequests = requestBudgetMaxRequests;
    }

    public long getRequestBudgetWindowMs() {
        if (requestBudgetWindowMs <= 0) {
            return HIGHSCORE_REQUEST_BUDGET_WINDOW_MS;
        }
        return Math.max(requestBudgetWindowMs, HIGHSCORE_REQUEST_BUDGET_WINDOW_MS);
    }

    public void setRequestBudgetWindowMs(long requestBudgetWindowMs) {
        this.requestBudgetWindowMs = requestBudgetWindowMs;
    }

    public int getCooldownLogIntervalMs() {
        return Math.max(1000, cooldownLogIntervalMs);
    }

    public void setCooldownLogIntervalMs(int cooldownLogIntervalMs) {
        this.cooldownLogIntervalMs = cooldownLogIntervalMs;
    }

    public int getProgressLogIntervalScopes() {
        return Math.max(1, progressLogIntervalScopes);
    }

    public void setProgressLogIntervalScopes(int progressLogIntervalScopes) {
        this.progressLogIntervalScopes = progressLogIntervalScopes;
    }

    public boolean isAbortRunOnForbidden() {
        return abortRunOnForbidden;
    }

    public void setAbortRunOnForbidden(boolean abortRunOnForbidden) {
        this.abortRunOnForbidden = abortRunOnForbidden;
    }

    public Map<String, Plan> getPlans() {
        return plans;
    }

    public void setPlans(Map<String, Plan> plans) {
        this.plans = plans == null ? new LinkedHashMap<>() : new LinkedHashMap<>(plans);
    }

    /**
     * Legacy single-plan view. Used when the new highscores.plans map is not configured.
     */
    public Plan toLegacyPlan() {
        Plan plan = new Plan();
        plan.setEnabled(enabled);
        plan.setCron(cron);
        plan.setZone(zone);
        plan.setRunOnStartup(runOnStartup);
        plan.setStartupDelayMs(startupDelayMs);
        plan.setCategories(categories);
        plan.setVocations(vocations);
        plan.setMaxPages(maxPages);
        plan.setPageDelayMs(pageDelayMs);
        plan.setWorldLimit(worldLimit);
        plan.setScopesPerRun(scopesPerRun);
        plan.setParallelism(parallelism);
        plan.setPageWindowSize(pageWindowSize);
        plan.setRequestParallelism(requestParallelism);
        plan.setRequestMaxAttempts(requestMaxAttempts);
        plan.setRetryBaseDelayMs(retryBaseDelayMs);
        plan.setRetryMaxDelayMs(retryMaxDelayMs);
        plan.setForbiddenCooldownMs(forbiddenCooldownMs);
        plan.setForbiddenInitialCooldownMs(forbiddenInitialCooldownMs);
        plan.setForbiddenMaxCooldownMs(forbiddenMaxCooldownMs);
        plan.setForbiddenCooldownMultiplier(forbiddenCooldownMultiplier);
        plan.setRequestJitterMs(requestJitterMs);
        plan.setRequestMinIntervalMs(requestMinIntervalMs);
        plan.setRequestBudgetMaxRequests(requestBudgetMaxRequests);
        plan.setRequestBudgetWindowMs(requestBudgetWindowMs);
        plan.setCooldownLogIntervalMs(cooldownLogIntervalMs);
        plan.setProgressLogIntervalScopes(progressLogIntervalScopes);
        plan.setAbortRunOnForbidden(abortRunOnForbidden);
        return plan;
    }

    public Map<String, Plan> effectivePlans() {
        if (plans == null || plans.isEmpty()) {
            Map<String, Plan> legacy = new LinkedHashMap<>();
            legacy.put("default", toLegacyPlan());
            return legacy;
        }
        return plans;
    }

    public List<StatCategory> categoryList() {
        return parseCategories(categories);
    }

    public List<Integer> vocationFilterIds() {
        return parseVocations(vocations);
    }

    private static List<StatCategory> parseCategories(String value) {
        List<StatCategory> parsed = new ArrayList<>();
        for (String token : splitCsv(value)) {
            try {
                parsed.add(StatCategory.valueOf(token));
            } catch (IllegalArgumentException ex) {
                log.warn("[HIGHSCORE_SCRAPER] Ignoring invalid highscore category config: {}", token);
            }
        }
        return parsed.isEmpty() ? List.of(StatCategory.EXPERIENCE) : parsed;
    }

    private static List<Integer> parseVocations(String value) {
        Set<Integer> parsed = new LinkedHashSet<>();
        for (String token : splitCsv(value)) {
            try {
                parsed.add(Integer.parseInt(token));
            } catch (NumberFormatException ex) {
                log.warn("[HIGHSCORE_SCRAPER] Ignoring invalid highscore vocation config: {}", token);
            }
        }
        return parsed.isEmpty() ? List.of(0) : new ArrayList<>(parsed);
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String raw : value.split(",")) {
            String token = raw.trim().toUpperCase();
            if (!token.isBlank()) {
                out.add(token);
            }
        }
        return out;
    }

    private static String normalizedZone(String value) {
        return (value == null || value.isBlank()) ? "America/Sao_Paulo" : value;
    }

    public static class Plan {
        private boolean enabled = true;
        private String cron = "0 0 7 * * *";
        private String zone = "America/Sao_Paulo";
        private boolean runOnStartup = false;
        private long startupDelayMs = 0;
        private String categories = "EXPERIENCE";
        private String vocations = "0";
        private int maxPages = 100;
        private int pageDelayMs = 0;
        private int worldLimit = 0;
        private int scopesPerRun = 0;
        private int parallelism = 4;
        private int pageWindowSize = 1;
        private int requestParallelism = 4;
        private int requestMaxAttempts = 1;
        private int retryBaseDelayMs = 5000;
        private int retryMaxDelayMs = 300000;
        /**
         * Deprecated compatibility field. Use forbiddenInitialCooldownMs/forbiddenMaxCooldownMs instead.
         */
        private int forbiddenCooldownMs = 259200000;
        private long forbiddenInitialCooldownMs = 259200000L; // 72h
        private long forbiddenMaxCooldownMs = 1209600000L; // 14d
        private double forbiddenCooldownMultiplier = 2.0D;
        private int requestJitterMs = 300;
        private int requestMinIntervalMs = 750;
        private int requestBudgetMaxRequests = HIGHSCORE_REQUEST_BUDGET_HARD_LIMIT;
        private long requestBudgetWindowMs = HIGHSCORE_REQUEST_BUDGET_WINDOW_MS;
        private int cooldownLogIntervalMs = 30000;
        private int progressLogIntervalScopes = 10;
        private boolean abortRunOnForbidden = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
        public String getZone() { return normalizedZone(zone); }
        public void setZone(String zone) { this.zone = zone; }
        public boolean isRunOnStartup() { return runOnStartup; }
        public void setRunOnStartup(boolean runOnStartup) { this.runOnStartup = runOnStartup; }
        public long getStartupDelayMs() { return Math.max(0, startupDelayMs); }
        public void setStartupDelayMs(long startupDelayMs) { this.startupDelayMs = startupDelayMs; }
        public String getCategories() { return categories; }
        public void setCategories(String categories) { this.categories = categories; }
        public String getVocations() { return vocations; }
        public void setVocations(String vocations) { this.vocations = vocations; }
        public int getMaxPages() { return Math.max(1, maxPages); }
        public void setMaxPages(int maxPages) { this.maxPages = maxPages; }
        public int getPageDelayMs() { return Math.max(0, pageDelayMs); }
        public void setPageDelayMs(int pageDelayMs) { this.pageDelayMs = pageDelayMs; }
        public int getWorldLimit() { return Math.max(0, worldLimit); }
        public void setWorldLimit(int worldLimit) { this.worldLimit = worldLimit; }
        public int getScopesPerRun() { return Math.max(0, scopesPerRun); }
        public boolean isAllScopesPerRun() { return getScopesPerRun() == 0; }
        public void setScopesPerRun(int scopesPerRun) { this.scopesPerRun = scopesPerRun; }
        public int getParallelism() { return Math.max(1, parallelism); }
        public void setParallelism(int parallelism) { this.parallelism = parallelism; }
        public int getPageWindowSize() { return Math.max(1, pageWindowSize); }
        public void setPageWindowSize(int pageWindowSize) { this.pageWindowSize = pageWindowSize; }
        public int getRequestParallelism() { return Math.max(1, requestParallelism); }
        public void setRequestParallelism(int requestParallelism) { this.requestParallelism = requestParallelism; }
        public int getRequestMaxAttempts() { return Math.max(1, requestMaxAttempts); }
        public void setRequestMaxAttempts(int requestMaxAttempts) { this.requestMaxAttempts = requestMaxAttempts; }
        public int getRetryBaseDelayMs() { return Math.max(0, retryBaseDelayMs); }
        public void setRetryBaseDelayMs(int retryBaseDelayMs) { this.retryBaseDelayMs = retryBaseDelayMs; }
        public int getRetryMaxDelayMs() { return Math.max(getRetryBaseDelayMs(), retryMaxDelayMs); }
        public void setRetryMaxDelayMs(int retryMaxDelayMs) { this.retryMaxDelayMs = retryMaxDelayMs; }
        public int getForbiddenCooldownMs() { return Math.max(0, forbiddenCooldownMs); }
        public void setForbiddenCooldownMs(int forbiddenCooldownMs) {
            this.forbiddenCooldownMs = forbiddenCooldownMs;
            if (forbiddenInitialCooldownMs <= 0) {
                this.forbiddenInitialCooldownMs = forbiddenCooldownMs;
            }
        }
        public long getForbiddenInitialCooldownMs() { return Math.max(0L, forbiddenInitialCooldownMs); }
        public void setForbiddenInitialCooldownMs(long forbiddenInitialCooldownMs) { this.forbiddenInitialCooldownMs = forbiddenInitialCooldownMs; }
        public long getForbiddenMaxCooldownMs() { return Math.max(getForbiddenInitialCooldownMs(), forbiddenMaxCooldownMs); }
        public void setForbiddenMaxCooldownMs(long forbiddenMaxCooldownMs) { this.forbiddenMaxCooldownMs = forbiddenMaxCooldownMs; }
        public double getForbiddenCooldownMultiplier() { return forbiddenCooldownMultiplier < 1.0D ? 1.0D : forbiddenCooldownMultiplier; }
        public void setForbiddenCooldownMultiplier(double forbiddenCooldownMultiplier) { this.forbiddenCooldownMultiplier = forbiddenCooldownMultiplier; }
        public int getRequestJitterMs() { return Math.max(0, requestJitterMs); }
        public void setRequestJitterMs(int requestJitterMs) { this.requestJitterMs = requestJitterMs; }
        public int getRequestMinIntervalMs() { return Math.max(0, requestMinIntervalMs); }
        public void setRequestMinIntervalMs(int requestMinIntervalMs) { this.requestMinIntervalMs = requestMinIntervalMs; }
        public int getRequestBudgetMaxRequests() {
            if (requestBudgetMaxRequests <= 0) {
                return HIGHSCORE_REQUEST_BUDGET_HARD_LIMIT;
            }
            return Math.min(requestBudgetMaxRequests, HIGHSCORE_REQUEST_BUDGET_HARD_LIMIT);
        }
        public void setRequestBudgetMaxRequests(int requestBudgetMaxRequests) { this.requestBudgetMaxRequests = requestBudgetMaxRequests; }
        public long getRequestBudgetWindowMs() {
            if (requestBudgetWindowMs <= 0) {
                return HIGHSCORE_REQUEST_BUDGET_WINDOW_MS;
            }
            return Math.max(requestBudgetWindowMs, HIGHSCORE_REQUEST_BUDGET_WINDOW_MS);
        }
        public void setRequestBudgetWindowMs(long requestBudgetWindowMs) { this.requestBudgetWindowMs = requestBudgetWindowMs; }
        public int getCooldownLogIntervalMs() { return Math.max(1000, cooldownLogIntervalMs); }
        public void setCooldownLogIntervalMs(int cooldownLogIntervalMs) { this.cooldownLogIntervalMs = cooldownLogIntervalMs; }
        public int getProgressLogIntervalScopes() { return Math.max(1, progressLogIntervalScopes); }
        public void setProgressLogIntervalScopes(int progressLogIntervalScopes) { this.progressLogIntervalScopes = progressLogIntervalScopes; }
        public boolean isAbortRunOnForbidden() { return abortRunOnForbidden; }
        public void setAbortRunOnForbidden(boolean abortRunOnForbidden) { this.abortRunOnForbidden = abortRunOnForbidden; }

        public List<StatCategory> categoryList() { return parseCategories(categories); }
        public List<Integer> vocationFilterIds() { return parseVocations(vocations); }

        public String summary() {
            return "enabled=" + enabled
                    + ", cron=" + cron
                    + ", zone=" + getZone()
                    + ", runOnStartup=" + runOnStartup
                    + ", categories=" + categories
                    + ", vocations=" + vocations
                    + ", maxPages=" + getMaxPages()
                    + ", worldLimit=" + getWorldLimit()
                    + ", scopesPerRun=" + getScopesPerRun()
                    + ", parallelism=" + getParallelism()
                    + ", pageWindowSize=" + getPageWindowSize()
                    + ", requestParallelism=" + getRequestParallelism()
                    + ", requestMinIntervalMs=" + getRequestMinIntervalMs()
                    + ", requestBudgetMaxRequests=" + getRequestBudgetMaxRequests()
                    + ", requestBudgetWindowMs=" + getRequestBudgetWindowMs()
                    + ", requestMaxAttempts=" + getRequestMaxAttempts()
                    + ", forbiddenInitialCooldownMs=" + getForbiddenInitialCooldownMs()
                    + ", forbiddenMaxCooldownMs=" + getForbiddenMaxCooldownMs()
                    + ", forbiddenCooldownMultiplier=" + getForbiddenCooldownMultiplier()
                    + ", abortRunOnForbidden=" + abortRunOnForbidden;
        }
    }
}
EOF_TIBIACHRONO_V3_1

write_file "src/main/resources/application.yml" <<'EOF_TIBIACHRONO_V3_2'
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/tibiastats
    username: tibia
    password: secret
  security:
    oauth2:
      resourceserver:
        jwt:
          secret-key: "please-change-me-to-a-very-long-random-secret"
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
  task:
    scheduling:
      pool:
        size: 4

  graphql:
    path: /graphql

server:
  port: 8080

# Scraping schedules (overrideable)
tibiastats:
  scrape:
    worlds:
      enabled: true
      rate-ms: 60000
    character-details:
      enabled: true
      rate-ms: 60000
      initial-delay-ms: 15000
      batch-size: 50

    guilds:
      enabled: false
      rate-ms: 3600000          # 1h
      initial-delay-ms: 30000
      world-limit: 0
      guild-limit: 50
      page-delay-ms: 750
      list-enabled: true
      details-enabled: true
    highscores:
      enabled: true
      cron: "0 0 7 * * *"
      categories: "ACHIEVEMENTS,AXE_FIGHTING,BOSS_POINTS,BOUNTY_POINTS_EARNED,CHARM_POINTS,CLUB_FIGHTING,DISTANCE_FIGHTING,DROME_SCORE,EXPERIENCE,FISHING,FIST_FIGHTING,GOSHNARS_TAINT,LOYALTY_POINTS,MAGIC_LEVEL,SHIELDING,SWORD_FIGHTING,WEEKLY_TASKS_COMPLETED"
      vocations: "0,1,2,3,4,5,6"
      max-pages: 100
      page-delay-ms: 1000
      world-limit: 0
      request-parallelism: 1
      request-max-attempts: 1
      retry-base-delay-ms: 5000
      retry-max-delay-ms: 300000
      forbidden-cooldown-ms: 259200000
      forbidden-initial-cooldown-ms: 259200000
      forbidden-max-cooldown-ms: 1209600000
      forbidden-cooldown-multiplier: 2.0
      request-jitter-ms: 300
      request-min-interval-ms: 750
      request-budget-max-requests: 150000
      request-budget-window-ms: 600000
      cooldown-log-interval-ms: 30000
      abort-run-on-forbidden: true
  jwt:
    access-ttl-ms: 900000
    refresh-ttl-ms: 1209600000
EOF_TIBIACHRONO_V3_2

write_file "src/main/resources/application-dev.yml" <<'EOF_TIBIACHRONO_V3_3'
spring:
  config:
    activate:
      on-profile: dev

  datasource:
    url: jdbc:postgresql://db:5432/tibiastats
    username: tibia
    password: secret
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    defer-datasource-initialization: false   # ensure migrations happen before JPA validates
    open-in-view: false
    show-sql: false
    properties:
      hibernate:
        format_sql: false

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  graphql:
    path: /graphql

  devtools:
    restart:
      enabled: true
    livereload:
      enabled: true

  security:
    oauth2:
      resourceserver:
        jwt:
          secret-key: "please-change-me-to-a-very-long-random-secret"

logging:
  level:
    root: INFO
    org.hibernate.SQL: WARN
    org.springframework.web: INFO
    org.hibernate.type.descriptor.sql.BasicBinder: WARN
    org.flywaydb.core: INFO
    org.hibernate.tool.hbm2ddl: INFO
    com.nathan.tibiastats: INFO

server:
  port: 8080

tibiastats:
  scrape:
    worlds:
      enabled: true
      rate-ms: 60000
    character-details:
      enabled: true
      rate-ms: 60000
      initial-delay-ms: 15000
      batch-size: 50
    guilds:
      enabled: false
      rate-ms: 3600000
      initial-delay-ms: 30000
      world-limit: 0
      guild-limit: 50
      page-delay-ms: 750
      list-enabled: true
      details-enabled: true
    highscores:
      enabled: true
      # The new production model uses independent highscore plans.
      # EXP is daily at 07:00 Sao Paulo time.
      # Other categories are spread across the week using vocation=0 (overall ranking) to avoid the x7 vocation multiplier.
      # If you later need vocation-specific history for a non-EXP category, change that plan's vocations to "0,1,2,3,4,5,6" or run a manual backfill.
      plans:
        daily-exp:
          enabled: true
          cron: "0 0 7 * * *"
          categories: "EXPERIENCE"
          vocations: "0,1,2,3,4,5,6"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        monday-axe-fighting-overall:
          enabled: true
          cron: "0 0 21 * * MON"
          categories: "AXE_FIGHTING"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        monday-club-fighting-overall:
          enabled: true
          cron: "0 30 23 * * MON"
          categories: "CLUB_FIGHTING"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        tuesday-sword-fighting-overall:
          enabled: true
          cron: "0 0 21 * * TUE"
          categories: "SWORD_FIGHTING"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        tuesday-distance-fighting-overall:
          enabled: true
          cron: "0 30 23 * * TUE"
          categories: "DISTANCE_FIGHTING"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        wednesday-shielding-overall:
          enabled: true
          cron: "0 0 21 * * WED"
          categories: "SHIELDING"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        wednesday-fist-fighting-overall:
          enabled: true
          cron: "0 30 23 * * WED"
          categories: "FIST_FIGHTING"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        thursday-magic-level-overall:
          enabled: true
          cron: "0 0 21 * * THU"
          categories: "MAGIC_LEVEL"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        thursday-achievements-overall:
          enabled: true
          cron: "0 30 23 * * THU"
          categories: "ACHIEVEMENTS"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        friday-charm-points-overall:
          enabled: true
          cron: "0 0 21 * * FRI"
          categories: "CHARM_POINTS"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        friday-loyalty-points-overall:
          enabled: true
          cron: "0 30 23 * * FRI"
          categories: "LOYALTY_POINTS"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        saturday-boss-points-overall:
          enabled: true
          cron: "0 0 9 * * SAT"
          categories: "BOSS_POINTS"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        saturday-bounty-points-overall:
          enabled: true
          cron: "0 0 13 * * SAT"
          categories: "BOUNTY_POINTS_EARNED"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        saturday-drome-score-overall:
          enabled: true
          cron: "0 0 17 * * SAT"
          categories: "DROME_SCORE"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        saturday-goshnars-taint-overall:
          enabled: true
          cron: "0 0 21 * * SAT"
          categories: "GOSHNARS_TAINT"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        sunday-fishing-overall:
          enabled: true
          cron: "0 0 9 * * SUN"
          categories: "FISHING"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        sunday-weekly-tasks-overall:
          enabled: true
          cron: "0 0 13 * * SUN"
          categories: "WEEKLY_TASKS_COMPLETED"
          vocations: "0"
          zone: "America/Sao_Paulo"
          run-on-startup: false
          startup-delay-ms: 0
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 4
          page-window-size: 1
          request-parallelism: 4
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 750
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
        # Manual/template plan. Keep disabled; enable temporarily for a controlled deep backfill.
        manual-backfill-all-highscores:
          enabled: false
          cron: "0 0 3 * * SUN"
          zone: "America/Sao_Paulo"
          run-on-startup: true
          startup-delay-ms: 0
          categories: "ACHIEVEMENTS,AXE_FIGHTING,BOSS_POINTS,BOUNTY_POINTS_EARNED,CHARM_POINTS,CLUB_FIGHTING,DISTANCE_FIGHTING,DROME_SCORE,EXPERIENCE,FISHING,FIST_FIGHTING,GOSHNARS_TAINT,LOYALTY_POINTS,MAGIC_LEVEL,SHIELDING,SWORD_FIGHTING,WEEKLY_TASKS_COMPLETED"
          vocations: "0,1,2,3,4,5,6"
          max-pages: 100
          page-delay-ms: 0
          world-limit: 0
          scopes-per-run: 0
          parallelism: 1
          page-window-size: 1
          request-parallelism: 1
          request-max-attempts: 1
          retry-base-delay-ms: 5000
          retry-max-delay-ms: 300000
          forbidden-cooldown-ms: 259200000
          forbidden-initial-cooldown-ms: 259200000
          forbidden-max-cooldown-ms: 1209600000
          forbidden-cooldown-multiplier: 2.0
          request-jitter-ms: 300
          request-min-interval-ms: 2000
          request-budget-max-requests: 150000
          request-budget-window-ms: 600000
          cooldown-log-interval-ms: 30000
          progress-log-interval-scopes: 10
          abort-run-on-forbidden: true
  jwt:
    access-ttl-ms: 900000       # 15 minutes
    refresh-ttl-ms: 1209600000  # 14 days
EOF_TIBIACHRONO_V3_3

write_file "src/test/java/com/nathan/tibiastats/config/HighscorePlanConfigurationTest.java" <<'EOF_TIBIACHRONO_V3_4'
package com.nathan.tibiastats.config;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class HighscorePlanConfigurationTest {
    @Test
    void applicationDevDefinesSafeDistributedHighscorePlans() {
        HighscoreScrapeProperties properties = bindApplicationDevHighscoreProperties();

        assertThat(properties.getPlans()).hasSize(18);

        HighscoreScrapeProperties.Plan dailyExp = properties.getPlans().get("daily-exp");
        assertThat(dailyExp).isNotNull();
        assertThat(dailyExp.isEnabled()).isTrue();
        assertThat(dailyExp.getCron()).isEqualTo("0 0 7 * * *");
        assertThat(dailyExp.getZone()).isEqualTo("America/Sao_Paulo");
        assertThat(dailyExp.categoryList()).containsExactly(StatCategory.EXPERIENCE);
        assertThat(dailyExp.vocationFilterIds()).containsExactly(0, 1, 2, 3, 4, 5, 6);
        assertThat(dailyExp.getPageWindowSize()).isEqualTo(1);
        assertThat(dailyExp.getRequestMaxAttempts()).isEqualTo(1);
        assertThat(dailyExp.getForbiddenInitialCooldownMs()).isEqualTo(259_200_000L);
        assertThat(dailyExp.getForbiddenMaxCooldownMs()).isEqualTo(1_209_600_000L);
        assertThat(dailyExp.getRequestBudgetMaxRequests()).isEqualTo(150_000);
        assertThat(dailyExp.getRequestBudgetWindowMs()).isGreaterThanOrEqualTo(600_000L);
        assertThat(dailyExp.isAbortRunOnForbidden()).isTrue();
        assertThat(dailyExp.isRunOnStartup()).isFalse();

        HighscoreScrapeProperties.Plan manualBackfill = properties.getPlans().get("manual-backfill-all-highscores");
        assertThat(manualBackfill).isNotNull();
        assertThat(manualBackfill.isEnabled()).isFalse();
        assertThat(manualBackfill.getParallelism()).isEqualTo(1);
        assertThat(manualBackfill.getRequestParallelism()).isEqualTo(1);
        assertThat(manualBackfill.getRequestMinIntervalMs()).isGreaterThanOrEqualTo(2_000);

        properties.getPlans().forEach((name, plan) -> {
            if (!plan.isEnabled()) {
                return;
            }
            assertThat(plan.getPageWindowSize()).as(name + " must not use page-level parallelism").isEqualTo(1);
            assertThat(plan.getRequestMaxAttempts()).as(name + " must not keep retrying 403/429 responses").isEqualTo(1);
            assertThat(plan.getForbiddenInitialCooldownMs()).as(name + " must start 403/429 cooldown at 72h").isEqualTo(259_200_000L);
            assertThat(plan.getForbiddenMaxCooldownMs()).as(name + " must cap progressive 403/429 cooldown at 14d").isEqualTo(1_209_600_000L);
            assertThat(plan.getRequestBudgetMaxRequests()).as(name + " must never exceed 150k requests per budget window").isLessThanOrEqualTo(150_000);
            assertThat(plan.getRequestBudgetWindowMs()).as(name + " must use at least a 10 minute budget window").isGreaterThanOrEqualTo(600_000L);
            assertThat(plan.isAbortRunOnForbidden()).as(name + " must abort on 403/429").isTrue();
            assertThat(plan.isRunOnStartup()).as(name + " should not run automatically on every app boot").isFalse();
        });
    }


    @Test
    void planClampsUnsafeRequestBudgetConfiguration() {
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setRequestBudgetMaxRequests(999_999);
        plan.setRequestBudgetWindowMs(1_000L);

        assertThat(plan.getRequestBudgetMaxRequests()).isEqualTo(150_000);
        assertThat(plan.getRequestBudgetWindowMs()).isEqualTo(600_000L);
    }

    private HighscoreScrapeProperties bindApplicationDevHighscoreProperties() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-dev.yml"));
        Properties yamlProperties = yaml.getObject();
        assertThat(yamlProperties).as("application-dev.yml must be loadable").isNotNull();

        Map<String, Object> source = new LinkedHashMap<>();
        yamlProperties.forEach((key, value) -> source.put(String.valueOf(key), value));

        return new Binder(new MapConfigurationPropertySource(source))
                .bind("tibiastats.scrape.highscores", HighscoreScrapeProperties.class)
                .orElseThrow(() -> new AssertionError("Could not bind tibiastats.scrape.highscores from application-dev.yml"));
    }
}
EOF_TIBIACHRONO_V3_4

echo "Highscore safe request budget and 72h/14d cooldown patch v3 applied."
echo "Backups saved at: $BACKUP_DIR"
