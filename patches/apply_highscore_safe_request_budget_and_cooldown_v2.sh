#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
if [ ! -f "$ROOT/pom.xml" ] || [ ! -d "$ROOT/src/main/java" ]; then
  echo "Run this script from the TibiaChrono project root." >&2
  exit 1
fi

BACKUP_DIR="$ROOT/.tibiachrono-highscore-safe-budget-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"

backup_file() {
  local path="$1"
  if [ -f "$ROOT/$path" ]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$path")"
    cp "$ROOT/$path" "$BACKUP_DIR/$path"
  fi
}

backup_file "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java"
backup_file "src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java"
backup_file "src/main/java/com/nathan/tibiastats/application/service/AdminScraperService.java"
backup_file "src/main/resources/application.yml"
backup_file "src/main/resources/application-dev.yml"
backup_file "src/test/java/com/nathan/tibiastats/config/HighscorePlanConfigurationTest.java"

python3 - <<'PY'
from pathlib import Path
import re
import sys

ROOT = Path.cwd()
SERVICE = ROOT / "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java"
PROPS = ROOT / "src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java"
ADMIN = ROOT / "src/main/java/com/nathan/tibiastats/application/service/AdminScraperService.java"
APP = ROOT / "src/main/resources/application.yml"
APP_DEV = ROOT / "src/main/resources/application-dev.yml"
TEST = ROOT / "src/test/java/com/nathan/tibiastats/config/HighscorePlanConfigurationTest.java"

for path in (SERVICE, PROPS, ADMIN, APP, APP_DEV, TEST):
    if not path.exists():
        raise SystemExit(f"Required file not found: {path.relative_to(ROOT)}")


def write(path: Path, text: str):
    path.write_text(text, encoding="utf-8", newline="\n")


def replace_required(text: str, old: str, new: str, description: str) -> str:
    if old not in text:
        if new in text:
            return text
        raise SystemExit(f"Could not safely update {description}. Expected block not found.")
    return text.replace(old, new, 1)


def insert_after_once(text: str, anchor: str, insertion: str, marker: str, description: str) -> str:
    if marker in text:
        return text
    if anchor not in text:
        raise SystemExit(f"Could not safely update {description}. Expected anchor not found.")
    return text.replace(anchor, anchor + insertion, 1)


def insert_after_all_budget_keys(yaml_text: str) -> str:
    """Add budget keys after every request-min-interval-ms line that does not already have them nearby."""
    lines = yaml_text.splitlines()
    out = []
    for idx, line in enumerate(lines):
        out.append(line)
        if "request-min-interval-ms:" not in line:
            continue
        indent = line[: len(line) - len(line.lstrip())]
        lookahead = "\n".join(lines[idx + 1: idx + 6])
        if "request-budget-max-requests:" not in lookahead:
            out.append(f"{indent}request-budget-max-requests: 150000")
        if "request-budget-window-ms:" not in lookahead:
            out.append(f"{indent}request-budget-window-ms: 600000")
    return "\n".join(out) + "\n"


def insert_after_all_forbidden_keys(yaml_text: str) -> str:
    """Add explicit progressive cooldown keys after every deprecated forbidden-cooldown-ms line."""
    yaml_text = re.sub(r"forbidden-cooldown-ms:\s*\d+", "forbidden-cooldown-ms: 259200000", yaml_text)
    lines = yaml_text.splitlines()
    out = []
    for idx, line in enumerate(lines):
        out.append(line)
        if "forbidden-cooldown-ms:" not in line:
            continue
        indent = line[: len(line) - len(line.lstrip())]
        lookahead = "\n".join(lines[idx + 1: idx + 8])
        if "forbidden-initial-cooldown-ms:" not in lookahead:
            out.append(f"{indent}forbidden-initial-cooldown-ms: 259200000")
        if "forbidden-max-cooldown-ms:" not in lookahead:
            out.append(f"{indent}forbidden-max-cooldown-ms: 1209600000")
        if "forbidden-cooldown-multiplier:" not in lookahead:
            out.append(f"{indent}forbidden-cooldown-multiplier: 2.0")
    return "\n".join(out) + "\n"

# ---------------------------------------------------------------------------
# HighscoreScrapeProperties.java
# ---------------------------------------------------------------------------
props = PROPS.read_text(encoding="utf-8")

props = insert_after_once(
    props,
    "    private static final Logger log = LoggerFactory.getLogger(HighscoreScrapeProperties.class);\n",
    "    private static final int HIGHSCORE_REQUEST_BUDGET_HARD_LIMIT = 150_000;\n"
    "    private static final long HIGHSCORE_REQUEST_BUDGET_WINDOW_MS = 600_000L;\n",
    "HIGHSCORE_REQUEST_BUDGET_HARD_LIMIT",
    "HighscoreScrapeProperties request budget constants",
)

