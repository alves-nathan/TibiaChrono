#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
if [ ! -f "$ROOT/pom.xml" ] || [ ! -d "$ROOT/src/main/java" ]; then
  echo "Run this script from the TibiaChrono project root." >&2
  exit 1
fi

BACKUP_DIR="$ROOT/.tibiachrono-highscore-403-backoff-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"
backup_file() {
  local path="$1"
  if [ -f "$ROOT/$path" ]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$path")"
    cp "$ROOT/$path" "$BACKUP_DIR/$path"
  fi
}

backup_file "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java"
mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/application/service"
cat > '$ROOT/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java' <<'EOF_FILE'
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
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
                "[HIGHSCORE_SCRAPER] Starting run: selectedScopes={}, scopesPerRun={}, allScopesPerRun={}, scopeParallelism={}, requestParallelism={}, pageWindowSize={}, maxPages={}, pageDelayMs={}, requestMaxAttempts={}, retryBaseDelayMs={}, retryMaxDelayMs={}, forbiddenCooldownMs={}, requestJitterMs={}, worlds={}, categories={}, vocations={}",
                scopes.size(),
                properties.getScopesPerRun(),
                properties.isAllScopesPerRun(),
                properties.getParallelism(),
                properties.getRequestParallelism(),
                properties.getPageWindowSize(),
                properties.getMaxPages(),
                properties.getPageDelayMs(),
                properties.getRequestMaxAttempts(),
                properties.getRetryBaseDelayMs(),
                properties.getRetryMaxDelayMs(),
                properties.getForbiddenCooldownMs(),
                properties.getRequestJitterMs(),
                worlds.size(),
                categories.size(),
                vocationFilterIds.size()
        );

        Semaphore scopeSemaphore = new Semaphore(properties.getParallelism());
        Semaphore requestSemaphore = new Semaphore(properties.getRequestParallelism());
        Map<String, Long> characterIdCache = new ConcurrentHashMap<>();
        List<Future<ScopeResult>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (HighscoreScope scope : scopes) {
                futures.add(executor.submit(() -> {
                    scopeSemaphore.acquire();
                    try {
                        return scrapeScope(scope, characterIdCache, executor, requestSemaphore);
                    } finally {
                        scopeSemaphore.release();
                    }
                }));
            }

            int success = 0;
            int empty = 0;
            int failed = 0;
            int totalRows = 0;
            int totalPages = 0;

            for (Future<ScopeResult> future : futures) {
                try {
                    ScopeResult result = future.get();
                    totalRows += result.rows();
                    totalPages += result.pages();
                    if ("SUCCESS".equals(result.status())) {
                        success++;
                    } else if ("EMPTY".equals(result.status())) {
                        empty++;
                    } else {
                        failed++;
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    failed++;
                    log.warn("[HIGHSCORE_SCRAPER] Run interrupted while waiting for scope results", ex);
                    break;
                } catch (ExecutionException ex) {
                    failed++;
                    log.error("[HIGHSCORE_SCRAPER] Unexpected highscore worker failure", ex.getCause());
                }
            }

            log.info(
                    "[HIGHSCORE_SCRAPER] Finished run: successScopes={}, emptyScopes={}, failedScopes={}, pages={}, rows={}, durationMs={}, cacheSize={}",
                    success,
                    empty,
                    failed,
                    totalPages,
                    totalRows,
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    characterIdCache.size()
            );
        }
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
            awaitGlobalHttpCooldown();
            requestSemaphore.acquire();
            try {
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
                sleepMs(retryDelayMs);
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

    private void awaitGlobalHttpCooldown() {
        while (true) {
            long now = System.currentTimeMillis();
            long until = globalHttpCooldownUntilMs.get();
            long waitMs = until - now;
            if (waitMs <= 0) {
                return;
            }
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
}
EOF_FILE

backup_file "src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java"
mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/config"
cat > '$ROOT/src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java' <<'EOF_FILE'
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
    private int parallelism = 4;

    /**
     * How many pages from the same scope may be fetched in one parallel window.
     * This is the biggest performance lever because the old scraper fetched pages 1..N sequentially.
     */
    private int pageWindowSize = 4;

    /**
     * Global cap for simultaneous HTTP requests across every scope and page window.
     * Keep this finite to avoid accidentally hammering tibia.com.
     */
    private int requestParallelism = 12;

    /**
     * Maximum attempts for one highscore page request. HTTP 403/429/5xx and timeouts are treated as transient.
     */
    private int requestMaxAttempts = 4;

    /**
     * Base backoff used after transient HTTP errors. Backoff grows with every retry attempt.
     */
    private int retryBaseDelayMs = 1500;

    /**
     * Maximum delay between retries for a single page.
     */
    private int retryMaxDelayMs = 30000;

    /**
     * Global cooldown after HTTP 403/429. This prevents the parallel workers from continuing to hammer Tibia.com.
     */
    private int forbiddenCooldownMs = 45000;

    /**
     * Small random delay added to requests/retries so parallel workers do not fire in perfect bursts.
     */
    private int requestJitterMs = 300;

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
EOF_FILE

backup_file "src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java"
mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/application/scheduler"
cat > '$ROOT/src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java' <<'EOF_FILE'
package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.HighscoreService;
import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HighscoreScrapeScheduler {
    private static final Logger log = LoggerFactory.getLogger(HighscoreScrapeScheduler.class);

    private final HighscoreService service;
    private final HighscoreScrapeProperties properties;

    public HighscoreScrapeScheduler(HighscoreService service, HighscoreScrapeProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostConstruct
    public void logConfiguration() {
        log.info(
                "[HIGHSCORE_SCRAPER] Scheduler configured: enabled={}, cron={}, zone={}, runOnStartup={}, startupDelayMs={}, categories={}, vocations={}, maxPages={}, pageDelayMs={}, worldLimit={}, scopesPerRun={}, allScopesPerRun={}, parallelism={}, pageWindowSize={}, requestParallelism={}, requestMaxAttempts={}, forbiddenCooldownMs={}, requestJitterMs={}",
                properties.isEnabled(),
                properties.getCron(),
                properties.getZone(),
                properties.isRunOnStartup(),
                properties.getStartupDelayMs(),
                properties.getCategories(),
                properties.getVocations(),
                properties.getMaxPages(),
                properties.getPageDelayMs(),
                properties.getWorldLimit(),
                properties.getScopesPerRun(),
                properties.isAllScopesPerRun(),
                properties.getParallelism(),
                properties.getPageWindowSize(),
                properties.getRequestParallelism(),
                properties.getRequestMaxAttempts(),
                properties.getForbiddenCooldownMs(),
                properties.getRequestJitterMs()
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        if (!properties.isRunOnStartup()) {
            log.info("[HIGHSCORE_SCRAPER] Startup run disabled. Waiting for cron schedule.");
            return;
        }

        Thread.startVirtualThread(() -> {
            try {
                long startupDelayMs = properties.getStartupDelayMs();
                if (startupDelayMs > 0) {
                    log.info("[HIGHSCORE_SCRAPER] Startup run scheduled after {}ms", startupDelayMs);
                    Thread.sleep(startupDelayMs);
                }

                log.info("[HIGHSCORE_SCRAPER] Startup run started");
                service.updateAllHighscores();
                log.info("[HIGHSCORE_SCRAPER] Startup run finished");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("[HIGHSCORE_SCRAPER] Startup run interrupted");
            } catch (Exception ex) {
                log.error("[HIGHSCORE_SCRAPER] Startup run failed", ex);
            }
        });
    }

    @Scheduled(
            cron = "${tibiastats.scrape.highscores.cron:0 0 7 * * *}",
            zone = "${tibiastats.scrape.highscores.zone:America/Sao_Paulo}"
    )
    public void run() {
        log.info("[HIGHSCORE_SCRAPER] Scheduler tick started");
        service.updateAllHighscores();
    }
}
EOF_FILE

backup_file "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java"
mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper"
cat > '$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java' <<'EOF_FILE'
package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.port.HighscorePort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class JsoupHighscoreAdapter implements HighscorePort {
    private static final String HS_URL = "https://www.tibia.com/community/?subtopic=highscores&world=%s&beprotection=-1&profession=%d&category=%d&currentpage=%d";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    /**
     * Reuse one HttpClient so Java can reuse connections instead of creating a fresh connection for every page.
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_2)
            .build();

    @Override
    public List<HighscoreRow> fetchHighscores(String world, StatCategory category, int vocationId, int page) {
        int catId = mapCategory(category);
        String encodedWorld = URLEncoder.encode(world, StandardCharsets.UTF_8).replace("+", "%20");
        String url = String.format(HS_URL, encodedWorld, vocationId, catId, page);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " from Tibia highscores: " + url);
            }

            Document doc = Jsoup.parse(response.body(), url);
            List<HighscoreRow> out = new ArrayList<>();
            Elements rows = doc.select("table.TableContent tr");
            for (Element tr : rows) {
                Elements tds = tr.select("td");
                if (tds.size() < 3) {
                    continue;
                }

                int rank = parseIntSafe(tds.get(0).text());
                String name = tds.get(1).text().trim();
                long value = parseLongSafe(tds.get(tds.size() - 1).text());

                if (rank > 0 && !name.isBlank()) {
                    out.add(new HighscoreRow(rank, name, value));
                }
            }
            return out;
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch highscores: world=" + world
                    + ", category=" + category
                    + ", vocationId=" + vocationId
                    + ", page=" + page, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching highscores: world=" + world
                    + ", category=" + category
                    + ", vocationId=" + vocationId
                    + ", page=" + page, e);
        }
    }

    private int mapCategory(StatCategory c) {
        return switch (c) {
            case ACHIEVEMENTS -> 1;
            case AXE_FIGHTING -> 2;
            case BOSS_POINTS -> 15;
            case BOUNTY_POINTS_EARNED -> 16;
            case CHARM_POINTS -> 3;
            case CLUB_FIGHTING -> 4;
            case DISTANCE_FIGHTING -> 5;
            case DROME_SCORE -> 14;
            case EXPERIENCE -> 6;
            case FISHING -> 7;
            case FIST_FIGHTING -> 8;
            case GOSHNARS_TAINT -> 9;
            case LOYALTY_POINTS -> 10;
            case MAGIC_LEVEL -> 11;
            case SHIELDING -> 12;
            case SWORD_FIGHTING -> 13;
            case WEEKLY_TASKS_COMPLETED -> 17;
        };
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseLongSafe(String s) {
        try {
            String cleaned = s.replaceAll("[^0-9]", "");
            return cleaned.isBlank() ? 0L : Long.parseLong(cleaned);
        } catch (Exception e) {
            return 0L;
        }
    }
}
EOF_FILE

backup_file "src/main/resources/application-dev.yml"
mkdir -p "$ROOT/src/main/resources"
cat > '$ROOT/src/main/resources/application-dev.yml' <<'EOF_FILE'
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
      parallelism: 4
      page-window-size: 4
      request-parallelism: 12
      request-max-attempts: 4
      retry-base-delay-ms: 1500
      retry-max-delay-ms: 30000
      forbidden-cooldown-ms: 45000
      request-jitter-ms: 300
  jwt:
    access-ttl-ms: 900000       # 15 minutes
    refresh-ttl-ms: 1209600000  # 14 days
EOF_FILE

echo "Applied highscore 403/backoff tuning. Backup created at: $BACKUP_DIR"
