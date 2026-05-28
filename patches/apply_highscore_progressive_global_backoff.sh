#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

if [[ ! -f "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java" ]]; then
  echo "ERROR: execute este script na raiz do projeto TibiaChrono." >&2
  exit 1
fi

if grep -q "HighscoreHttpBackoffState" src/main/java/com/nathan/tibiastats/infrastructure/persistence/HighscoreScrapeStateRepository.java \
   && grep -q "isHttpBackoffActive" src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java \
   && grep -q "forbiddenInitialCooldownMs" src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java \
   && ls src/main/resources/db/migration/V*__highscore_global_http_backoff.sql >/dev/null 2>&1; then
  echo "Highscore progressive global backoff já parece aplicado. Nada a fazer."
  exit 0
fi

TMP_PATCH="$(mktemp)"
trap 'rm -f "$TMP_PATCH"' EXIT
cat > "$TMP_PATCH" <<'PATCH'
diff -ruN tibia_work/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java tibia_backoff_work/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java
--- tibia_work/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java	2026-05-26 22:56:12.000000000 +0000
+++ tibia_backoff_work/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java	2026-05-28 12:27:05.635119725 +0000
@@ -51,6 +51,7 @@
     private final AtomicLong nextAllowedHttpRequestAtMs = new AtomicLong(0);
     private final AtomicLong lastCooldownLogAtMs = new AtomicLong(0);
     private final AtomicLong lastRetrySleepLogAtMs = new AtomicLong(0);
+    private final Object httpBackoffLock = new Object();
     private final Map<String, Object> nameLocks = new ConcurrentHashMap<>();
 
     public HighscoreService(
@@ -83,6 +84,10 @@
             return;
         }
 
+        if (isHttpBackoffActive(planName)) {
+            return;
+        }
+
         if (!running.compareAndSet(false, true)) {
             log.warn("[HIGHSCORE_SCRAPER] Previous highscore run is still active. Skipping this tick: plan={}", planName);
             return;
@@ -123,7 +128,7 @@
         }
 
         log.info(
-                "[HIGHSCORE_SCRAPER] Starting run: plan={}, selectedScopes={}, scopesPerRun={}, allScopesPerRun={}, scopeWorkers={}, requestParallelism={}, pageWindowSize={}, maxPages={}, pageDelayMs={}, requestMaxAttempts={}, retryBaseDelayMs={}, retryMaxDelayMs={}, forbiddenCooldownMs={}, requestJitterMs={}, requestMinIntervalMs={}, cooldownLogIntervalMs={}, progressLogIntervalScopes={}, worlds={}, categories={}, vocations={}, abortRunOnForbidden={}",
+                "[HIGHSCORE_SCRAPER] Starting run: plan={}, selectedScopes={}, scopesPerRun={}, allScopesPerRun={}, scopeWorkers={}, requestParallelism={}, pageWindowSize={}, maxPages={}, pageDelayMs={}, requestMaxAttempts={}, retryBaseDelayMs={}, retryMaxDelayMs={}, forbiddenCooldownMs={}, forbiddenInitialCooldownMs={}, forbiddenMaxCooldownMs={}, forbiddenCooldownMultiplier={}, requestJitterMs={}, requestMinIntervalMs={}, cooldownLogIntervalMs={}, progressLogIntervalScopes={}, worlds={}, categories={}, vocations={}, abortRunOnForbidden={}",
                 planName,
                 scopes.size(),
                 plan.getScopesPerRun(),
@@ -137,6 +142,9 @@
                 plan.getRetryBaseDelayMs(),
                 plan.getRetryMaxDelayMs(),
                 plan.getForbiddenCooldownMs(),
+                plan.getForbiddenInitialCooldownMs(),
+                plan.getForbiddenMaxCooldownMs(),
+                plan.getForbiddenCooldownMultiplier(),
                 plan.getRequestJitterMs(),
                 plan.getRequestMinIntervalMs(),
                 plan.getCooldownLogIntervalMs(),
@@ -197,6 +205,10 @@
                 }
             }
 
