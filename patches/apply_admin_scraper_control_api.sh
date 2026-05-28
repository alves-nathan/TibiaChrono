#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

if [[ ! -f "pom.xml" || ! -d "src/main/java/com/nathan/tibiastats" ]]; then
  echo "ERROR: execute este script na raiz do projeto TibiaChrono." >&2
  exit 1
fi

if [[ -f "src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/AdminScraperController.java" ]] \
   && grep -q "class AdminScraperService" "src/main/java/com/nathan/tibiastats/application/service/AdminScraperService.java" 2>/dev/null \
   && grep -q "public ScrapeJobResult updateHighscores" "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java" \
   && grep -q "ScrapeJobService.HIGHSCORE_SCRAPER" "src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java"; then
  echo "Admin scraper control API já parece aplicada. Nada a fazer."
  exit 0
fi

BACKUP_DIR=".tibiachrono-admin-scraper-control-api-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"
backup_file() {
  local file="$1"
  if [[ -f "$file" ]]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$file" "$BACKUP_DIR/$file"
  fi
}

backup_file "src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java"
backup_file "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java"
backup_file "src/main/java/com/nathan/tibiastats/config/AppProperties.java"
backup_file "src/main/java/com/nathan/tibiastats/application/service/AdminScraperService.java"
backup_file "src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/AdminScraperController.java"

tmp_patch="$(mktemp)"
trap 'rm -f "$tmp_patch"' EXIT
cat > "$tmp_patch" <<'PATCH'
diff -ruN a/src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java b/src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java
--- a/src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java	2026-05-28 12:55:45.702443880 +0000
+++ b/src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java	2026-05-28 12:55:45.721839144 +0000
@@ -1,6 +1,8 @@
 package com.nathan.tibiastats.application.scheduler;
 
 import com.nathan.tibiastats.application.service.HighscoreService;
+import com.nathan.tibiastats.application.service.ScrapeJobResult;
+import com.nathan.tibiastats.application.service.ScrapeJobService;
 import com.nathan.tibiastats.config.HighscoreScrapeProperties;
 import jakarta.annotation.PostConstruct;
 import org.slf4j.Logger;
@@ -21,10 +23,14 @@
 
     private final HighscoreService service;
     private final HighscoreScrapeProperties properties;
+    private final ScrapeJobService scrapeJobService;
 
-    public HighscoreScrapeScheduler(HighscoreService service, HighscoreScrapeProperties properties) {
+    public HighscoreScrapeScheduler(HighscoreService service,
+                                    HighscoreScrapeProperties properties,
+                                    ScrapeJobService scrapeJobService) {
         this.service = service;
         this.properties = properties;
+        this.scrapeJobService = scrapeJobService;
     }
 
     @PostConstruct
@@ -107,7 +113,14 @@
             return;
         }
 
-        log.info("[HIGHSCORE_SCRAPER] Plan tick started: plan={}, trigger={}", planName, trigger);
-        service.updateHighscores(planName, plan);
+        Long jobId = scrapeJobService.start(ScrapeJobService.HIGHSCORE_SCRAPER);
+        log.info("[HIGHSCORE_SCRAPER] Plan tick started: plan={}, trigger={}, jobId={}", planName, trigger, jobId);
+        try {
+            ScrapeJobResult result = service.updateHighscores(planName, plan);
+            scrapeJobService.finishSuccess(jobId, result);
+        } catch (Exception ex) {
+            scrapeJobService.finishFailure(jobId, ScrapeJobResult.empty(), ex);
+            throw ex;
+        }
     }
 }