props = re.sub(
    r"private long forbiddenInitialCooldownMs = \d+L;\s*//[^\n]*",
    "private long forbiddenInitialCooldownMs = 259200000L; // 72h",
    props,
)
props = re.sub(
    r"private long forbiddenMaxCooldownMs = \d+L;\s*//[^\n]*",
    "private long forbiddenMaxCooldownMs = 1209600000L; // 14d",
    props,
)

# Add top-level and nested field defaults after each requestMinIntervalMs field.
def add_budget_fields_after_request_interval(match):
    indent = match.group(1)
    return (
        match.group(0)
        + f"{indent}private int requestBudgetMaxRequests = HIGHSCORE_REQUEST_BUDGET_HARD_LIMIT;\n"
        + f"{indent}private long requestBudgetWindowMs = HIGHSCORE_REQUEST_BUDGET_WINDOW_MS;\n"
    )

props = re.sub(
    r"^(\s*)private int requestMinIntervalMs = 750;\n(?!\s*private int requestBudgetMaxRequests)",
    add_budget_fields_after_request_interval,
    props,
    flags=re.MULTILINE,
)

# Top-level getters/setters.
top_level_request_interval_methods = """    public int getRequestMinIntervalMs() {
        return Math.max(0, requestMinIntervalMs);
    }

    public void setRequestMinIntervalMs(int requestMinIntervalMs) {
        this.requestMinIntervalMs = requestMinIntervalMs;
    }
"""
top_level_budget_methods = top_level_request_interval_methods + """
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
"""
if props.count("public int getRequestBudgetMaxRequests()") < 1:
    props = replace_required(
        props,
        top_level_request_interval_methods,
        top_level_budget_methods,
        "HighscoreScrapeProperties top-level request budget accessors",
    )

# Legacy plan copy.
props = insert_after_once(
    props,
    "        plan.setRequestMinIntervalMs(requestMinIntervalMs);\n",
    "        plan.setRequestBudgetMaxRequests(requestBudgetMaxRequests);\n"
    "        plan.setRequestBudgetWindowMs(requestBudgetWindowMs);\n",
    "plan.setRequestBudgetMaxRequests(requestBudgetMaxRequests);",
    "HighscoreScrapeProperties legacy plan budget copy",
)

# Nested Plan single-line getters/setters.
plan_interval_methods = """        public int getRequestMinIntervalMs() { return Math.max(0, requestMinIntervalMs); }
        public void setRequestMinIntervalMs(int requestMinIntervalMs) { this.requestMinIntervalMs = requestMinIntervalMs; }
"""
plan_budget_methods = plan_interval_methods + """        public int getRequestBudgetMaxRequests() {
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
"""
if props.count("public int getRequestBudgetMaxRequests()") < 2:
    props = replace_required(
        props,
        plan_interval_methods,
        plan_budget_methods,
        "HighscoreScrapeProperties plan request budget accessors",
    )

props = insert_after_once(
    props,
    "                    + \", requestMinIntervalMs=\" + getRequestMinIntervalMs()\n",
    "                    + \", requestBudgetMaxRequests=\" + getRequestBudgetMaxRequests()\n"
    "                    + \", requestBudgetWindowMs=\" + getRequestBudgetWindowMs()\n",
    "requestBudgetMaxRequests=",
    "HighscoreScrapeProperties plan summary request budget fields",
)

write(PROPS, props)

# ---------------------------------------------------------------------------
# HighscoreService.java
# ---------------------------------------------------------------------------
service = SERVICE.read_text(encoding="utf-8")

if "import java.util.ArrayDeque;" not in service:
    service = replace_required(
        service,
        "import java.util.ArrayList;\n",
        "import java.util.ArrayDeque;\nimport java.util.ArrayList;\n",
        "HighscoreService ArrayDeque import",
    )

service = insert_after_once(
    service,
    "    private final AtomicLong nextAllowedHttpRequestAtMs = new AtomicLong(0);\n",
    "    private final Object requestBudgetLock = new Object();\n"
    "    private final ArrayDeque<Long> recentHighscoreRequestStarts = new ArrayDeque<>();\n"
    "    private final AtomicLong lastRequestBudgetLogAtMs = new AtomicLong(0);\n",
    "recentHighscoreRequestStarts",
    "HighscoreService request budget fields",
)