+            if (!rateLimited.get() && (success > 0 || empty > 0)) {
+                resetHttpBackoffAfterSuccessfulRun(planName, success, empty);
+            }
+
             log.info(
                     "[HIGHSCORE_SCRAPER] Finished run: plan={}, successScopes={}, emptyScopes={}, failedScopes={}, completedScopes={}, selectedScopes={}, pages={}, rows={}, durationMs={}, cacheSize={}, rateLimited={}",
                     planName,
@@ -469,6 +481,44 @@
         return Normalizer.normalize(cleaned, Normalizer.Form.NFKC);
     }
 
+
+    private boolean isHttpBackoffActive(String planName) {
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
+    private void resetHttpBackoffAfterSuccessfulRun(String planName, int successScopes, int emptyScopes) {
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
     private void throttleRequestWithJitter(HighscoreScrapeProperties.Plan plan) {
         int baseDelay = plan.getPageDelayMs();
         int jitter = plan.getRequestJitterMs();
@@ -522,17 +572,41 @@
     }
 
     private void activateGlobalHttpCooldown(HighscoreScrapeProperties.Plan plan, String reason) {
-        int cooldownMs = plan.getForbiddenCooldownMs();
-        if (cooldownMs <= 0) {
+        long initialCooldownMs = plan.getForbiddenInitialCooldownMs();
+        if (initialCooldownMs <= 0) {
             return;
         }
 
-        long until = System.currentTimeMillis() + cooldownMs;
-        long previous = globalHttpCooldownUntilMs.getAndUpdate(current -> Math.max(current, until));
-        if (until > previous) {
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
             log.warn(
-                    "[HIGHSCORE_SCRAPER] HTTP cooldown activated for {}ms after Tibia response: {}",
-                    cooldownMs,
+                    "[HIGHSCORE_SCRAPER] Global highscore backoff activated. All highscore plans disabled until cooldown expires: cooldownMs={}, cooldownUntil={}, consecutiveFailures={}, maxCooldownMs={}, multiplier={}, reason={}",
+                    backoff.currentCooldownMs(),
+                    backoff.cooldownUntil(),
+                    backoff.consecutiveFailures(),
+                    plan.getForbiddenMaxCooldownMs(),
+                    plan.getForbiddenCooldownMultiplier(),
                     reason
             );
         }
@@ -583,7 +657,7 @@
         long delay = Math.min(max, exponential);
 
         if (isForbiddenOrRateLimited(ex)) {
-            delay = Math.max(delay, Math.min(max, plan.getForbiddenCooldownMs()));
+            delay = Math.max(delay, Math.min(max, plan.getForbiddenInitialCooldownMs()));
         }
 
         int jitter = plan.getRequestJitterMs();
diff -ruN tibia_work/src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java tibia_backoff_work/src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java
--- tibia_work/src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java	2026-05-26 22:56:12.000000000 +0000
+++ tibia_backoff_work/src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java	2026-05-28 12:26:20.040880701 +0000
@@ -35,7 +35,13 @@
     private int requestMaxAttempts = 1;
     private int retryBaseDelayMs = 5000;
     private int retryMaxDelayMs = 300000;
+    /**
+     * Deprecated compatibility field. Use forbiddenInitialCooldownMs/forbiddenMaxCooldownMs instead.
+     */
     private int forbiddenCooldownMs = 14400000;
+    private long forbiddenInitialCooldownMs = 86400000L; // 24h
+    private long forbiddenMaxCooldownMs = 604800000L;    // 7d
+    private double forbiddenCooldownMultiplier = 2.0D;
     private int requestJitterMs = 300;
     private int requestMinIntervalMs = 750;
     private int cooldownLogIntervalMs = 30000;
@@ -193,6 +199,33 @@
 
     public void setForbiddenCooldownMs(int forbiddenCooldownMs) {
         this.forbiddenCooldownMs = forbiddenCooldownMs;
+        if (forbiddenInitialCooldownMs <= 0) {
+            this.forbiddenInitialCooldownMs = forbiddenCooldownMs;
+        }
+    }
+
+    public long getForbiddenInitialCooldownMs() {
+        return Math.max(0L, forbiddenInitialCooldownMs);
+    }
+
+    public void setForbiddenInitialCooldownMs(long forbiddenInitialCooldownMs) {
+        this.forbiddenInitialCooldownMs = forbiddenInitialCooldownMs;
+    }
+
+    public long getForbiddenMaxCooldownMs() {
+        return Math.max(getForbiddenInitialCooldownMs(), forbiddenMaxCooldownMs);
+    }
+
+    public void setForbiddenMaxCooldownMs(long forbiddenMaxCooldownMs) {
+        this.forbiddenMaxCooldownMs = forbiddenMaxCooldownMs;
+    }
+
+    public double getForbiddenCooldownMultiplier() {
+        return forbiddenCooldownMultiplier < 1.0D ? 1.0D : forbiddenCooldownMultiplier;
+    }
+
+    public void setForbiddenCooldownMultiplier(double forbiddenCooldownMultiplier) {
+        this.forbiddenCooldownMultiplier = forbiddenCooldownMultiplier;
     }
 
     public int getRequestJitterMs() {
@@ -266,6 +299,9 @@
         plan.setRetryBaseDelayMs(retryBaseDelayMs);
         plan.setRetryMaxDelayMs(retryMaxDelayMs);
         plan.setForbiddenCooldownMs(forbiddenCooldownMs);
+        plan.setForbiddenInitialCooldownMs(forbiddenInitialCooldownMs);
+        plan.setForbiddenMaxCooldownMs(forbiddenMaxCooldownMs);
+        plan.setForbiddenCooldownMultiplier(forbiddenCooldownMultiplier);
         plan.setRequestJitterMs(requestJitterMs);
         plan.setRequestMinIntervalMs(requestMinIntervalMs);
         plan.setCooldownLogIntervalMs(cooldownLogIntervalMs);
@@ -351,7 +387,13 @@
         private int requestMaxAttempts = 1;
         private int retryBaseDelayMs = 5000;
         private int retryMaxDelayMs = 300000;
+        /**
+         * Deprecated compatibility field. Use forbiddenInitialCooldownMs/forbiddenMaxCooldownMs instead.
+         */
         private int forbiddenCooldownMs = 14400000;
+        private long forbiddenInitialCooldownMs = 86400000L; // 24h
+        private long forbiddenMaxCooldownMs = 604800000L;    // 7d
+        private double forbiddenCooldownMultiplier = 2.0D;
         private int requestJitterMs = 300;
         private int requestMinIntervalMs = 750;
         private int cooldownLogIntervalMs = 30000;
@@ -394,7 +436,18 @@
         public int getRetryMaxDelayMs() { return Math.max(getRetryBaseDelayMs(), retryMaxDelayMs); }
         public void setRetryMaxDelayMs(int retryMaxDelayMs) { this.retryMaxDelayMs = retryMaxDelayMs; }
         public int getForbiddenCooldownMs() { return Math.max(0, forbiddenCooldownMs); }
-        public void setForbiddenCooldownMs(int forbiddenCooldownMs) { this.forbiddenCooldownMs = forbiddenCooldownMs; }
+        public void setForbiddenCooldownMs(int forbiddenCooldownMs) {
+            this.forbiddenCooldownMs = forbiddenCooldownMs;
+            if (forbiddenInitialCooldownMs <= 0) {
+                this.forbiddenInitialCooldownMs = forbiddenCooldownMs;
+            }
+        }
+        public long getForbiddenInitialCooldownMs() { return Math.max(0L, forbiddenInitialCooldownMs); }
+        public void setForbiddenInitialCooldownMs(long forbiddenInitialCooldownMs) { this.forbiddenInitialCooldownMs = forbiddenInitialCooldownMs; }
+        public long getForbiddenMaxCooldownMs() { return Math.max(getForbiddenInitialCooldownMs(), forbiddenMaxCooldownMs); }
+        public void setForbiddenMaxCooldownMs(long forbiddenMaxCooldownMs) { this.forbiddenMaxCooldownMs = forbiddenMaxCooldownMs; }
+        public double getForbiddenCooldownMultiplier() { return forbiddenCooldownMultiplier < 1.0D ? 1.0D : forbiddenCooldownMultiplier; }
+        public void setForbiddenCooldownMultiplier(double forbiddenCooldownMultiplier) { this.forbiddenCooldownMultiplier = forbiddenCooldownMultiplier; }
         public int getRequestJitterMs() { return Math.max(0, requestJitterMs); }
         public void setRequestJitterMs(int requestJitterMs) { this.requestJitterMs = requestJitterMs; }
         public int getRequestMinIntervalMs() { return Math.max(0, requestMinIntervalMs); }
@@ -424,6 +477,9 @@
                     + ", requestParallelism=" + getRequestParallelism()
                     + ", requestMinIntervalMs=" + getRequestMinIntervalMs()
                     + ", requestMaxAttempts=" + getRequestMaxAttempts()
+                    + ", forbiddenInitialCooldownMs=" + getForbiddenInitialCooldownMs()
+                    + ", forbiddenMaxCooldownMs=" + getForbiddenMaxCooldownMs()
+                    + ", forbiddenCooldownMultiplier=" + getForbiddenCooldownMultiplier()
                     + ", abortRunOnForbidden=" + abortRunOnForbidden;
         }
     }
diff -ruN tibia_work/src/main/java/com/nathan/tibiastats/infrastructure/persistence/HighscoreScrapeStateRepository.java tibia_backoff_work/src/main/java/com/nathan/tibiastats/infrastructure/persistence/HighscoreScrapeStateRepository.java
--- tibia_work/src/main/java/com/nathan/tibiastats/infrastructure/persistence/HighscoreScrapeStateRepository.java	2026-05-26 18:19:41.000000000 +0000
+++ tibia_backoff_work/src/main/java/com/nathan/tibiastats/infrastructure/persistence/HighscoreScrapeStateRepository.java	2026-05-28 12:26:39.006992787 +0000
@@ -117,6 +117,101 @@
                 scope.worldId(), scope.category().name(), scope.vocationFilterId());
     }
 
+
+    public HighscoreHttpBackoffState getHttpBackoffState() {
+        ensureHttpBackoffRow();
+        return jdbc.queryForObject("""
+            select cooldown_until,
+                   consecutive_failures,
+                   current_cooldown_ms,
+                   last_status,
+                   last_reason,
+                   last_failure_at,
+                   last_success_at
+              from highscore_http_backoff_state
+             where id = 1
+            """, this::mapHttpBackoffState);
+    }
+
+    public HighscoreHttpBackoffState activateHttpBackoff(long initialCooldownMs, long maxCooldownMs, double multiplier, String reason) {
+        ensureHttpBackoffRow();
+        HighscoreHttpBackoffState current = getHttpBackoffState();
+        Instant now = Instant.now();
+        if (current != null && current.isActive(now)) {
+            return current;
+        }
+
+        long normalizedInitial = Math.max(0L, initialCooldownMs);
+        long normalizedMax = Math.max(normalizedInitial, maxCooldownMs);
+        double normalizedMultiplier = multiplier < 1.0D ? 1.0D : multiplier;
+        long previousCooldown = current == null ? 0L : Math.max(0L, current.currentCooldownMs());
+        long nextCooldown = previousCooldown <= 0L
+                ? normalizedInitial
+                : Math.min(normalizedMax, Math.max(normalizedInitial, Math.round(previousCooldown * normalizedMultiplier)));
+        Instant cooldownUntil = now.plusMillis(nextCooldown);
+        int consecutiveFailures = current == null ? 1 : current.consecutiveFailures() + 1;
+
+        jdbc.update("""
+            update highscore_http_backoff_state
+               set cooldown_until = ?,
+                   consecutive_failures = ?,
+                   current_cooldown_ms = ?,
+                   last_status = 'FORBIDDEN',
+                   last_reason = ?,
+                   last_failure_at = ?,
+                   updated_at = now()
+             where id = 1
+            """,
+                java.sql.Timestamp.from(cooldownUntil),
+                consecutiveFailures,
+                nextCooldown,
+                truncate(reason, 4000),
+                java.sql.Timestamp.from(now)
+        );
+
+        return getHttpBackoffState();
+    }
+
+    public void resetHttpBackoffAfterSuccess() {
+        ensureHttpBackoffRow();
+        jdbc.update("""
+            update highscore_http_backoff_state
+               set cooldown_until = null,
+                   consecutive_failures = 0,
+                   current_cooldown_ms = 0,
+                   last_status = 'OK',
+                   last_reason = null,
+                   last_success_at = now(),
+                   updated_at = now()
+             where id = 1
+            """);
+    }
+
+    private void ensureHttpBackoffRow() {
+        jdbc.update("""
+            insert into highscore_http_backoff_state (id, updated_at)
+            values (1, now())
+            on conflict (id) do nothing
+            """);
+    }
+
+    private HighscoreHttpBackoffState mapHttpBackoffState(ResultSet rs, int rowNum) throws SQLException {
+        return new HighscoreHttpBackoffState(
+                toInstant(rs, "cooldown_until"),
+                rs.getInt("consecutive_failures"),
+                rs.getLong("current_cooldown_ms"),
+                rs.getString("last_status"),
+                rs.getString("last_reason"),
+                toInstant(rs, "last_failure_at"),
+                toInstant(rs, "last_success_at")
+        );
+    }
+
+    private Instant toInstant(ResultSet rs, String column) throws SQLException {
+        java.sql.Timestamp timestamp = rs.getTimestamp(column);
+        return timestamp == null ? null : timestamp.toInstant();
+    }
+
     private HighscoreScope mapScope(ResultSet rs, int rowNum) throws SQLException {
         return new HighscoreScope(
                 rs.getInt("world_id"),
@@ -132,4 +227,25 @@
         }
         return value.length() <= maxLength ? value : value.substring(0, maxLength);
     }
+
+    public record HighscoreHttpBackoffState(
+            Instant cooldownUntil,
+            int consecutiveFailures,
+            long currentCooldownMs,
+            String lastStatus,
+            String lastReason,
+            Instant lastFailureAt,
+            Instant lastSuccessAt
+    ) {
+        public boolean isActive(Instant now) {
+            return cooldownUntil != null && cooldownUntil.isAfter(now);
+        }
+
+        public long remainingMs(Instant now) {
+            if (!isActive(now)) {
+                return 0L;
+            }
+            return Math.max(0L, java.time.Duration.between(now, cooldownUntil).toMillis());
+        }
+    }
 }