diff -ruN a/src/main/java/com/nathan/tibiastats/application/service/AdminScraperService.java b/src/main/java/com/nathan/tibiastats/application/service/AdminScraperService.java
--- a/src/main/java/com/nathan/tibiastats/application/service/AdminScraperService.java	1970-01-01 00:00:00.000000000 +0000
+++ b/src/main/java/com/nathan/tibiastats/application/service/AdminScraperService.java	2026-05-28 12:55:45.887931716 +0000
@@ -0,0 +1,313 @@
+package com.nathan.tibiastats.application.service;
+
+import com.nathan.tibiastats.config.AppProperties;
+import com.nathan.tibiastats.config.GuildScrapeProperties;
+import com.nathan.tibiastats.config.HighscoreScrapeProperties;
+import com.nathan.tibiastats.infrastructure.persistence.HighscoreScrapeStateRepository;
+import org.springframework.stereotype.Service;
+
+import java.time.Instant;
+import java.util.ArrayList;
+import java.util.List;
+import java.util.Map;
+import java.util.concurrent.ConcurrentHashMap;
+import java.util.concurrent.atomic.AtomicBoolean;
+import java.util.function.Supplier;
+
+@Service
+public class AdminScraperService {
+    private static final String WORLDS_KEY = "worlds";
+    private static final String CHARACTER_DETAILS_KEY = "character-details";
+    private static final String GUILDS_KEY = "guilds";
+    private static final String HIGHSCORE_KEY_PREFIX = "highscores:";
+
+    private final ScrapeService scrapeService;
+    private final CharacterDetailsService characterDetailsService;
+    private final GuildScrapeService guildScrapeService;
+    private final HighscoreService highscoreService;
+    private final ScrapeJobService scrapeJobService;
+    private final ApiQueryService queries;
+    private final AppProperties appProperties;
+    private final GuildScrapeProperties guildProperties;
+    private final HighscoreScrapeProperties highscoreProperties;
+    private final Map<String, AtomicBoolean> manualRuns = new ConcurrentHashMap<>();
+
+    public AdminScraperService(ScrapeService scrapeService,
+                               CharacterDetailsService characterDetailsService,
+                               GuildScrapeService guildScrapeService,
+                               HighscoreService highscoreService,
+                               ScrapeJobService scrapeJobService,
+                               ApiQueryService queries,
+                               AppProperties appProperties,
+                               GuildScrapeProperties guildProperties,
+                               HighscoreScrapeProperties highscoreProperties) {
+        this.scrapeService = scrapeService;
+        this.characterDetailsService = characterDetailsService;
+        this.guildScrapeService = guildScrapeService;
+        this.highscoreService = highscoreService;
+        this.scrapeJobService = scrapeJobService;
+        this.queries = queries;
+        this.appProperties = appProperties;
+        this.guildProperties = guildProperties;
+        this.highscoreProperties = highscoreProperties;
+    }
+
+    public ScraperStatusResponse status() {
+        List<ScraperStatus> scrapers = new ArrayList<>();
+        scrapers.add(new ScraperStatus(
+                WORLDS_KEY,
+                appProperties.getWorlds().isEnabled(),
+                "fixedRateMs=" + appProperties.getWorlds().getRateMs(),
+                isManualRunActive(WORLDS_KEY),
+                hasRunningJob(ScrapeJobService.WORLD_SCRAPER),
+                latestJob(ScrapeJobService.WORLD_SCRAPER),
+                latestRunningJob(ScrapeJobService.WORLD_SCRAPER)
+        ));
+        scrapers.add(new ScraperStatus(
+                CHARACTER_DETAILS_KEY,
+                appProperties.getCharacterDetails().isEnabled(),
+                "fixedDelayMs=" + appProperties.getCharacterDetails().getRateMs()
+                        + ", initialDelayMs=" + appProperties.getCharacterDetails().getInitialDelayMs()
+                        + ", batchSize=" + appProperties.getCharacterDetails().getBatchSize(),
+                isManualRunActive(CHARACTER_DETAILS_KEY),
+                hasRunningJob(ScrapeJobService.CHARACTER_DETAILS_SCRAPER),
+                latestJob(ScrapeJobService.CHARACTER_DETAILS_SCRAPER),
+                latestRunningJob(ScrapeJobService.CHARACTER_DETAILS_SCRAPER)
+        ));
+        scrapers.add(new ScraperStatus(
+                GUILDS_KEY,
+                guildProperties.isEnabled(),
+                "fixedDelayMs=" + guildProperties.getRateMs()
+                        + ", initialDelayMs=" + guildProperties.getInitialDelayMs()
+                        + ", listEnabled=" + guildProperties.isListEnabled()
+                        + ", detailsEnabled=" + guildProperties.isDetailsEnabled(),
+                isManualRunActive(GUILDS_KEY),
+                hasRunningJob(ScrapeJobService.GUILD_SCRAPER),
+                latestJob(ScrapeJobService.GUILD_SCRAPER),
+                latestRunningJob(ScrapeJobService.GUILD_SCRAPER)
+        ));
+        scrapers.add(new ScraperStatus(
+                "highscores",
+                highscoreProperties.isEnabled(),
+                "plans=" + highscoreProperties.effectivePlans().size(),
+                hasAnyHighscoreManualRunActive(),
+                highscoreService.isRunning() || hasRunningJob(ScrapeJobService.HIGHSCORE_SCRAPER),
+                latestJob(ScrapeJobService.HIGHSCORE_SCRAPER),
+                latestRunningJob(ScrapeJobService.HIGHSCORE_SCRAPER)
+        ));
+
+        List<HighscorePlanStatus> highscorePlans = highscoreProperties.effectivePlans().entrySet().stream()
+                .map(entry -> toHighscorePlanStatus(entry.getKey(), entry.getValue()))
+                .toList();
+
+        return new ScraperStatusResponse(scrapers, highscorePlans, highscoreBackoffStatus());
+    }
+
+    public HighscoreBackoffStatus highscoreBackoffStatus() {
+        return toBackoffStatus(highscoreService.getHttpBackoffState());
+    }
+
+    public HighscoreBackoffStatus resetHighscoreBackoff() {
+        return toBackoffStatus(highscoreService.resetHttpBackoffManually());
+    }
+
+    public ManualRunResponse triggerWorlds() {
+        return triggerManualRun(
+                WORLDS_KEY,
+                ScrapeJobService.WORLD_SCRAPER,
+                null,
+                scrapeService::updateAllWorlds
+        );
+    }
+
+    public ManualRunResponse triggerCharacterDetails() {
+        return triggerManualRun(
+                CHARACTER_DETAILS_KEY,
+                ScrapeJobService.CHARACTER_DETAILS_SCRAPER,
+                null,
+                characterDetailsService::updateMissingDetailsBatch
+        );
+    }
+
+    public ManualRunResponse triggerGuilds() {
+        return triggerManualRun(
+                GUILDS_KEY,
+                ScrapeJobService.GUILD_SCRAPER,
+                null,
+                guildScrapeService::updateKnownGuilds
+        );
+    }
+
+    public ManualRunResponse triggerHighscorePlan(String planName) {
+        String normalizedPlanName = normalizePlanName(planName);
+        HighscoreScrapeProperties.Plan plan = highscoreProperties.effectivePlans().get(normalizedPlanName);
+        if (plan == null) {
+            throw new IllegalArgumentException("Unknown highscore plan: " + normalizedPlanName);
+        }
+
+        return triggerManualRun(
+                HIGHSCORE_KEY_PREFIX + normalizedPlanName,
+                ScrapeJobService.HIGHSCORE_SCRAPER,
+                normalizedPlanName,
+                () -> highscoreService.updateHighscores(normalizedPlanName, plan)
+        );
+    }
+
+    private ManualRunResponse triggerManualRun(String runKey,
+                                               String jobName,
+                                               String planName,
+                                               Supplier<ScrapeJobResult> worker) {
+        AtomicBoolean running = manualRuns.computeIfAbsent(runKey, ignored -> new AtomicBoolean(false));
+        if (!running.compareAndSet(false, true)) {
+            throw new IllegalStateException("Manual scraper run already active: " + runKey);
+        }
+
+        Instant acceptedAt = Instant.now();
+        Thread.startVirtualThread(() -> {
+            Long jobId = null;
+            try {
+                jobId = scrapeJobService.start(jobName);
+                ScrapeJobResult result = worker.get();
+                scrapeJobService.finishSuccess(jobId, result == null ? ScrapeJobResult.empty() : result);
+            } catch (Exception ex) {
+                if (Thread.currentThread().isInterrupted()) {
+                    Thread.currentThread().interrupt();
+                }
+                if (jobId != null) {
+                    scrapeJobService.finishFailure(jobId, ScrapeJobResult.empty(), ex);
+                }
+            } finally {
+                running.set(false);
+            }
+        });
+
+        return new ManualRunResponse(runKey, planName, true, "Manual scraper run accepted", acceptedAt);
+    }
+
+    private HighscorePlanStatus toHighscorePlanStatus(String planName, HighscoreScrapeProperties.Plan plan) {
+        return new HighscorePlanStatus(
+                planName,
+                highscoreProperties.isEnabled() && plan.isEnabled(),
+                plan.getCron(),
+                plan.getZone(),
+                plan.isRunOnStartup(),
+                plan.getStartupDelayMs(),
+                plan.categoryList().stream().map(Enum::name).toList(),
+                plan.vocationFilterIds(),
+                plan.getWorldLimit(),
+                plan.getScopesPerRun(),
+                plan.getMaxPages(),
+                plan.getParallelism(),
+                plan.getRequestParallelism(),
+                plan.getRequestMinIntervalMs(),
+                plan.getForbiddenInitialCooldownMs(),
+                plan.getForbiddenMaxCooldownMs(),
+                plan.getForbiddenCooldownMultiplier(),
+                isManualRunActive(HIGHSCORE_KEY_PREFIX + planName)
+        );
+    }
+
+    private HighscoreBackoffStatus toBackoffStatus(HighscoreScrapeStateRepository.HighscoreHttpBackoffState state) {
+        Instant now = Instant.now();
+        if (state == null) {
+            return new HighscoreBackoffStatus(false, null, 0, 0, 0, null, null, null, null);
+        }
+        return new HighscoreBackoffStatus(
+                state.isActive(now),
+                state.cooldownUntil(),
+                state.remainingMs(now),
+                state.consecutiveFailures(),
+                state.currentCooldownMs(),
+                state.lastStatus(),
+                state.lastReason(),
+                state.lastFailureAt(),
+                state.lastSuccessAt()
+        );
+    }
+
+    private boolean hasAnyHighscoreManualRunActive() {
+        return manualRuns.entrySet().stream()
+                .anyMatch(entry -> entry.getKey().startsWith(HIGHSCORE_KEY_PREFIX) && entry.getValue().get());
+    }
+
+    private boolean isManualRunActive(String runKey) {
+        AtomicBoolean running = manualRuns.get(runKey);
+        return running != null && running.get();
+    }
+
+    private boolean hasRunningJob(String jobName) {
+        return latestRunningJob(jobName) != null;
+    }
+
+    private ApiQueryService.ScrapeJobView latestJob(String jobName) {
+        return queries.findScrapeJobs(jobName, null, 1).stream().findFirst().orElse(null);
+    }
+
+    private ApiQueryService.ScrapeJobView latestRunningJob(String jobName) {
+        return queries.findScrapeJobs(jobName, "RUNNING", 1).stream().findFirst().orElse(null);
+    }
+
+    private String normalizePlanName(String planName) {
+        if (planName == null || planName.isBlank()) {
+            throw new IllegalArgumentException("Highscore plan name is required");
+        }
+        return planName.trim();
+    }
+
+    public record ScraperStatusResponse(
+            List<ScraperStatus> scrapers,
+            List<HighscorePlanStatus> highscorePlans,
+            HighscoreBackoffStatus highscoreBackoff
+    ) {}
+
+    public record ScraperStatus(
+            String name,
+            boolean enabled,
+            String schedule,
+            boolean manualRunActive,
+            boolean running,
+            ApiQueryService.ScrapeJobView latestJob,
+            ApiQueryService.ScrapeJobView latestRunningJob
+    ) {}
+
+    public record HighscorePlanStatus(
+            String name,
+            boolean enabled,
+            String cron,
+            String zone,
+            boolean runOnStartup,
+            long startupDelayMs,
+            List<String> categories,
+            List<Integer> vocationFilterIds,
+            int worldLimit,
+            int scopesPerRun,
+            int maxPages,
+            int parallelism,
+            int requestParallelism,
+            int requestMinIntervalMs,
+            long forbiddenInitialCooldownMs,
+            long forbiddenMaxCooldownMs,
+            double forbiddenCooldownMultiplier,
+            boolean manualRunActive
+    ) {}
+
+    public record HighscoreBackoffStatus(
+            boolean active,
+            Instant cooldownUntil,
+            long remainingMs,
+            int consecutiveFailures,
+            long currentCooldownMs,
+            String lastStatus,
+            String lastReason,
+            Instant lastFailureAt,
+            Instant lastSuccessAt
+    ) {}
+
+    public record ManualRunResponse(
+            String scraper,
+            String planName,
+            boolean accepted,
+            String message,
+            Instant acceptedAt
+    ) {}
+}
diff -ruN a/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java b/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java
--- a/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java	2026-05-28 12:55:45.620939070 +0000
+++ b/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java	2026-05-28 12:55:45.637773018 +0000
@@ -70,38 +70,53 @@
         this.statRecordWriter = statRecordWriter;
     }
 
