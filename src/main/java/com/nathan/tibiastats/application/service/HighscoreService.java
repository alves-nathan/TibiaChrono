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
