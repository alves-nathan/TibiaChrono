#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
if [ ! -f "$ROOT/pom.xml" ] || [ ! -d "$ROOT/src/main/java" ]; then
  echo "Execute este script na raiz do projeto TibiaChrono, onde fica o pom.xml" >&2
  exit 1
fi

TS="$(date +%Y%m%d%H%M%S)"
BACKUP_DIR="$ROOT/.tibiachrono-highscore-parallel-backup-$TS"
mkdir -p "$BACKUP_DIR"

backup_file() {
  local file="$1"
  if [ -f "$ROOT/$file" ]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$ROOT/$file" "$BACKUP_DIR/$file"
  fi
}

backup_file "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java"
backup_file "src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java"
backup_file "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java"
backup_file "src/main/java/com/nathan/tibiastats/domain/model/StatCategory.java"
backup_file "src/main/resources/application-dev.yml"
backup_file "docker-compose.dev.yml"

mkdir -p \
  "$ROOT/src/main/java/com/nathan/tibiastats/application/service" \
  "$ROOT/src/main/java/com/nathan/tibiastats/application/scheduler" \
  "$ROOT/src/main/java/com/nathan/tibiastats/config" \
  "$ROOT/src/main/java/com/nathan/tibiastats/domain/model" \
  "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper" \
  "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/persistence" \
  "$ROOT/src/main/resources/db/migration"

cat > "$ROOT/src/main/java/com/nathan/tibiastats/domain/model/StatCategory.java" <<'EOF'
package com.nathan.tibiastats.domain.model;

public enum StatCategory {
    ACHIEVEMENTS,
    AXE_FIGHTING,
    BOSS_POINTS,
    BOUNTY_POINTS_EARNED,
    CHARM_POINTS,
    CLUB_FIGHTING,
    DISTANCE_FIGHTING,
    DROME_SCORE,
    EXPERIENCE,
    FISHING,
    FIST_FIGHTING,
    GOSHNARS_TAINT,
    LOYALTY_POINTS,
    MAGIC_LEVEL,
    SHIELDING,
    SWORD_FIGHTING,
    WEEKLY_TASKS_COMPLETED
}
EOF

cat > "$ROOT/src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java" <<'EOF'
package com.nathan.tibiastats.config;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "tibiastats.scrape.highscores")
public class HighscoreScrapeProperties {
    private static final Logger log = LoggerFactory.getLogger(HighscoreScrapeProperties.class);

    private boolean enabled = true;
    private String cron = "0 0 7 * * *";
    private String categories = "EXPERIENCE";
    private String vocations = "0";
    private int maxPages = 100;
    private int pageDelayMs = 500;
    private int worldLimit = 0;
    private int scopesPerRun = 20;
    private int parallelism = 4;

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

