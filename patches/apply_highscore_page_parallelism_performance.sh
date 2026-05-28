#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${1:-.}"
cd "$PROJECT_ROOT"

if [ ! -f "pom.xml" ]; then
  echo "Erro: rode este script na raiz do projeto TibiaChrono, onde fica o pom.xml" >&2
  exit 1
fi

BACKUP_DIR=".tibiachrono-highscore-performance-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"

backup_file() {
  local file="$1"
  if [ -f "$file" ]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$file" "$BACKUP_DIR/$file"
  fi
}

backup_file "src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java"
backup_file "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java"
backup_file "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java"
backup_file "src/main/resources/application-dev.yml"

python3 - <<'PYFILES'
from pathlib import Path

Path('src/main/java/com/nathan/tibiastats/config').mkdir(parents=True, exist_ok=True)
Path('src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java').write_text(r'''package com.nathan.tibiastats.config;

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
    private int pageDelayMs = 100;
    private int worldLimit = 0;
    private int scopesPerRun = 0;
    private int parallelism = 8;

    /**
     * How many pages from the same scope may be fetched in one parallel window.
     * This is the biggest performance lever because the old scraper fetched pages 1..N sequentially.
     */
    private int pageWindowSize = 8;

    /**
     * Global cap for simultaneous HTTP requests across every scope and page window.
     * Keep this finite to avoid accidentally hammering tibia.com.
     */
    private int requestParallelism = 24;

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
''')

Path('src/main/java/com/nathan/tibiastats/application/service').mkdir(parents=True, exist_ok=True)
Path('src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java').write_text(r'''package com.nathan.tibiastats.application.service;

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
import java.util.concurrent.atomic.AtomicBoolean;
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
                "[HIGHSCORE_SCRAPER] Starting run: selectedScopes={}, scopesPerRun={}, allScopesPerRun={}, scopeParallelism={}, requestParallelism={}, pageWindowSize={}, maxPages={}, pageDelayMs={}, worlds={}, categories={}, vocations={}",
                scopes.size(),
                properties.getScopesPerRun(),
                properties.isAllScopesPerRun(),
                properties.getParallelism(),
                properties.getRequestParallelism(),
                properties.getPageWindowSize(),
                properties.getMaxPages(),
                properties.getPageDelayMs(),
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
        requestSemaphore.acquire();
        try {
            throttleRequest();
            List<HighscorePort.HighscoreRow> rows = highscorePort.fetchHighscores(
                    scope.worldName(),
                    scope.category(),
                    scope.vocationFilterId(),
                    page
            );
            return new PageResult(page, rows);
        } finally {
            requestSemaphore.release();
        }
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

    private void throttleRequest() {
        int delay = properties.getPageDelayMs();
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while throttling highscore request", ex);
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
''')

Path('src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper').mkdir(parents=True, exist_ok=True)
Path('src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java').write_text(r'''package com.nathan.tibiastats.infrastructure.adapter.scraper;

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
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; TibiaChrono/1.0; +https://localhost)";
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
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
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
''')
PYFILES

python3 - <<'PYYAML'
from pathlib import Path
path = Path('src/main/resources/application-dev.yml')
text = path.read_text()

text = text.replace('''    show-sql: true
    properties:
      hibernate:
        format_sql: true''', '''    show-sql: false
    properties:
      hibernate:
        format_sql: false''')

for old, new in {
    'org.hibernate.SQL: DEBUG': 'org.hibernate.SQL: WARN',
    'org.springframework.web: DEBUG': 'org.springframework.web: INFO',
    'org.hibernate.type.descriptor.sql.BasicBinder: TRACE': 'org.hibernate.type.descriptor.sql.BasicBinder: WARN',
    'org.hibernate.orm.jdbc.bind: TRACE': 'org.hibernate.orm.jdbc.bind: WARN',
    'org.flywaydb.core: DEBUG': 'org.flywaydb.core: INFO',
    'org.hibernate.tool.hbm2ddl: DEBUG': 'org.hibernate.tool.hbm2ddl: INFO',
    'com.nathan.tibiastats: DEBUG': 'com.nathan.tibiastats: INFO',
}.items():
    text = text.replace(old, new)

lines = text.splitlines()
out = []
in_highscores = False
seen_page_window = False
seen_request_parallelism = False
for line in lines:
    stripped = line.strip()
    if line.startswith('    highscores:'):
        in_highscores = True
        seen_page_window = False
        seen_request_parallelism = False
        out.append(line)
        continue

    if in_highscores and (line.startswith('  jwt:') or line.startswith('    ') and stripped.endswith(':') and not line.startswith('    highscores:') and stripped.split(':')[0] not in {
        'enabled','cron','zone','run-on-startup','startup-delay-ms','categories','vocations','max-pages','page-delay-ms','world-limit','scopes-per-run','parallelism','page-window-size','request-parallelism'
    }):
        if not seen_page_window:
            out.append('      page-window-size: 8')
        if not seen_request_parallelism:
            out.append('      request-parallelism: 24')
        in_highscores = False
        out.append(line)
        continue

    if in_highscores:
        if stripped.startswith('page-delay-ms:'):
            out.append('      page-delay-ms: 100')
            continue
        if stripped.startswith('parallelism:'):
            out.append('      parallelism: 8')
            continue
        if stripped.startswith('page-window-size:'):
            out.append('      page-window-size: 8')
            seen_page_window = True
            continue
        if stripped.startswith('request-parallelism:'):
            out.append('      request-parallelism: 24')
            seen_request_parallelism = True
            continue

    out.append(line)

if in_highscores:
    if not seen_page_window:
        out.append('      page-window-size: 8')
    if not seen_request_parallelism:
        out.append('      request-parallelism: 24')

path.write_text('\n'.join(out) + '\n')
PYYAML

echo "Correção aplicada. Backup em: $BACKUP_DIR"
echo "Próximos passos sugeridos:"
echo "  docker compose -f docker-compose.dev.yml stop app"
echo "  make up-dev"
echo "Logs: docker compose -f docker-compose.dev.yml logs -f app | grep --line-buffered HIGHSCORE_SCRAPER"
