#!/usr/bin/env bash
set -Eeuo pipefail

if [[ ! -f "pom.xml" || ! -d "src/main/java/com/nathan/tibiastats" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

if ! command -v git >/dev/null 2>&1; then
  echo "ERROR: git is required to apply this patch cleanly." >&2
  exit 1
fi

STAMP="$(date +%Y%m%d%H%M%S)"
BACKUP_DIR="patches/.backups/highscore-http-backoff-coordinator-$STAMP"
mkdir -p "$BACKUP_DIR"

for f in 'src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java' 'src/main/java/com/nathan/tibiastats/application/service/HighscoreHttpBackoffCoordinator.java'; do
  if [[ -f "$f" ]]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$f")"
    cp "$f" "$BACKUP_DIR/$f"
  fi
done

PATCH_FILE="$(mktemp)"
trap 'rm -f "$PATCH_FILE"' EXIT
cat > "$PATCH_FILE" <<'PATCH_EOF'
diff --git a/src/main/java/com/nathan/tibiastats/application/service/HighscoreHttpBackoffCoordinator.java b/src/main/java/com/nathan/tibiastats/application/service/HighscoreHttpBackoffCoordinator.java
new file mode 100644
index 0000000..3564b77
--- /dev/null
+++ b/src/main/java/com/nathan/tibiastats/application/service/HighscoreHttpBackoffCoordinator.java
@@ -0,0 +1,147 @@
+package com.nathan.tibiastats.application.service;
+
+import com.nathan.tibiastats.config.HighscoreScrapeProperties;
+import com.nathan.tibiastats.infrastructure.persistence.HighscoreScrapeStateRepository;
+import org.slf4j.Logger;
+import org.slf4j.LoggerFactory;
+import org.springframework.stereotype.Service;
+
+import java.time.Instant;
+import java.util.concurrent.atomic.AtomicLong;
+
+@Service
+public class HighscoreHttpBackoffCoordinator {
+    private static final Logger log = LoggerFactory.getLogger(HighscoreHttpBackoffCoordinator.class);
+
+    private final HighscoreScrapeStateRepository stateRepository;
+    private final AtomicLong globalHttpCooldownUntilMs = new AtomicLong(0);
+    private final AtomicLong lastCooldownLogAtMs = new AtomicLong(0);
+    private final Object httpBackoffLock = new Object();
+
+    public HighscoreHttpBackoffCoordinator(HighscoreScrapeStateRepository stateRepository) {
+        this.stateRepository = stateRepository;
+    }
+
+    public HighscoreScrapeStateRepository.HighscoreHttpBackoffState getState() {
+        return stateRepository.getHttpBackoffState();
+    }
+
+    public HighscoreScrapeStateRepository.HighscoreHttpBackoffState resetManually() {
+        stateRepository.resetHttpBackoffAfterSuccess();
+        globalHttpCooldownUntilMs.set(0);
+        lastCooldownLogAtMs.set(0);
+        return stateRepository.getHttpBackoffState();
+    }
+
+    public boolean isActive(String planName) {
+        HighscoreScrapeStateRepository.HighscoreHttpBackoffState backoff = stateRepository.getHttpBackoffState();
+        Instant now = Instant.now();
+        if (backoff == null || !backoff.isActive(now)) {
+            return false;
+        }
+
+        long untilMs = backoff.cooldownUntil() == null ? 0L : backoff.cooldownUntil().toEpochMilli();
+        globalHttpCooldownUntilMs.getAndUpdate(value -> Math.max(value, untilMs));
+        log.warn(
+                "[HIGHSCORE_SCRAPER] Skipping highscore plan because global HTTP backoff is active: plan={}, remainingMs={}, cooldownUntil={}, consecutiveFailures={}, currentCooldownMs={}, lastReason={}",
+                planName,
+                backoff.remainingMs(now),
+                backoff.cooldownUntil(),
+                backoff.consecutiveFailures(),
+                backoff.currentCooldownMs(),
+                backoff.lastReason()
+        );
+        return true;
+    }
+
+    public void resetAfterSuccessfulRun(String planName, int successScopes, int emptyScopes) {
+        HighscoreScrapeStateRepository.HighscoreHttpBackoffState backoff = stateRepository.getHttpBackoffState();
+        if (backoff == null || (backoff.consecutiveFailures() <= 0 && backoff.cooldownUntil() == null)) {
+            return;
+        }
+
+        stateRepository.resetHttpBackoffAfterSuccess();
+        globalHttpCooldownUntilMs.set(0);
+        log.info(
+                "[HIGHSCORE_SCRAPER] Global highscore HTTP backoff reset after successful run: plan={}, successScopes={}, emptyScopes={}",
+                planName,
+                successScopes,
+                emptyScopes
+        );
+    }
+
+    public void awaitCooldown(HighscoreScrapeProperties.Plan plan) {
+        while (true) {
+            long now = System.currentTimeMillis();
+            long until = globalHttpCooldownUntilMs.get();
+            long waitMs = until - now;
+            if (waitMs <= 0) {
+                return;
+            }
+            logCooldownHeartbeat(plan, waitMs);
+            sleepMs(Math.min(waitMs, 1000));
+        }
+    }
+
+    public void activate(HighscoreScrapeProperties.Plan plan, String reason) {
+        long initialCooldownMs = plan.getForbiddenInitialCooldownMs();
+        if (initialCooldownMs <= 0) {
+            return;
+        }
+
+        synchronized (httpBackoffLock) {
+            HighscoreScrapeStateRepository.HighscoreHttpBackoffState current = stateRepository.getHttpBackoffState();
+            Instant now = Instant.now();
+            if (current != null && current.isActive(now)) {
+                globalHttpCooldownUntilMs.getAndUpdate(value -> Math.max(value, current.cooldownUntil().toEpochMilli()));
+                log.warn(
+                        "[HIGHSCORE_SCRAPER] HTTP backoff already active after Tibia response. remainingMs={}, consecutiveFailures={}, currentCooldownMs={}, reason={}",
+                        current.remainingMs(now),
+                        current.consecutiveFailures(),
+                        current.currentCooldownMs(),
+                        reason
+                );
+                return;
+            }
+
+            HighscoreScrapeStateRepository.HighscoreHttpBackoffState backoff = stateRepository.activateHttpBackoff(
+                    initialCooldownMs,
+                    plan.getForbiddenMaxCooldownMs(),
+                    plan.getForbiddenCooldownMultiplier(),
+                    reason
+            );
+            long untilMs = backoff.cooldownUntil() == null ? 0L : backoff.cooldownUntil().toEpochMilli();
+            globalHttpCooldownUntilMs.getAndUpdate(value -> Math.max(value, untilMs));
+            log.warn(
+                    "[HIGHSCORE_SCRAPER] Global highscore backoff activated. All highscore plans disabled until cooldown expires: cooldownMs={}, cooldownUntil={}, consecutiveFailures={}, maxCooldownMs={}, multiplier={}, reason={}",
+                    backoff.currentCooldownMs(),
+                    backoff.cooldownUntil(),
+                    backoff.consecutiveFailures(),
+                    plan.getForbiddenMaxCooldownMs(),
+                    plan.getForbiddenCooldownMultiplier(),
+                    reason
+            );
+        }
+    }
+
+    private void logCooldownHeartbeat(HighscoreScrapeProperties.Plan plan, long remainingMs) {
+        long now = System.currentTimeMillis();
+        long intervalMs = plan.getCooldownLogIntervalMs();
+        long lastLog = lastCooldownLogAtMs.get();
+        if (intervalMs > 0 && now - lastLog >= intervalMs && lastCooldownLogAtMs.compareAndSet(lastLog, now)) {
+            log.info("[HIGHSCORE_SCRAPER] HTTP cooldown still active: remainingMs={}", Math.max(0, remainingMs));
+        }
+    }
+
+    private void sleepMs(long delayMs) {
+        if (delayMs <= 0) {
+            return;
+        }
+        try {
+            Thread.sleep(delayMs);
+        } catch (InterruptedException ex) {
+            Thread.currentThread().interrupt();
+            throw new IllegalStateException("Interrupted while waiting during highscore HTTP backoff", ex);
+        }
+    }
+}
diff --git a/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java b/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java
index f18fbb0..e6d4414 100644
--- a/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java
+++ b/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java
@@ -46,12 +46,10 @@ public class HighscoreService {
     private final HighscoreScrapeProperties properties;
     private final HighscoreScrapeStateRepository stateRepository;
     private final HighscoreStatRecordWriter statRecordWriter;
+    private final HighscoreHttpBackoffCoordinator httpBackoffCoordinator;
     private final AtomicBoolean running = new AtomicBoolean(false);
-    private final AtomicLong globalHttpCooldownUntilMs = new AtomicLong(0);
     private final AtomicLong nextAllowedHttpRequestAtMs = new AtomicLong(0);
-    private final AtomicLong lastCooldownLogAtMs = new AtomicLong(0);
     private final AtomicLong lastRetrySleepLogAtMs = new AtomicLong(0);
-    private final Object httpBackoffLock = new Object();
     private final Map<String, Object> nameLocks = new ConcurrentHashMap<>();
 
     public HighscoreService(
@@ -60,7 +58,8 @@ public class HighscoreService {
             CharacterNamingService namingService,
             HighscoreScrapeProperties properties,
             HighscoreScrapeStateRepository stateRepository,
-            HighscoreStatRecordWriter statRecordWriter
+            HighscoreStatRecordWriter statRecordWriter,
+            HighscoreHttpBackoffCoordinator httpBackoffCoordinator
     ) {
         this.highscorePort = highscorePort;
         this.worldRepository = worldRepository;
@@ -68,6 +67,7 @@ public class HighscoreService {
         this.properties = properties;
         this.stateRepository = stateRepository;
         this.statRecordWriter = statRecordWriter;
+        this.httpBackoffCoordinator = httpBackoffCoordinator;
     }
 
     public ScrapeJobResult updateAllHighscores() {
@@ -84,7 +84,7 @@ public class HighscoreService {
             return ScrapeJobResult.empty();
         }
 
-        if (isHttpBackoffActive(planName)) {
+        if (httpBackoffCoordinator.isActive(planName)) {
             return ScrapeJobResult.empty();
         }
 
@@ -102,14 +102,11 @@ public class HighscoreService {
     }
 
     public HighscoreScrapeStateRepository.HighscoreHttpBackoffState getHttpBackoffState() {
-        return stateRepository.getHttpBackoffState();
+        return httpBackoffCoordinator.getState();
     }
 
     public HighscoreScrapeStateRepository.HighscoreHttpBackoffState resetHttpBackoffManually() {
-        stateRepository.resetHttpBackoffAfterSuccess();
-        globalHttpCooldownUntilMs.set(0);
-        lastCooldownLogAtMs.set(0);
-        return stateRepository.getHttpBackoffState();
+        return httpBackoffCoordinator.resetManually();
     }
 
     public boolean isRunning() {
@@ -221,7 +218,7 @@ public class HighscoreService {
             }
 
             if (!rateLimited.get() && (success > 0 || empty > 0)) {
-                resetHttpBackoffAfterSuccessfulRun(planName, success, empty);
+                httpBackoffCoordinator.resetAfterSuccessfulRun(planName, success, empty);
             }
 
             log.info(
@@ -423,7 +420,7 @@ public class HighscoreService {
             }
             requestSemaphore.acquire();
             try {
-                awaitGlobalHttpCooldown(plan);
+                httpBackoffCoordinator.awaitCooldown(plan);
                 awaitGlobalRequestPace(plan);
                 throttleRequestWithJitter(plan);
                 List<HighscorePort.HighscoreRow> rows = highscorePort.fetchHighscores(
@@ -439,7 +436,7 @@ public class HighscoreService {
                 boolean shouldRetry = transientFailure && attempt < maxAttempts;
 
                 if (isForbiddenOrRateLimited(ex)) {
-                    activateGlobalHttpCooldown(plan, rootMessage(ex));
+                    httpBackoffCoordinator.activate(plan, rootMessage(ex));
                     if (plan.isAbortRunOnForbidden()) {
                         rateLimited.set(true);
                         throw new RateLimitedHighscoreException(rootMessage(ex), ex);
@@ -498,43 +495,6 @@ public class HighscoreService {
     }
 
 
-    private boolean isHttpBackoffActive(String planName) {
-        HighscoreScrapeStateRepository.HighscoreHttpBackoffState backoff = stateRepository.getHttpBackoffState();
-        Instant now = Instant.now();
-        if (backoff == null || !backoff.isActive(now)) {
-            return false;
-        }
-
-        long untilMs = backoff.cooldownUntil() == null ? 0L : backoff.cooldownUntil().toEpochMilli();
-        globalHttpCooldownUntilMs.getAndUpdate(value -> Math.max(value, untilMs));
-        log.warn(
-                "[HIGHSCORE_SCRAPER] Skipping highscore plan because global HTTP backoff is active: plan={}, remainingMs={}, cooldownUntil={}, consecutiveFailures={}, currentCooldownMs={}, lastReason={}",
-                planName,
-                backoff.remainingMs(now),
-                backoff.cooldownUntil(),
-                backoff.consecutiveFailures(),
-                backoff.currentCooldownMs(),
-                backoff.lastReason()
-        );
-        return true;
-    }
-
-    private void resetHttpBackoffAfterSuccessfulRun(String planName, int successScopes, int emptyScopes) {
-        HighscoreScrapeStateRepository.HighscoreHttpBackoffState backoff = stateRepository.getHttpBackoffState();
-        if (backoff == null || (backoff.consecutiveFailures() <= 0 && backoff.cooldownUntil() == null)) {
-            return;
-        }
-
-        stateRepository.resetHttpBackoffAfterSuccess();
-        globalHttpCooldownUntilMs.set(0);
-        log.info(
-                "[HIGHSCORE_SCRAPER] Global highscore HTTP backoff reset after successful run: plan={}, successScopes={}, emptyScopes={}",
-                planName,
-                successScopes,
-                emptyScopes
-        );
-    }
-
     private void throttleRequestWithJitter(HighscoreScrapeProperties.Plan plan) {
         int baseDelay = plan.getPageDelayMs();
         int jitter = plan.getRequestJitterMs();
@@ -574,69 +534,6 @@ public class HighscoreService {
         }
     }
 
-    private void awaitGlobalHttpCooldown(HighscoreScrapeProperties.Plan plan) {
-        while (true) {
-            long now = System.currentTimeMillis();
-            long until = globalHttpCooldownUntilMs.get();
-            long waitMs = until - now;
-            if (waitMs <= 0) {
-                return;
-            }
-            logCooldownHeartbeat(plan, waitMs);
-            sleepMs(Math.min(waitMs, 1000));
-        }
-    }
-
-    private void activateGlobalHttpCooldown(HighscoreScrapeProperties.Plan plan, String reason) {
-        long initialCooldownMs = plan.getForbiddenInitialCooldownMs();
-        if (initialCooldownMs <= 0) {
-            return;
-        }
-
-        synchronized (httpBackoffLock) {
-            HighscoreScrapeStateRepository.HighscoreHttpBackoffState current = stateRepository.getHttpBackoffState();
-            Instant now = Instant.now();
-            if (current != null && current.isActive(now)) {
-                globalHttpCooldownUntilMs.getAndUpdate(value -> Math.max(value, current.cooldownUntil().toEpochMilli()));
-                log.warn(
-                        "[HIGHSCORE_SCRAPER] HTTP backoff already active after Tibia response. remainingMs={}, consecutiveFailures={}, currentCooldownMs={}, reason={}",
-                        current.remainingMs(now),
-                        current.consecutiveFailures(),
-                        current.currentCooldownMs(),
-                        reason
-                );
-                return;
-            }
-
-            HighscoreScrapeStateRepository.HighscoreHttpBackoffState backoff = stateRepository.activateHttpBackoff(
-                    initialCooldownMs,
-                    plan.getForbiddenMaxCooldownMs(),
-                    plan.getForbiddenCooldownMultiplier(),
-                    reason
-            );
-            long untilMs = backoff.cooldownUntil() == null ? 0L : backoff.cooldownUntil().toEpochMilli();
-            globalHttpCooldownUntilMs.getAndUpdate(value -> Math.max(value, untilMs));
-            log.warn(
-                    "[HIGHSCORE_SCRAPER] Global highscore backoff activated. All highscore plans disabled until cooldown expires: cooldownMs={}, cooldownUntil={}, consecutiveFailures={}, maxCooldownMs={}, multiplier={}, reason={}",
-                    backoff.currentCooldownMs(),
-                    backoff.cooldownUntil(),
-                    backoff.consecutiveFailures(),
-                    plan.getForbiddenMaxCooldownMs(),
-                    plan.getForbiddenCooldownMultiplier(),
-                    reason
-            );
-        }
-    }
-
-    private void logCooldownHeartbeat(HighscoreScrapeProperties.Plan plan, long remainingMs) {
-        long now = System.currentTimeMillis();
-        long intervalMs = plan.getCooldownLogIntervalMs();
-        long lastLog = lastCooldownLogAtMs.get();
-        if (intervalMs > 0 && now - lastLog >= intervalMs && lastCooldownLogAtMs.compareAndSet(lastLog, now)) {
-            log.info("[HIGHSCORE_SCRAPER] HTTP cooldown still active: remainingMs={}", Math.max(0, remainingMs));
-        }
-    }
-
     private void sleepWithRetryHeartbeat(HighscoreScrapeProperties.Plan plan, long delayMs, HighscoreScope scope, int page, int attempt, int maxAttempts) {
         if (delayMs <= 0) {
             return;

PATCH_EOF

if git apply --check "$PATCH_FILE"; then
  git apply --whitespace=nowarn "$PATCH_FILE"
else
  echo "ERROR: patch does not apply cleanly. Backup was created at $BACKUP_DIR" >&2
  echo "Tip: verify that previous architecture patches were applied in order." >&2
  exit 1
fi

echo "Done. Highscore HTTP backoff coordinator extraction applied."
echo "Backup directory: $BACKUP_DIR"
echo "Next step: make test"
