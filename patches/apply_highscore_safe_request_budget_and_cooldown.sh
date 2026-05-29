#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

PROPS="src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java"
SERVICE="src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java"
APP_YML="src/main/resources/application.yml"
DEV_YML="src/main/resources/application-dev.yml"
PLAN_TEST="src/test/java/com/nathan/tibiastats/config/HighscorePlanConfigurationTest.java"
BUDGET_TEST="src/test/java/com/nathan/tibiastats/config/HighscoreRequestBudgetConfigurationTest.java"

if [[ ! -f "pom.xml" || ! -f "$PROPS" || ! -f "$SERVICE" ]]; then
  echo "ERROR: execute este script na raiz do projeto TibiaChrono." >&2
  exit 1
fi

if grep -q "SAFE_REQUEST_BUDGET_MAX_REQUESTS" "$PROPS" \
   && grep -q "awaitHighscoreRequestBudget" "$SERVICE" \
   && grep -q "request-budget-max-requests" "$DEV_YML" 2>/dev/null \
   && [[ -f "$BUDGET_TEST" ]]; then
  echo "Highscore safe request budget/cooldown já parece aplicado. Nada a fazer."
  exit 0
fi

BACKUP_DIR=".tibiachrono-highscore-safe-budget-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"
backup_file() {
  local file="$1"
  if [[ -f "$file" ]]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$file" "$BACKUP_DIR/$file"
  fi
}

backup_file "$PROPS"
backup_file "$SERVICE"
backup_file "$APP_YML"
backup_file "$DEV_YML"
backup_file "$PLAN_TEST"
backup_file "$BUDGET_TEST"

python3 - <<'PY'
from pathlib import Path
import re

PROPS = Path('src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java')
SERVICE = Path('src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java')
APP_YML = Path('src/main/resources/application.yml')
DEV_YML = Path('src/main/resources/application-dev.yml')
PLAN_TEST = Path('src/test/java/com/nathan/tibiastats/config/HighscorePlanConfigurationTest.java')
BUDGET_TEST = Path('src/test/java/com/nathan/tibiastats/config/HighscoreRequestBudgetConfigurationTest.java')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'Could not safely update {label}. Expected block not found.')
    return text.replace(old, new, 1)