diff -ruN tibia_work/src/main/resources/db/migration/V47__highscore_global_http_backoff.sql tibia_backoff_work/src/main/resources/db/migration/V47__highscore_global_http_backoff.sql
--- tibia_work/src/main/resources/db/migration/V47__highscore_global_http_backoff.sql	1970-01-01 00:00:00.000000000 +0000
+++ tibia_backoff_work/src/main/resources/db/migration/V47__highscore_global_http_backoff.sql	2026-05-28 12:27:36.581291071 +0000
@@ -0,0 +1,21 @@
+CREATE TABLE IF NOT EXISTS highscore_http_backoff_state (
+    id SMALLINT PRIMARY KEY DEFAULT 1,
+    cooldown_until TIMESTAMP WITH TIME ZONE NULL,
+    consecutive_failures INTEGER NOT NULL DEFAULT 0,
+    current_cooldown_ms BIGINT NOT NULL DEFAULT 0,
+    last_status VARCHAR(50) NULL,
+    last_reason TEXT NULL,
+    last_failure_at TIMESTAMP WITH TIME ZONE NULL,
+    last_success_at TIMESTAMP WITH TIME ZONE NULL,
+    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
+    CONSTRAINT chk_highscore_http_backoff_singleton CHECK (id = 1),
+    CONSTRAINT chk_highscore_http_backoff_failures_non_negative CHECK (consecutive_failures >= 0),
+    CONSTRAINT chk_highscore_http_backoff_cooldown_non_negative CHECK (current_cooldown_ms >= 0)
+);
+
+INSERT INTO highscore_http_backoff_state (id, updated_at)
+VALUES (1, now())
+ON CONFLICT (id) DO NOTHING;
+
+CREATE INDEX IF NOT EXISTS idx_highscore_http_backoff_cooldown_until
+    ON highscore_http_backoff_state (cooldown_until);
PATCH