    public int getScopesPerRun() {
        return Math.max(1, scopesPerRun);
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
        List<Integer> parsed = new ArrayList<>();
        for (String token : splitCsv(vocations)) {
            try {
                parsed.add(Integer.parseInt(token));
            } catch (NumberFormatException ex) {
                log.warn("[HIGHSCORE_SCRAPER] Ignoring invalid highscore vocation config: {}", token);
            }
        }
        return parsed.isEmpty() ? List.of(0) : parsed;
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
EOF

cat > "$ROOT/src/main/java/com/nathan/tibiastats/application/service/HighscoreScope.java" <<'EOF'
package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.StatCategory;

public record HighscoreScope(
        Integer worldId,
        String worldName,
        StatCategory category,
        int vocationFilterId
) {
    public String label() {
        return worldName + "/" + category + "/vocation=" + vocationFilterId;
    }
}
EOF

cat > "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/persistence/HighscoreScrapeStateRepository.java" <<'EOF'
package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.application.service.HighscoreScope;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class HighscoreScrapeStateRepository {
    private final JdbcTemplate jdbc;

    public HighscoreScrapeStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registerScopes(List<World> worlds, List<StatCategory> categories, List<Integer> vocationFilterIds) {
        String sql = """
            insert into highscore_scrape_scopes
                (world_id, world_name, category, vocation_filter_id, created_at, updated_at)
            values (?, ?, ?, ?, now(), now())
            on conflict (world_id, category, vocation_filter_id)
            do update set
                world_name = excluded.world_name,
                updated_at = now()
            """;

        List<Object[]> args = new ArrayList<>();
        for (World world : worlds) {
            for (StatCategory category : categories) {
                for (Integer vocationFilterId : vocationFilterIds) {
                    args.add(new Object[]{world.getId(), world.getName(), category.name(), vocationFilterId});
                }
            }
        }
        if (!args.isEmpty()) {
            jdbc.batchUpdate(sql, args);
        }
    }

    public List<HighscoreScope> findNextScopes(
            List<World> worlds,
            List<StatCategory> categories,
            List<Integer> vocationFilterIds,
            int limit
    ) {
        Set<Integer> allowedWorldIds = new HashSet<>();
        for (World world : worlds) {
            allowedWorldIds.add(world.getId());
        }
        Set<String> allowedCategories = new HashSet<>();
        for (StatCategory category : categories) {
            allowedCategories.add(category.name());
        }
        Set<Integer> allowedVocations = new HashSet<>(vocationFilterIds);

        List<HighscoreScope> all = jdbc.query("""
            select world_id, world_name, category, vocation_filter_id
            from highscore_scrape_scopes
            order by
                last_scraped_at asc nulls first,
                last_finished_at asc nulls first,
                world_name asc,
                category asc,
                vocation_filter_id asc
            """, this::mapScope);

        return all.stream()
                .filter(scope -> allowedWorldIds.contains(scope.worldId()))
                .filter(scope -> allowedCategories.contains(scope.category().name()))
                .filter(scope -> allowedVocations.contains(scope.vocationFilterId()))
                .limit(Math.max(1, limit))
                .toList();
    }

    public void markStarted(HighscoreScope scope) {
        jdbc.update("""
            update highscore_scrape_scopes
            set
                last_started_at = now(),
                last_status = 'RUNNING',
                last_error = null,
                updated_at = now()
            where world_id = ? and category = ? and vocation_filter_id = ?
            """, scope.worldId(), scope.category().name(), scope.vocationFilterId());
    }

    public void markFinished(HighscoreScope scope, String status, int pageCount, int rowCount, long durationMs, String error) {
        jdbc.update("""
            update highscore_scrape_scopes
            set
                last_finished_at = now(),
                last_scraped_at = now(),
                last_status = ?,
                last_page_count = ?,
                last_row_count = ?,
                last_duration_ms = ?,
                last_error = ?,
                updated_at = now()
            where world_id = ? and category = ? and vocation_filter_id = ?
            """, truncate(status, 50), pageCount, rowCount, durationMs, truncate(error, 4000),
                scope.worldId(), scope.category().name(), scope.vocationFilterId());
    }

    private HighscoreScope mapScope(ResultSet rs, int rowNum) throws SQLException {
        return new HighscoreScope(
                rs.getInt("world_id"),
                rs.getString("world_name"),
                StatCategory.valueOf(rs.getString("category")),
                rs.getInt("vocation_filter_id")
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
EOF

cat > "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/persistence/HighscoreStatRecordWriter.java" <<'EOF'
package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public class HighscoreStatRecordWriter {
    private final JdbcTemplate jdbc;

    public HighscoreStatRecordWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record HighscoreStatRow(
            Long characterId,
            Integer worldId,
            StatCategory category,
            int vocationFilterId,
            LocalDate date,
            long value,
            int rank,
            Instant scrapedAt
    ) {}

    @Transactional
    public int upsertBatch(List<HighscoreStatRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        String sql = """
            insert into character_statrecords
                (character_id, world_id, category, vocation_filter_id, date, value, rank, scraped_at)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (character_id, world_id, category, vocation_filter_id, date)
            do update set
                value = excluded.value,
                rank = excluded.rank,
                scraped_at = excluded.scraped_at
            """;

        int[][] affected = jdbc.batchUpdate(sql, rows, Math.min(rows.size(), 500), (ps, row) -> {
            ps.setLong(1, row.characterId());
            ps.setInt(2, row.worldId());
            ps.setString(3, row.category().name());
            ps.setInt(4, row.vocationFilterId());
            ps.setDate(5, Date.valueOf(row.date()));
            ps.setLong(6, row.value());
            ps.setInt(7, row.rank());
            ps.setTimestamp(8, Timestamp.from(row.scrapedAt()));
        });

        int total = 0;
        for (int[] batch : affected) {
            for (int count : batch) {
                if (count > 0) {
                    total += count;
                }
            }
        }
        return total;
    }
}
EOF

cat > "$ROOT/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java" <<'EOF'
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
                "[HIGHSCORE_SCRAPER] Starting run: scopes={}, parallelism={}, maxPages={}, pageDelayMs={}, worlds={}, categories={}, vocations={}",
                scopes.size(),
                properties.getParallelism(),
                properties.getMaxPages(),
                properties.getPageDelayMs(),
                worlds.size(),
                categories.size(),
                vocationFilterIds.size()
        );

        Semaphore semaphore = new Semaphore(properties.getParallelism());
        Map<String, Long> characterIdCache = new ConcurrentHashMap<>();
        List<Future<ScopeResult>> futures = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (HighscoreScope scope : scopes) {
                futures.add(executor.submit(() -> {
                    semaphore.acquire();
                    try {
                        return scrapeScope(scope, characterIdCache);
                    } finally {
                        semaphore.release();
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
                    "[HIGHSCORE_SCRAPER] Finished run: successScopes={}, emptyScopes={}, failedScopes={}, pages={}, rows={}, durationMs={}",
                    success,
                    empty,
                    failed,
                    totalPages,
                    totalRows,
                    Duration.between(startedAt, Instant.now()).toMillis()
            );
        }
    }

    private ScopeResult scrapeScope(HighscoreScope scope, Map<String, Long> characterIdCache) {
        Instant startedAt = Instant.now();
        stateRepository.markStarted(scope);
        log.info("[HIGHSCORE_SCRAPER] Scope started: {}", scope.label());

        int pages = 0;
        int rows = 0;
        try {
            LocalDate snapshotDate = LocalDate.now(SNAPSHOT_ZONE);
            Instant scrapedAt = Instant.now();

            for (int page = 1; page <= properties.getMaxPages(); page++) {
                List<HighscorePort.HighscoreRow> fetchedRows = highscorePort.fetchHighscores(
                        scope.worldName(),
                        scope.category(),
                        scope.vocationFilterId(),
                        page
                );

                if (fetchedRows.isEmpty()) {
                    break;
                }

                pages++;
                List<HighscoreStatRecordWriter.HighscoreStatRow> statRows = new ArrayList<>(fetchedRows.size());
                for (HighscorePort.HighscoreRow row : fetchedRows) {
                    String normalizedName = normalizeCharacterName(row.name());
                    if (normalizedName.isBlank()) {
                        continue;
                    }
                    Long characterId = resolveCharacterId(normalizedName, characterIdCache);
                    statRows.add(new HighscoreStatRecordWriter.HighscoreStatRow(
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

                rows += statRecordWriter.upsertBatch(statRows);
                log.debug("[HIGHSCORE_SCRAPER] Scope page saved: scope={}, page={}, rows={}", scope.label(), page, statRows.size());
                sleepBetweenPages();
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

    private void sleepBetweenPages() {
        int delay = properties.getPageDelayMs();
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting between highscore pages", ex);
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
}
EOF

cat > "$ROOT/src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java" <<'EOF'
package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.HighscoreService;
import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
                "[HIGHSCORE_SCRAPER] Scheduler configured: enabled={}, cron={}, categories={}, vocations={}, maxPages={}, pageDelayMs={}, worldLimit={}, scopesPerRun={}, parallelism={}",
                properties.isEnabled(),
                properties.getCron(),
                properties.getCategories(),
                properties.getVocations(),
                properties.getMaxPages(),
                properties.getPageDelayMs(),
                properties.getWorldLimit(),
                properties.getScopesPerRun(),
                properties.getParallelism()
        );
    }

    @Scheduled(cron = "${tibiastats.scrape.highscores.cron:0 0 7 * * *}")
    public void run() {
        log.info("[HIGHSCORE_SCRAPER] Scheduler tick started");
        service.updateAllHighscores();
    }
}
EOF

cat > "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java" <<'EOF'
package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.port.HighscorePort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class JsoupHighscoreAdapter implements HighscorePort {
    private static final String HS_URL = "https://www.tibia.com/community/?subtopic=highscores&world=%s&beprotection=-1&profession=%d&category=%d&currentpage=%d";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; TibiaChrono/1.0; +https://localhost)";
    private static final int TIMEOUT_MS = 20_000;

    @Override
    public List<HighscoreRow> fetchHighscores(String world, StatCategory category, int vocationId, int page) {
        int catId = mapCategory(category);
        String encodedWorld = URLEncoder.encode(world, StandardCharsets.UTF_8).replace("+", "%20");
        String url = String.format(HS_URL, encodedWorld, vocationId, catId, page);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

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
EOF

python3 - <<'PY'
from pathlib import Path
root = Path.cwd()
# Create next Flyway migration safely.
migration_dir = root / "src/main/resources/db/migration"
versions = []
for p in migration_dir.glob("V*__*.sql"):
    stem = p.name.split("__", 1)[0][1:]
    try:
        versions.append(int(stem))
    except ValueError:
        pass
next_version = (max(versions) + 1) if versions else 1
migration = migration_dir / f"V{next_version}__highscore_parallel_scopes.sql"
if not migration.exists():
    migration.write_text("""\
ALTER TABLE character_statrecords
    ADD COLUMN IF NOT EXISTS vocation_filter_id INTEGER NOT NULL DEFAULT 0;

UPDATE character_statrecords
SET vocation_filter_id = 0
WHERE vocation_filter_id IS NULL;

ALTER TABLE character_statrecords
    ALTER COLUMN vocation_filter_id SET DEFAULT 0;

ALTER TABLE character_statrecords
    ALTER COLUMN vocation_filter_id SET NOT NULL;

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT c.conname
    INTO constraint_name
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    WHERE t.relname = 'character_statrecords'
      AND c.contype = 'c'
      AND pg_get_constraintdef(c.oid) ILIKE '%category%'
    LIMIT 1;

    IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE character_statrecords DROP CONSTRAINT ' || quote_ident(constraint_name);
    END IF;
END $$;

ALTER TABLE character_statrecords
    ADD CONSTRAINT chk_character_statrecords_category CHECK (
        category IN (
            'ACHIEVEMENTS',
            'AXE_FIGHTING',
            'BOSS_POINTS',
            'BOUNTY_POINTS_EARNED',
            'CHARM_POINTS',
            'CLUB_FIGHTING',
            'DISTANCE_FIGHTING',
            'DROME_SCORE',
            'EXPERIENCE',
            'FISHING',
            'FIST_FIGHTING',
            'GOSHNARS_TAINT',
            'LOYALTY_POINTS',
            'MAGIC_LEVEL',
            'SHIELDING',
            'SWORD_FIGHTING',
            'WEEKLY_TASKS_COMPLETED'
        )
    );

WITH duplicated AS (
    SELECT
        id,
        row_number() OVER (
            PARTITION BY character_id, world_id, category, vocation_filter_id, date
            ORDER BY scraped_at DESC, id DESC
        ) AS rn
    FROM character_statrecords
)
DELETE FROM character_statrecords csr
USING duplicated d
WHERE csr.id = d.id
  AND d.rn > 1;

DROP INDEX IF EXISTS ux_character_statrecords_daily_scope;
DROP INDEX IF EXISTS ux_character_statrecords_daily;

CREATE UNIQUE INDEX IF NOT EXISTS ux_character_statrecords_daily_scope
ON character_statrecords(character_id, world_id, category, vocation_filter_id, date);

CREATE INDEX IF NOT EXISTS idx_csr_world_cat_voc_date
ON character_statrecords(world_id, category, vocation_filter_id, date);

CREATE TABLE IF NOT EXISTS highscore_scrape_scopes (
    id BIGSERIAL PRIMARY KEY,
    world_id INTEGER NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    world_name TEXT NOT NULL,
    category TEXT NOT NULL,
    vocation_filter_id INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_started_at TIMESTAMP WITH TIME ZONE,
    last_finished_at TIMESTAMP WITH TIME ZONE,
    last_scraped_at TIMESTAMP WITH TIME ZONE,
    last_status TEXT,
    last_page_count INTEGER,
    last_row_count INTEGER,
    last_duration_ms BIGINT,
    last_error TEXT,
    UNIQUE(world_id, category, vocation_filter_id)
);

CREATE INDEX IF NOT EXISTS idx_highscore_scopes_next
ON highscore_scrape_scopes(last_scraped_at NULLS FIRST, last_finished_at NULLS FIRST, world_name, category, vocation_filter_id);
""")
print(f"Created migration: {migration.name}")

# Update application-dev.yml highscore block without touching other scraper blocks.
appdev = root / "src/main/resources/application-dev.yml"
if appdev.exists():
    lines = appdev.read_text().splitlines()
    block = [
        "    highscores:",
        "      enabled: true",
        "      cron: \"0 */5 * * * *\"",
        "      categories: \"ACHIEVEMENTS,AXE_FIGHTING,BOSS_POINTS,BOUNTY_POINTS_EARNED,CHARM_POINTS,CLUB_FIGHTING,DISTANCE_FIGHTING,DROME_SCORE,EXPERIENCE,FISHING,FIST_FIGHTING,GOSHNARS_TAINT,LOYALTY_POINTS,MAGIC_LEVEL,SHIELDING,SWORD_FIGHTING,WEEKLY_TASKS_COMPLETED\"",
        "      vocations: \"0,1,2,3,4,5,6\"",
        "      max-pages: 100",
        "      page-delay-ms: 500",
        "      world-limit: 0",
        "      scopes-per-run: 20",
        "      parallelism: 4",
    ]

    def indent_count(s):
        return len(s) - len(s.lstrip(' '))

    # Find tibiastats -> scrape -> highscores.
    scrape_start = None
    for i, line in enumerate(lines):
        if line.strip() == "scrape:" and indent_count(line) == 2:
            # Ensure previous top-level tibiastats exists above, but keep simple.
            scrape_start = i
            break
    if scrape_start is None:
        # Append full tibiastats block if no scrape block exists.
        if lines and lines[-1].strip():
            lines.append("")
        lines.extend(["tibiastats:", "  scrape:", *block])
    else:
        # End of scrape block is first non-empty line after scrape_start with indent <= 2.
        scrape_end = len(lines)
        for j in range(scrape_start + 1, len(lines)):
            if lines[j].strip() and indent_count(lines[j]) <= 2:
                scrape_end = j
                break
        high_start = None
        for j in range(scrape_start + 1, scrape_end):
            if lines[j].strip() == "highscores:" and indent_count(lines[j]) == 4:
                high_start = j
                break
        if high_start is not None:
            high_end = scrape_end
            for j in range(high_start + 1, scrape_end):
                if lines[j].strip() and indent_count(lines[j]) <= 4:
                    high_end = j
                    break
            lines = lines[:high_start] + block + lines[high_end:]
        else:
            lines = lines[:scrape_end] + block + lines[scrape_end:]
    appdev.write_text("\n".join(lines) + "\n")

# Remove highscore-specific env vars from docker-compose.dev.yml so application-dev.yml is the source of truth.
compose = root / "docker-compose.dev.yml"
if compose.exists():
    new_lines = []
    for line in compose.read_text().splitlines():
        if "TIBIASTATS_SCRAPE_HIGHSCORES_" in line:
            continue
        new_lines.append(line)
    compose.write_text("\n".join(new_lines) + "\n")
PY

echo "Highscore parallel scope scraper applied. Backup created at: $BACKUP_DIR"
echo "Next steps: make down-dev && make up-dev"