# 1) Configuration properties: hard 150k/10min request budget + conservative 403 cooldown defaults.
props = PROPS.read_text()
if 'SAFE_REQUEST_BUDGET_MAX_REQUESTS' not in props:
    props = replace_once(
        props,
        '    private static final Logger log = LoggerFactory.getLogger(HighscoreScrapeProperties.class);\n',
        '    private static final Logger log = LoggerFactory.getLogger(HighscoreScrapeProperties.class);\n'
        '    public static final long SAFE_REQUEST_BUDGET_WINDOW_MS = 600_000L;\n'
        '    public static final int SAFE_REQUEST_BUDGET_MAX_REQUESTS = 150_000;\n'
        '    private static final long DEFAULT_FORBIDDEN_INITIAL_COOLDOWN_MS = 259_200_000L; // 72h\n'
        '    private static final long DEFAULT_FORBIDDEN_MAX_COOLDOWN_MS = 1_209_600_000L;    // 14d\n',
        'HighscoreScrapeProperties constants'
    )
    props = re.sub(
        r'private long forbiddenInitialCooldownMs = 86400000L;\s*// 24h',
        'private long forbiddenInitialCooldownMs = DEFAULT_FORBIDDEN_INITIAL_COOLDOWN_MS;',
        props,
        count=2,
    )
    props = re.sub(
        r'private long forbiddenMaxCooldownMs = 604800000L;\s*// 7d',
        'private long forbiddenMaxCooldownMs = DEFAULT_FORBIDDEN_MAX_COOLDOWN_MS;',
        props,
        count=2,
    )
    props = replace_once(
        props,
        '    private int requestMinIntervalMs = 750;\n    private int cooldownLogIntervalMs = 30000;\n',
        '    private int requestMinIntervalMs = 750;\n'
        '    private long requestBudgetWindowMs = SAFE_REQUEST_BUDGET_WINDOW_MS;\n'
        '    private int requestBudgetMaxRequests = SAFE_REQUEST_BUDGET_MAX_REQUESTS;\n'
        '    private int cooldownLogIntervalMs = 30000;\n',
        'root request budget fields'
    )
    props = replace_once(
        props,
        '        private int requestMinIntervalMs = 750;\n        private int cooldownLogIntervalMs = 30000;\n',
        '        private int requestMinIntervalMs = 750;\n'
        '        private long requestBudgetWindowMs = SAFE_REQUEST_BUDGET_WINDOW_MS;\n'
        '        private int requestBudgetMaxRequests = SAFE_REQUEST_BUDGET_MAX_REQUESTS;\n'
        '        private int cooldownLogIntervalMs = 30000;\n',
        'plan request budget fields'
    )
    root_getters = '''    public int getRequestMinIntervalMs() {\n        return Math.max(0, requestMinIntervalMs);\n    }\n\n    public void setRequestMinIntervalMs(int requestMinIntervalMs) {\n        this.requestMinIntervalMs = requestMinIntervalMs;\n    }\n\n'''
    props = replace_once(
        props,
        root_getters,
        root_getters + '''    public long getRequestBudgetWindowMs() {\n        return Math.max(SAFE_REQUEST_BUDGET_WINDOW_MS, requestBudgetWindowMs);\n    }\n\n    public void setRequestBudgetWindowMs(long requestBudgetWindowMs) {\n        this.requestBudgetWindowMs = requestBudgetWindowMs;\n    }\n\n    public int getRequestBudgetMaxRequests() {\n        return Math.max(1, Math.min(requestBudgetMaxRequests, SAFE_REQUEST_BUDGET_MAX_REQUESTS));\n    }\n\n    public void setRequestBudgetMaxRequests(int requestBudgetMaxRequests) {\n        this.requestBudgetMaxRequests = requestBudgetMaxRequests;\n    }\n\n''',
        'root request budget accessors'
    )
    props = replace_once(
        props,
        '        plan.setRequestMinIntervalMs(requestMinIntervalMs);\n        plan.setCooldownLogIntervalMs(cooldownLogIntervalMs);\n',
        '        plan.setRequestMinIntervalMs(requestMinIntervalMs);\n'
        '        plan.setRequestBudgetWindowMs(requestBudgetWindowMs);\n'
        '        plan.setRequestBudgetMaxRequests(requestBudgetMaxRequests);\n'
        '        plan.setCooldownLogIntervalMs(cooldownLogIntervalMs);\n',
        'legacy plan request budget copy'
    )
    plan_getters = '        public int getRequestMinIntervalMs() { return Math.max(0, requestMinIntervalMs); }\n        public void setRequestMinIntervalMs(int requestMinIntervalMs) { this.requestMinIntervalMs = requestMinIntervalMs; }\n'
    props = replace_once(
        props,
        plan_getters,
        plan_getters
        + '        public long getRequestBudgetWindowMs() { return Math.max(SAFE_REQUEST_BUDGET_WINDOW_MS, requestBudgetWindowMs); }\n'
        + '        public void setRequestBudgetWindowMs(long requestBudgetWindowMs) { this.requestBudgetWindowMs = requestBudgetWindowMs; }\n'
        + '        public int getRequestBudgetMaxRequests() { return Math.max(1, Math.min(requestBudgetMaxRequests, SAFE_REQUEST_BUDGET_MAX_REQUESTS)); }\n'
        + '        public void setRequestBudgetMaxRequests(int requestBudgetMaxRequests) { this.requestBudgetMaxRequests = requestBudgetMaxRequests; }\n',
        'plan request budget accessors'
    )
    props = replace_once(
        props,
        '                    + ", requestMinIntervalMs=" + getRequestMinIntervalMs()\n                    + ", requestMaxAttempts=" + getRequestMaxAttempts()\n',
        '                    + ", requestMinIntervalMs=" + getRequestMinIntervalMs()\n'
        '                    + ", requestBudgetWindowMs=" + getRequestBudgetWindowMs()\n'
        '                    + ", requestBudgetMaxRequests=" + getRequestBudgetMaxRequests()\n'
        '                    + ", requestMaxAttempts=" + getRequestMaxAttempts()\n',
        'plan summary request budget details'
    )
    PROPS.write_text(props)