if "awaitGlobalRequestBudget(plan);" not in service:
    service = replace_required(
        service,
        "                awaitGlobalHttpCooldown(plan);\n"
        "                awaitGlobalRequestPace(plan);\n"
        "                throttleRequestWithJitter(plan);\n"
        "                List<HighscorePort.HighscoreRow> rows = highscorePort.fetchHighscores(\n",
        "                awaitGlobalHttpCooldown(plan);\n"
        "                awaitGlobalRequestPace(plan);\n"
        "                throttleRequestWithJitter(plan);\n"
        "                awaitGlobalRequestBudget(plan);\n"
        "                List<HighscorePort.HighscoreRow> rows = highscorePort.fetchHighscores(\n",
        "HighscoreService request budget call before highscore fetch",
    )

budget_method = """
    /**
     * Hard global request budget for all highscore workers in this JVM. This is intentionally independent from
     * requestMinIntervalMs: if a future configuration increases parallelism or lowers pacing, the scraper still
     * cannot start more than the configured hard-capped number of highscore HTTP requests in the configured window.
     */
    private void awaitGlobalRequestBudget(HighscoreScrapeProperties.Plan plan) {
        int maxRequests = plan.getRequestBudgetMaxRequests();
        long windowMs = plan.getRequestBudgetWindowMs();

        while (true) {
            long now = System.currentTimeMillis();
            long waitMs;
            synchronized (requestBudgetLock) {
                pruneHighscoreRequestBudget(now, windowMs);
                if (recentHighscoreRequestStarts.size() < maxRequests) {
                    recentHighscoreRequestStarts.addLast(now);
                    return;
                }

                Long oldestRequest = recentHighscoreRequestStarts.peekFirst();
                waitMs = oldestRequest == null ? 1L : Math.max(1L, oldestRequest + windowMs - now);
            }

            logRequestBudgetHeartbeat(plan, maxRequests, windowMs, waitMs);
            sleepMs(Math.min(waitMs, 1000));
        }
    }

    private void pruneHighscoreRequestBudget(long now, long windowMs) {
        long cutoff = now - windowMs;
        while (!recentHighscoreRequestStarts.isEmpty() && recentHighscoreRequestStarts.peekFirst() <= cutoff) {
            recentHighscoreRequestStarts.removeFirst();
        }
    }

    private void logRequestBudgetHeartbeat(HighscoreScrapeProperties.Plan plan, int maxRequests, long windowMs, long waitMs) {
        long now = System.currentTimeMillis();
        long intervalMs = plan.getCooldownLogIntervalMs();
        long lastLog = lastRequestBudgetLogAtMs.get();
        if (intervalMs > 0 && now - lastLog >= intervalMs && lastRequestBudgetLogAtMs.compareAndSet(lastLog, now)) {
            log.warn(
                    "[HIGHSCORE_SCRAPER] Global highscore request budget exhausted. Waiting before next request: maxRequests={}, windowMs={}, waitMs={}",
                    maxRequests,
                    windowMs,
                    waitMs
            );
        }
    }
"""
if "private void awaitGlobalRequestBudget(HighscoreScrapeProperties.Plan plan)" not in service:
    service = replace_required(
        service,
        "    /**\n"
        "     * Global pacing across all virtual threads. Semaphores cap concurrent requests, but they do not prevent bursts\n"
        "     * where many workers fire at the same millisecond. Tibia.com responds with 403 when bursts are too aggressive,\n"
        "     * so we also space out request starts globally.\n"
        "     */\n"
        "    private void awaitGlobalRequestPace(HighscoreScrapeProperties.Plan plan) {\n",
        budget_method + "\n"
        "    /**\n"
        "     * Global pacing across all virtual threads. Semaphores cap concurrent requests, but they do not prevent bursts\n"
        "     * where many workers fire at the same millisecond. Tibia.com responds with 403 when bursts are too aggressive,\n"
        "     * so we also space out request starts globally.\n"
        "     */\n"
        "    private void awaitGlobalRequestPace(HighscoreScrapeProperties.Plan plan) {\n",
        "HighscoreService request budget methods",
    )

write(SERVICE, service)

# ---------------------------------------------------------------------------
# AdminScraperService.java
# ---------------------------------------------------------------------------
admin = ADMIN.read_text(encoding="utf-8")
admin = insert_after_once(
    admin,
    "                plan.getRequestMinIntervalMs(),\n",
    "                plan.getRequestBudgetMaxRequests(),\n"
    "                plan.getRequestBudgetWindowMs(),\n",
    "plan.getRequestBudgetMaxRequests(),",
    "AdminScraperService highscore plan status constructor",
)
admin = insert_after_once(
    admin,
    "            int requestMinIntervalMs,\n",
    "            int requestBudgetMaxRequests,\n"
    "            long requestBudgetWindowMs,\n",
    "int requestBudgetMaxRequests,",
    "AdminScraperService highscore plan status record",
)
write(ADMIN, admin)

