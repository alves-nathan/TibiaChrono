#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
BACKUP_DIR="$ROOT/.tibiachrono-highscore-bounded-workers-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"

backup_file() {
  local file="$1"
  if [ -f "$ROOT/$file" ]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$ROOT/$file" "$BACKUP_DIR/$file"
  fi
}

write_file() {
  local file="$1"
  mkdir -p "$ROOT/$(dirname "$file")"
  cat > "$ROOT/$file"
}

backup_file "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java"
backup_file "src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java"
backup_file "src/main/resources/application-dev.yml"

write_file "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java" <<'EOF_SERVICE'
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

    public void updateAllHighscores() {
        if (!properties.isEnabled()) {
            log.info("[HIGHSCORE_SCRAPER] Skipping run because highscores.enabled=false");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.warn("[HIGHSCORE_SCRAPER] Previous highscore run is still active. Skipping this tick.");
            return;
        }

        Instant startedAt = Instant.now();
        try {
            runIncrementalHighscoreScrape(startedAt);
        } finally {
            running.set(false);
        }
    }

    private void runIncrementalHighscoreScrape(Instant startedAt) {
        List<World> worlds = worldRepository.findAll().stream()
                .sorted(Comparator.comparing(World::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(properties.getWorldLimit() > 0 ? properties.getWorldLimit() : Long.MAX_VALUE)
                .toList();
        List<StatCategory> categories = properties.categoryList();
        List<Integer> vocationFilterIds = properties.vocationFilterIds();

        if (worlds.isEmpty()) {
            log.warn("[HIGHSCORE_SCRAPER] No worlds found. Run the world scraper first.");
            return;
        }

        stateRepository.registerScopes(worlds, categories, vocationFilterIds);
        List<HighscoreScope> scopes = stateRepository.findNextScopes(
                worlds,
                categories,
                vocationFilterIds,
                properties.getScopesPerRun()
        );

        if (scopes.isEmpty()) {
            log.info("[HIGHSCORE_SCRAPER] No eligible highscore scopes found.");
            return;
        }

        log.info(
                "[HIGHSCORE_SCRAPER] Starting run: selectedScopes={}, scopesPerRun={}, allScopesPerRun={}, scopeWorkers={}, requestParallelism={}, pageWindowSize={}, maxPages={}, pageDelayMs={}, requestMaxAttempts={}, retryBaseDelayMs={}, retryMaxDelayMs={}, forbiddenCooldownMs={}, requestJitterMs={}, requestMinIntervalMs={}, cooldownLogIntervalMs={}, progressLogIntervalScopes={}, worlds={}, categories={}, vocations={}",
                scopes.size(),
                properties.getScopesPerRun(),
                properties.isAllScopesPerRun(),
                Math.min(properties.getParallelism(), scopes.size()),
                properties.getRequestParallelism(),
                properties.getPageWindowSize(),
                properties.getMaxPages(),
                properties.getPageDelayMs(),
                properties.getRequestMaxAttempts(),
                properties.getRetryBaseDelayMs(),
                properties.getRetryMaxDelayMs(),
                properties.getForbiddenCooldownMs(),
                properties.getRequestJitterMs(),
                properties.getRequestMinIntervalMs(),
                properties.getCooldownLogIntervalMs(),
                properties.getProgressLogIntervalScopes(),
                worlds.size(),
                categories.size(),
                vocationFilterIds.size()
        );

        Semaphore requestSemaphore = new Semaphore(properties.getRequestParallelism());
        Map<String, Long> characterIdCache = new ConcurrentHashMap<>();
        AtomicInteger nextScopeIndex = new AtomicInteger(0);
        AtomicInteger completedScopes = new AtomicInteger(0);
        int workerCount = Math.min(properties.getParallelism(), scopes.size());
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
                        requestSemaphore
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

            log.info(
                    "[HIGHSCORE_SCRAPER] Finished run: successScopes={}, emptyScopes={}, failedScopes={}, completedScopes={}, selectedScopes={}, pages={}, rows={}, durationMs={}, cacheSize={}",
                    success,
                    empty,
                    failed,
                    completedScopes.get(),
                    scopes.size(),
                    totalPages,
                    totalRows,
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    characterIdCache.size()
            );
        }
    }

    private WorkerResult runScopeWorker(
            int workerId,
            List<HighscoreScope> scopes,
            AtomicInteger nextScopeIndex,
            AtomicInteger completedScopes,
            Map<String, Long> characterIdCache,
            ExecutorService executor,
            Semaphore requestSemaphore
    ) {
        int success = 0;
        int empty = 0;
        int failed = 0;
        int rows = 0;
        int pages = 0;

        while (!Thread.currentThread().isInterrupted()) {
            int index = nextScopeIndex.getAndIncrement();
            if (index >= scopes.size()) {
                break;
            }

            HighscoreScope scope = scopes.get(index);
            ScopeResult result = scrapeScope(scope, characterIdCache, executor, requestSemaphore);
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
            if (done == scopes.size() || done % properties.getProgressLogIntervalScopes() == 0) {
                log.info(
                        "[HIGHSCORE_SCRAPER] Run progress: completedScopes={}/{}, workerId={}, lastScope={}, lastStatus={}, pagesSoFar={}, rowsSoFar={}",
                        done,
                        scopes.size(),
                        workerId,
                        scope.label(),
                        result.status(),
                        pages,
                        rows
                );
            }
        }

        return new WorkerResult(success, empty, failed, pages, rows);
    }

    private ScopeResult scrapeScope(
            HighscoreScope scope,
            Map<String, Long> characterIdCache,
            ExecutorService executor,
            Semaphore requestSemaphore
    ) {
        Instant startedAt = Instant.now();
        stateRepository.markStarted(scope);
        log.info("[HIGHSCORE_SCRAPER] Scope started: {}", scope.label());

        int pages = 0;
        int rows = 0;
        try {
            LocalDate snapshotDate = LocalDate.now(SNAPSHOT_ZONE);
            Instant scrapedAt = Instant.now();
            int page = 1;
            boolean shouldStop = false;

            while (page <= properties.getMaxPages() && !shouldStop) {
                int windowStart = page;
                int windowEnd = Math.min(properties.getMaxPages(), windowStart + properties.getPageWindowSize() - 1);
                List<Future<PageResult>> pageFutures = new ArrayList<>();

                for (int currentPage = windowStart; currentPage <= windowEnd; currentPage++) {
                    int pageToFetch = currentPage;
                    pageFutures.add(executor.submit(() -> fetchPage(scope, pageToFetch, requestSemaphore)));
                }

                List<PageResult> pageResults = new ArrayList<>(pageFutures.size());
                for (Future<PageResult> pageFuture : pageFutures) {
                    pageResults.add(pageFuture.get());
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
            log.info("[HIGHSCORE_SCRAPER] Scope finished: scope={}, status={}, pages={}, rows={}, durationMs={}",
                    scope.label(), status, pages, rows, durationMs);
            return new ScopeResult(status, pages, rows);
        } catch (Exception ex) {
            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
            stateRepository.markFinished(scope, "FAILED", pages, rows, durationMs, rootMessage(ex));
            log.error("[HIGHSCORE_SCRAPER] Scope failed: scope={}, pages={}, rows={}, durationMs={}, error={}",
                    scope.label(), pages, rows, durationMs, rootMessage(ex), ex);
            return new ScopeResult("FAILED", pages, rows);
        }
    }

    private PageResult fetchPage(HighscoreScope scope, int page, Semaphore requestSemaphore) throws InterruptedException {
        int maxAttempts = properties.getRequestMaxAttempts();
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            requestSemaphore.acquire();
            try {
                awaitGlobalHttpCooldown();
                awaitGlobalRequestPace();
                throttleRequestWithJitter();
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
                    activateGlobalHttpCooldown(rootMessage(ex));
                }

                if (!shouldRetry) {
                    throw ex;
                }

                long retryDelayMs = retryDelayMs(attempt, ex);
                log.warn(
                        "[HIGHSCORE_SCRAPER] Transient page fetch failure. Retrying: scope={}, page={}, attempt={}/{}, delayMs={}, error={}",
                        scope.label(),
                        page,
                        attempt,
                        maxAttempts,
                        retryDelayMs,
                        rootMessage(ex)
                );
                sleepWithRetryHeartbeat(retryDelayMs, scope, page, attempt, maxAttempts);
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

    private void throttleRequestWithJitter() {
        int baseDelay = properties.getPageDelayMs();
        int jitter = properties.getRequestJitterMs();
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
    private void awaitGlobalRequestPace() {
        int minIntervalMs = properties.getRequestMinIntervalMs();
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

    private void awaitGlobalHttpCooldown() {
        while (true) {
            long now = System.currentTimeMillis();
            long until = globalHttpCooldownUntilMs.get();
            long waitMs = until - now;
            if (waitMs <= 0) {
                return;
            }
            logCooldownHeartbeat(waitMs);
            sleepMs(Math.min(waitMs, 1000));
        }
    }

    private void activateGlobalHttpCooldown(String reason) {
        int cooldownMs = properties.getForbiddenCooldownMs();
        if (cooldownMs <= 0) {
            return;
        }

        long until = System.currentTimeMillis() + cooldownMs;
        long previous = globalHttpCooldownUntilMs.getAndUpdate(current -> Math.max(current, until));
        if (until > previous) {
            log.warn(
                    "[HIGHSCORE_SCRAPER] HTTP cooldown activated for {}ms after Tibia response: {}",
                    cooldownMs,
                    reason
            );
        }
    }

    private void logCooldownHeartbeat(long remainingMs) {
        long now = System.currentTimeMillis();
        long intervalMs = properties.getCooldownLogIntervalMs();
        long lastLog = lastCooldownLogAtMs.get();
        if (intervalMs > 0 && now - lastLog >= intervalMs && lastCooldownLogAtMs.compareAndSet(lastLog, now)) {
            log.info("[HIGHSCORE_SCRAPER] HTTP cooldown still active: remainingMs={}", Math.max(0, remainingMs));
        }
    }

    private void sleepWithRetryHeartbeat(long delayMs, HighscoreScope scope, int page, int attempt, int maxAttempts) {
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

            long intervalMs = properties.getCooldownLogIntervalMs();
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

    private long retryDelayMs(int attempt, RuntimeException ex) {
        long base = Math.max(0, properties.getRetryBaseDelayMs());
        long max = Math.max(base, properties.getRetryMaxDelayMs());
        long exponential = base <= 0 ? 0 : base * (1L << Math.min(attempt - 1, 5));
        long delay = Math.min(max, exponential);

        if (isForbiddenOrRateLimited(ex)) {
            delay = Math.max(delay, Math.min(max, properties.getForbiddenCooldownMs()));
        }

        int jitter = properties.getRequestJitterMs();
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

    private record ScopeResult(String status, int pages, int rows) {}
    private record PageResult(int page, List<HighscorePort.HighscoreRow> rows) {}
    private record WorkerResult(int successScopes, int emptyScopes, int failedScopes, int pages, int rows) {}
}

EOF_SERVICE

write_file "src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java" <<'EOF_PROPS'
package com.nathan.tibiastats.config;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "tibiastats.scrape.highscores")
public class HighscoreScrapeProperties {
    private static final Logger log = LoggerFactory.getLogger(HighscoreScrapeProperties.class);

    private boolean enabled = true;
    private String cron = "0 0 7 * * *";
    private String zone = "America/Sao_Paulo";
    private boolean runOnStartup = false;
    private long startupDelayMs = 0;
    private String categories = "EXPERIENCE";
    private String vocations = "0";
    private int maxPages = 100;
    private int pageDelayMs = 250;
    private int worldLimit = 0;
    private int scopesPerRun = 0;
    private int parallelism = 2;
    private int pageWindowSize = 1;
    private int requestParallelism = 1;
    private int requestMaxAttempts = 3;
    private int retryBaseDelayMs = 5000;
    private int retryMaxDelayMs = 300000;
    private int forbiddenCooldownMs = 300000;
    private int requestJitterMs = 750;
    private int requestMinIntervalMs = 2000;
    private int cooldownLogIntervalMs = 30000;
    private int progressLogIntervalScopes = 10;

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
        return (zone == null || zone.isBlank()) ? "America/Sao_Paulo" : zone;
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

    public List<StatCategory> categoryList() {
        List<StatCategory> parsed = new ArrayList<>();
        for (String token : splitCsv(categories)) {
            try {
                parsed.add(StatCategory.valueOf(token));
            } catch (IllegalArgumentException ex) {
                log.warn("[HIGHSCORE_SCRAPER] Ignoring invalid highscore category config: {}", token);
            }
        }
        return parsed.isEmpty() ? List.of(StatCategory.EXPERIENCE) : parsed;
    }

    public List<Integer> vocationFilterIds() {
        Set<Integer> parsed = new LinkedHashSet<>();
        for (String token : splitCsv(vocations)) {
            try {
                parsed.add(Integer.parseInt(token));
            } catch (NumberFormatException ex) {
                log.warn("[HIGHSCORE_SCRAPER] Ignoring invalid highscore vocation config: {}", token);
            }
        }
        return parsed.isEmpty() ? List.of(0) : new ArrayList<>(parsed);
    }

    private List<String> splitCsv(String value) {
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
}

EOF_PROPS

write_file "src/main/resources/application-dev.yml" <<'EOF_APP'
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
      rate-ms: 60000            # 1 min scrape interval
    highscores:
      enabled: true
      cron: "0 0 7 * * *"
      zone: "America/Sao_Paulo"
      run-on-startup: true
      startup-delay-ms: 0
      categories: "ACHIEVEMENTS,AXE_FIGHTING,BOSS_POINTS,BOUNTY_POINTS_EARNED,CHARM_POINTS,CLUB_FIGHTING,DISTANCE_FIGHTING,DROME_SCORE,EXPERIENCE,FISHING,FIST_FIGHTING,GOSHNARS_TAINT,LOYALTY_POINTS,MAGIC_LEVEL,SHIELDING,SWORD_FIGHTING,WEEKLY_TASKS_COMPLETED"
      vocations: "0,1,2,3,4,5,6"
      max-pages: 100
      page-delay-ms: 250
      world-limit: 0
      scopes-per-run: 0
      parallelism: 2
      page-window-size: 1
      request-parallelism: 1
      request-max-attempts: 3
      retry-base-delay-ms: 5000
      retry-max-delay-ms: 300000
      forbidden-cooldown-ms: 300000
      request-jitter-ms: 750
      request-min-interval-ms: 2000
      cooldown-log-interval-ms: 30000
      progress-log-interval-scopes: 10
  jwt:
    access-ttl-ms: 900000       # 15 minutes
    refresh-ttl-ms: 1209600000  # 14 days

EOF_APP

chmod 644   "$ROOT/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java"   "$ROOT/src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java"   "$ROOT/src/main/resources/application-dev.yml"

echo "Applied highscore bounded workers + cooldown heartbeat safe throttle. Backup: $BACKUP_DIR"