# 2) Service: in-memory rolling request budget enforced before every highscore HTTP fetch.
service = SERVICE.read_text()
if 'requestBudgetStarts' not in service:
    service = replace_once(service, 'import java.util.ArrayList;\n', 'import java.util.ArrayDeque;\nimport java.util.ArrayList;\n', 'HighscoreService ArrayDeque import')
    service = replace_once(service, 'import java.util.Comparator;\n', 'import java.util.Comparator;\nimport java.util.Deque;\n', 'HighscoreService Deque import')
    service = replace_once(
        service,
        '    private final AtomicLong lastRetrySleepLogAtMs = new AtomicLong(0);\n    private final Object httpBackoffLock = new Object();\n',
        '    private final AtomicLong lastRetrySleepLogAtMs = new AtomicLong(0);\n'
        '    private final AtomicLong lastRequestBudgetLogAtMs = new AtomicLong(0);\n'
        '    private final Object httpBackoffLock = new Object();\n'
        '    private final Object requestBudgetLock = new Object();\n'
        '    private final Deque<Long> requestBudgetStarts = new ArrayDeque<>();\n',
        'HighscoreService request budget fields'
    )
    service = replace_once(
        service,
        'requestJitterMs={}, requestMinIntervalMs={}, cooldownLogIntervalMs={}, progressLogIntervalScopes={}, worlds={}, categories={}, vocations={}, abortRunOnForbidden={}',
        'requestJitterMs={}, requestMinIntervalMs={}, requestBudgetWindowMs={}, requestBudgetMaxRequests={}, cooldownLogIntervalMs={}, progressLogIntervalScopes={}, worlds={}, categories={}, vocations={}, abortRunOnForbidden={}',
        'HighscoreService startup log placeholders'
    )
    service = replace_once(
        service,
        '                plan.getRequestJitterMs(),\n                plan.getRequestMinIntervalMs(),\n                plan.getCooldownLogIntervalMs(),\n',
        '                plan.getRequestJitterMs(),\n'
        '                plan.getRequestMinIntervalMs(),\n'
        '                plan.getRequestBudgetWindowMs(),\n'
        '                plan.getRequestBudgetMaxRequests(),\n'
        '                plan.getCooldownLogIntervalMs(),\n',
        'HighscoreService startup log args'
    )
    service = replace_once(
        service,
        '                awaitGlobalHttpCooldown(plan);\n                awaitGlobalRequestPace(plan);\n                throttleRequestWithJitter(plan);\n',
        '                awaitGlobalHttpCooldown(plan);\n'
        '                awaitHighscoreRequestBudget(plan);\n'
        '                awaitGlobalRequestPace(plan);\n'
        '                throttleRequestWithJitter(plan);\n',
        'HighscoreService request budget call'
    )
    budget_methods = '''    /**\n     * Hard safety budget for Tibia.com highscore requests. The public incident threshold discussed for\n     * highscores was 150k requests in 10 minutes, so this process keeps a rolling request-start budget\n     * at or below that ceiling even if someone later increases workers or removes request pacing.\n     */\n    private void awaitHighscoreRequestBudget(HighscoreScrapeProperties.Plan plan) {\n        int maxRequests = plan.getRequestBudgetMaxRequests();\n        long windowMs = plan.getRequestBudgetWindowMs();\n\n        while (true) {\n            long waitMs;\n            int used;\n            synchronized (requestBudgetLock) {\n                long now = System.currentTimeMillis();\n                purgeExpiredRequestBudgetEntries(now, windowMs);\n                used = requestBudgetStarts.size();\n                if (used < maxRequests) {\n                    requestBudgetStarts.addLast(now);\n                    return;\n                }\n\n                Long oldest = requestBudgetStarts.peekFirst();\n                waitMs = oldest == null ? 1L : Math.max(1L, oldest + windowMs - now);\n            }\n\n            logRequestBudgetHeartbeat(plan, used, maxRequests, windowMs, waitMs);\n            sleepMs(Math.min(waitMs, 1000L));\n        }\n    }\n\n    private void purgeExpiredRequestBudgetEntries(long now, long windowMs) {\n        long cutoff = now - windowMs;\n        while (!requestBudgetStarts.isEmpty() && requestBudgetStarts.peekFirst() <= cutoff) {\n            requestBudgetStarts.removeFirst();\n        }\n    }\n\n    private void logRequestBudgetHeartbeat(HighscoreScrapeProperties.Plan plan, int used, int maxRequests, long windowMs, long waitMs) {\n        long now = System.currentTimeMillis();\n        long intervalMs = plan.getCooldownLogIntervalMs();\n        long lastLog = lastRequestBudgetLogAtMs.get();\n        if (intervalMs > 0 && now - lastLog >= intervalMs && lastRequestBudgetLogAtMs.compareAndSet(lastLog, now)) {\n            log.warn(\n                    "[HIGHSCORE_SCRAPER] Highscore request budget exhausted. Waiting before next Tibia.com request: usedRequests={}, maxRequests={}, windowMs={}, waitMs={}",\n                    used,\n                    maxRequests,\n                    windowMs,\n                    waitMs\n            );\n        }\n    }\n\n'''
    service = replace_once(service, '    private void throttleRequestWithJitter(HighscoreScrapeProperties.Plan plan) {\n', budget_methods + '    private void throttleRequestWithJitter(HighscoreScrapeProperties.Plan plan) {\n', 'HighscoreService request budget methods')
    SERVICE.write_text(service)