if patch -p1 --forward < "$TMP_PATCH"; then
  echo "Patch aplicado com sucesso."
else
  echo "Patch não aplicou limpo. Verificando se a alteração já foi parcialmente aplicada..." >&2
  if grep -q "HighscoreHttpBackoffState" src/main/java/com/nathan/tibiastats/infrastructure/persistence/HighscoreScrapeStateRepository.java \
     && grep -q "isHttpBackoffActive" src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java \
     && grep -q "forbiddenInitialCooldownMs" src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java; then
    if ! ls src/main/resources/db/migration/V*__highscore_global_http_backoff.sql >/dev/null 2>&1; then
      NEXT_VERSION=$(( $(find src/main/resources/db/migration -maxdepth 1 -type f -name 'V*.sql' | sed -E 's#.*\/V([0-9]+)__.*#\1#' | sort -n | tail -1) + 1 ))
      cat > "src/main/resources/db/migration/V${NEXT_VERSION}__highscore_global_http_backoff.sql" <<'SQL'
CREATE TABLE IF NOT EXISTS highscore_http_backoff_state (
    id SMALLINT PRIMARY KEY DEFAULT 1,
    cooldown_until TIMESTAMP WITH TIME ZONE NULL,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    current_cooldown_ms BIGINT NOT NULL DEFAULT 0,
    last_status VARCHAR(50) NULL,
    last_reason TEXT NULL,
    last_failure_at TIMESTAMP WITH TIME ZONE NULL,
    last_success_at TIMESTAMP WITH TIME ZONE NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_highscore_http_backoff_singleton CHECK (id = 1),
    CONSTRAINT chk_highscore_http_backoff_failures_non_negative CHECK (consecutive_failures >= 0),
    CONSTRAINT chk_highscore_http_backoff_cooldown_non_negative CHECK (current_cooldown_ms >= 0)
);