# ---------------------------------------------------------------------------
# YAML config files
# ---------------------------------------------------------------------------
app = APP.read_text(encoding="utf-8")
# Add explicit safe defaults in the legacy top-level production highscore block.
if "request-budget-max-requests:" not in app:
    app = replace_required(
        app,
        "      page-delay-ms: 1000\n      world-limit: 0\n",
        "      page-delay-ms: 1000\n"
        "      world-limit: 0\n"
        "      request-min-interval-ms: 750\n"
        "      request-budget-max-requests: 150000\n"
        "      request-budget-window-ms: 600000\n"
        "      forbidden-initial-cooldown-ms: 259200000\n"
        "      forbidden-max-cooldown-ms: 1209600000\n"
        "      forbidden-cooldown-multiplier: 2.0\n"
        "      abort-run-on-forbidden: true\n",
        "application.yml highscore safe defaults",
    )
write(APP, app)

app_dev = APP_DEV.read_text(encoding="utf-8")
app_dev = insert_after_all_forbidden_keys(app_dev)
app_dev = insert_after_all_budget_keys(app_dev)
write(APP_DEV, app_dev)

# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------
test = TEST.read_text(encoding="utf-8")
test = insert_after_once(
    test,
    "        assertThat(dailyExp.getRequestMaxAttempts()).isEqualTo(1);\n",
    "        assertThat(dailyExp.getRequestBudgetMaxRequests()).isLessThanOrEqualTo(150_000);\n"
    "        assertThat(dailyExp.getRequestBudgetWindowMs()).isGreaterThanOrEqualTo(600_000);\n"
    "        assertThat(dailyExp.getForbiddenInitialCooldownMs()).isGreaterThanOrEqualTo(259_200_000L);\n",
    "dailyExp.getRequestBudgetMaxRequests()",
    "HighscorePlanConfigurationTest daily-exp assertions",
)

test = insert_after_once(
    test,
    "            assertThat(plan.isRunOnStartup()).as(name + \" should not run automatically on every app boot\").isFalse();\n",
    "            assertThat(plan.getRequestBudgetMaxRequests()).as(name + \" must stay within the 150k/10min hard budget\").isLessThanOrEqualTo(150_000);\n"
    "            assertThat(plan.getRequestBudgetWindowMs()).as(name + \" must use at least a 10 minute budget window\").isGreaterThanOrEqualTo(600_000);\n"
    "            assertThat(plan.getForbiddenInitialCooldownMs()).as(name + \" must cool down long enough after 403/429\").isGreaterThanOrEqualTo(259_200_000L);\n"
    "            assertThat(plan.getForbiddenMaxCooldownMs()).as(name + \" must allow progressive multi-day cooldowns\").isGreaterThanOrEqualTo(1_209_600_000L);\n",
    "plan.getRequestBudgetMaxRequests()",
    "HighscorePlanConfigurationTest per-plan budget assertions",
)

if "void requestBudgetAccessorsHardCapUnsafeConfiguration" not in test:
    insertion = """
    @Test
    void requestBudgetAccessorsHardCapUnsafeConfiguration() {
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        properties.setRequestBudgetMaxRequests(999_999);
        properties.setRequestBudgetWindowMs(1_000);
        assertThat(properties.getRequestBudgetMaxRequests()).isEqualTo(150_000);
        assertThat(properties.getRequestBudgetWindowMs()).isEqualTo(600_000L);

        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setRequestBudgetMaxRequests(999_999);
        plan.setRequestBudgetWindowMs(1_000);
        assertThat(plan.getRequestBudgetMaxRequests()).isEqualTo(150_000);
        assertThat(plan.getRequestBudgetWindowMs()).isEqualTo(600_000L);
    }

"""
    test = replace_required(
        test,
        "    private HighscoreScrapeProperties bindApplicationDevHighscoreProperties() {\n",
        insertion + "    private HighscoreScrapeProperties bindApplicationDevHighscoreProperties() {\n",
        "HighscorePlanConfigurationTest request budget hard cap test",
    )
write(TEST, test)

print("Highscore safe request budget and cooldown patch applied successfully.")
PY

echo "Patch applied successfully. Backup created at: $BACKUP_DIR"
echo "Recommended validation: make test && make qa"