# 3) YAML defaults: make the safe budget and long cooldown explicit.
if APP_YML.exists():
    app_yml = APP_YML.read_text()
    if 'request-budget-window-ms' not in app_yml:
        app_yml = replace_once(
            app_yml,
            '      page-delay-ms: 1000\n      world-limit: 0\n',
            '      page-delay-ms: 1000\n'
            '      world-limit: 0\n'
            '      request-min-interval-ms: 750\n'
            '      request-budget-window-ms: 600000   # hard rolling window: 10 minutes\n'
            '      request-budget-max-requests: 150000 # never exceed Tibia.com safe highscore ceiling\n'
            '      forbidden-initial-cooldown-ms: 259200000 # 72h after first 403/429\n'
            '      forbidden-max-cooldown-ms: 1209600000    # 14d cap after repeated 403/429\n'
            '      forbidden-cooldown-multiplier: 2.0\n'
            '      abort-run-on-forbidden: true\n',
            'application.yml highscore safe defaults'
        )
        APP_YML.write_text(app_yml)

if DEV_YML.exists():
    dev_yml = DEV_YML.read_text()
    if 'forbidden-initial-cooldown-ms' not in dev_yml:
        dev_yml = re.sub(
            r'(\n\s+forbidden-cooldown-ms:\s*\d+)',
            r'\1\n          forbidden-initial-cooldown-ms: 259200000\n          forbidden-max-cooldown-ms: 1209600000\n          forbidden-cooldown-multiplier: 2.0',
            dev_yml,
        )
    if 'request-budget-window-ms' not in dev_yml:
        dev_yml = re.sub(
            r'(\n\s+request-min-interval-ms:\s*\d+)',
            r'\1\n          request-budget-window-ms: 600000\n          request-budget-max-requests: 150000',
            dev_yml,
        )
    DEV_YML.write_text(dev_yml)