INSERT INTO highscore_http_backoff_state (id, updated_at)
VALUES (1, now())
ON CONFLICT (id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_highscore_http_backoff_cooldown_until
    ON highscore_http_backoff_state (cooldown_until);
SQL
    fi
    echo "Alteração já estava parcialmente aplicada; migração garantida."
  else
    echo "ERROR: patch falhou e não consegui confirmar aplicação parcial." >&2
    exit 1
  fi
fi

# If V47 already existed in the project for another feature, move this migration to the next available version.
if [[ -f src/main/resources/db/migration/V47__highscore_global_http_backoff.sql ]]; then
  if find src/main/resources/db/migration -maxdepth 1 -type f -name 'V47__*.sql' ! -name 'V47__highscore_global_http_backoff.sql' | grep -q .; then
    NEXT_VERSION=$(( $(find src/main/resources/db/migration -maxdepth 1 -type f -name 'V*.sql' | sed -E 's#.*\/V([0-9]+)__.*#\1#' | sort -n | tail -1) + 1 ))
    mv src/main/resources/db/migration/V47__highscore_global_http_backoff.sql "src/main/resources/db/migration/V${NEXT_VERSION}__highscore_global_http_backoff.sql"
    echo "Migração de backoff movida para V${NEXT_VERSION} para evitar conflito."
  fi
fi

echo "Concluído. Rode ./run-tests.sh e depois reinicie a aplicação."