-    public void updateAllHighscores() {
-        updateHighscores("default", properties.toLegacyPlan());
+    public ScrapeJobResult updateAllHighscores() {
+        return updateHighscores("default", properties.toLegacyPlan());
     }
 
-    public void updateHighscores(String planName, HighscoreScrapeProperties.Plan plan) {
+    public ScrapeJobResult updateHighscores(String planName, HighscoreScrapeProperties.Plan plan) {
         if (!properties.isEnabled()) {
             log.info("[HIGHSCORE_SCRAPER] Skipping run because highscores.enabled=false: plan={}", planName);
-            return;
+            return ScrapeJobResult.empty();
         }
         if (plan == null || !plan.isEnabled()) {
             log.info("[HIGHSCORE_SCRAPER] Skipping disabled highscore plan: plan={}", planName);
-            return;
+            return ScrapeJobResult.empty();
         }
 
         if (isHttpBackoffActive(planName)) {
-            return;
+            return ScrapeJobResult.empty();
         }
 
         if (!running.compareAndSet(false, true)) {
             log.warn("[HIGHSCORE_SCRAPER] Previous highscore run is still active. Skipping this tick: plan={}", planName);
-            return;
+            return ScrapeJobResult.empty();
         }
 
         Instant startedAt = Instant.now();
         try {
-            runIncrementalHighscoreScrape(startedAt, planName, plan);
+            return runIncrementalHighscoreScrape(startedAt, planName, plan);
         } finally {
             running.set(false);
         }
     }
 
-    private void runIncrementalHighscoreScrape(Instant startedAt, String planName, HighscoreScrapeProperties.Plan plan) {
+    public HighscoreScrapeStateRepository.HighscoreHttpBackoffState getHttpBackoffState() {
+        return stateRepository.getHttpBackoffState();
+    }
+
+    public HighscoreScrapeStateRepository.HighscoreHttpBackoffState resetHttpBackoffManually() {
+        stateRepository.resetHttpBackoffAfterSuccess();
+        globalHttpCooldownUntilMs.set(0);
+        lastCooldownLogAtMs.set(0);
+        return stateRepository.getHttpBackoffState();
+    }
+
+    public boolean isRunning() {
+        return running.get();
+    }
+
+    private ScrapeJobResult runIncrementalHighscoreScrape(Instant startedAt, String planName, HighscoreScrapeProperties.Plan plan) {
         List<World> worlds = worldRepository.findAll().stream()
                 .sorted(Comparator.comparing(World::getName, String.CASE_INSENSITIVE_ORDER))
                 .limit(plan.getWorldLimit() > 0 ? plan.getWorldLimit() : Long.MAX_VALUE)
@@ -111,7 +126,7 @@
 
         if (worlds.isEmpty()) {
             log.warn("[HIGHSCORE_SCRAPER] No worlds found. Run the world scraper first.");
-            return;
+            return ScrapeJobResult.empty();
         }
 
         stateRepository.registerScopes(worlds, categories, vocationFilterIds);
@@ -124,7 +139,7 @@
 
         if (scopes.isEmpty()) {
             log.info("[HIGHSCORE_SCRAPER] No eligible highscore scopes found.");
-            return;
+            return ScrapeJobResult.empty();
         }
 
         log.info(
@@ -223,6 +238,7 @@
                     characterIdCache.size(),
                     rateLimited.get()
             );
+            return ScrapeJobResult.of(success + empty + failed, 0, totalRows, failed);
         }
     }
 
diff -ruN a/src/main/java/com/nathan/tibiastats/config/AppProperties.java b/src/main/java/com/nathan/tibiastats/config/AppProperties.java
--- a/src/main/java/com/nathan/tibiastats/config/AppProperties.java	2026-05-28 12:55:45.793175760 +0000
+++ b/src/main/java/com/nathan/tibiastats/config/AppProperties.java	2026-05-28 12:55:45.813280824 +0000
@@ -11,7 +11,12 @@
     private CharacterDetails characterDetails = new CharacterDetails();
 
     public static class Worlds {
+        private boolean enabled = true;
         private long rateMs = 60000L;
+
+        public boolean isEnabled() { return enabled; }
+        public void setEnabled(boolean enabled) { this.enabled = enabled; }
+
         public long getRateMs(){return rateMs;}
         public void setRateMs(long v){this.rateMs=v;}
     }
diff -ruN a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/AdminScraperController.java b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/AdminScraperController.java
--- a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/AdminScraperController.java	1970-01-01 00:00:00.000000000 +0000
+++ b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/AdminScraperController.java	2026-05-28 12:55:45.956920371 +0000
@@ -0,0 +1,71 @@
+package com.nathan.tibiastats.infrastructure.adapter.web.rest;
+
+import com.nathan.tibiastats.application.service.AdminScraperService;
+import org.springframework.http.HttpStatus;
+import org.springframework.http.ResponseEntity;
+import org.springframework.web.bind.annotation.GetMapping;
+import org.springframework.web.bind.annotation.PathVariable;
+import org.springframework.web.bind.annotation.PostMapping;
+import org.springframework.web.bind.annotation.RequestMapping;
+import org.springframework.web.bind.annotation.RestController;
+import org.springframework.web.server.ResponseStatusException;
+
+@RestController
+@RequestMapping("/api/admin/scrapers")
+public class AdminScraperController {
+    private final AdminScraperService adminScrapers;
+
+    public AdminScraperController(AdminScraperService adminScrapers) {
+        this.adminScrapers = adminScrapers;
+    }
+
+    @GetMapping("/status")
+    public AdminScraperService.ScraperStatusResponse status() {
+        return adminScrapers.status();
+    }
+
+    @GetMapping("/highscores/backoff")
+    public AdminScraperService.HighscoreBackoffStatus highscoreBackoff() {
+        return adminScrapers.highscoreBackoffStatus();
+    }
+
+    @PostMapping("/highscores/backoff/reset")
+    public AdminScraperService.HighscoreBackoffStatus resetHighscoreBackoff() {
+        return adminScrapers.resetHighscoreBackoff();
+    }
+
+    @PostMapping("/worlds/run")
+    public ResponseEntity<AdminScraperService.ManualRunResponse> runWorlds() {
+        return accepted(() -> adminScrapers.triggerWorlds());
+    }
+
+    @PostMapping("/character-details/run")
+    public ResponseEntity<AdminScraperService.ManualRunResponse> runCharacterDetails() {
+        return accepted(() -> adminScrapers.triggerCharacterDetails());
+    }
+
+    @PostMapping("/guilds/run")
+    public ResponseEntity<AdminScraperService.ManualRunResponse> runGuilds() {
+        return accepted(() -> adminScrapers.triggerGuilds());
+    }
+
+    @PostMapping("/highscores/plans/{planName}/run")
+    public ResponseEntity<AdminScraperService.ManualRunResponse> runHighscorePlan(@PathVariable String planName) {
+        return accepted(() -> adminScrapers.triggerHighscorePlan(planName));
+    }
+
+    private ResponseEntity<AdminScraperService.ManualRunResponse> accepted(ManualRunTrigger trigger) {
+        try {
+            return ResponseEntity.accepted().body(trigger.start());
+        } catch (IllegalArgumentException ex) {
+            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
+        } catch (IllegalStateException ex) {
+            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
+        }
+    }
+
+    @FunctionalInterface
+    private interface ManualRunTrigger {
+        AdminScraperService.ManualRunResponse start();
+    }
+}
PATCH

patch -p1 < "$tmp_patch"

echo "Patch aplicado: Admin scraper control API."
echo "Endpoints principais:"
echo "  GET  /api/admin/scrapers/status"
echo "  GET  /api/admin/scrapers/highscores/backoff"
echo "  POST /api/admin/scrapers/highscores/backoff/reset"
echo "  POST /api/admin/scrapers/worlds/run"
echo "  POST /api/admin/scrapers/character-details/run"
echo "  POST /api/admin/scrapers/guilds/run"
echo "  POST /api/admin/scrapers/highscores/plans/{planName}/run"
echo "Sugestão: rode ./run-tests.sh ou mvn test após aplicar."