# 4) Tests/config characterization.
if PLAN_TEST.exists():
    test = PLAN_TEST.read_text()
    if 'getRequestBudgetWindowMs' not in test:
        test = replace_once(
            test,
            '        assertThat(dailyExp.getRequestMaxAttempts()).isEqualTo(1);\n        assertThat(dailyExp.isAbortRunOnForbidden()).isTrue();\n',
            '        assertThat(dailyExp.getRequestMaxAttempts()).isEqualTo(1);\n'
            '        assertThat(dailyExp.getForbiddenInitialCooldownMs()).isGreaterThanOrEqualTo(259_200_000L);\n'
            '        assertThat(dailyExp.getRequestBudgetWindowMs()).isGreaterThanOrEqualTo(600_000L);\n'
            '        assertThat(dailyExp.getRequestBudgetMaxRequests()).isLessThanOrEqualTo(150_000);\n'
            '        assertThat(dailyExp.isAbortRunOnForbidden()).isTrue();\n',
            'HighscorePlanConfigurationTest daily plan assertions'
        )
        test = replace_once(
            test,
            '            assertThat(plan.getRequestMaxAttempts()).as(name + " must not keep retrying 403/429 responses").isEqualTo(1);\n            assertThat(plan.isAbortRunOnForbidden()).as(name + " must abort on 403/429").isTrue();\n',
            '            assertThat(plan.getRequestMaxAttempts()).as(name + " must not keep retrying 403/429 responses").isEqualTo(1);\n'
            '            assertThat(plan.getForbiddenInitialCooldownMs()).as(name + " must cool down long enough after 403/429").isGreaterThanOrEqualTo(259_200_000L);\n'
            '            assertThat(plan.getRequestBudgetWindowMs()).as(name + " must use at least a 10-minute request budget window").isGreaterThanOrEqualTo(600_000L);\n'
            '            assertThat(plan.getRequestBudgetMaxRequests()).as(name + " must never exceed 150k requests per 10 minutes").isBetween(1, 150_000);\n'
            '            assertThat(plan.isAbortRunOnForbidden()).as(name + " must abort on 403/429").isTrue();\n',
            'HighscorePlanConfigurationTest enabled plan assertions'
        )
        PLAN_TEST.write_text(test)

if not BUDGET_TEST.exists():
    BUDGET_TEST.parent.mkdir(parents=True, exist_ok=True)
    BUDGET_TEST.write_text('''package com.nathan.tibiastats.config;\n\nimport org.junit.jupiter.api.Test;\n\nimport static org.assertj.core.api.Assertions.assertThat;\n\nclass HighscoreRequestBudgetConfigurationTest {\n    @Test\n    void planClampsRequestBudgetToTheSafeHighscoreCeiling() {\n        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();\n\n        plan.setRequestBudgetWindowMs(1);\n        plan.setRequestBudgetMaxRequests(999_999);\n\n        assertThat(plan.getRequestBudgetWindowMs()).isEqualTo(600_000L);\n        assertThat(plan.getRequestBudgetMaxRequests()).isEqualTo(150_000);\n    }\n\n    @Test\n    void defaultForbiddenCooldownIsConservativeEnoughToLeaveTheApplicationRunning() {\n        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();\n\n        assertThat(plan.getForbiddenInitialCooldownMs()).isEqualTo(259_200_000L);\n        assertThat(plan.getForbiddenMaxCooldownMs()).isEqualTo(1_209_600_000L);\n    }\n}\n''')

print('Applied highscore safe request budget and conservative 403 cooldown patch.')
PY

echo "Done. Backup created at: $BACKUP_DIR"
echo "Recommended checks: make test && make qa"
