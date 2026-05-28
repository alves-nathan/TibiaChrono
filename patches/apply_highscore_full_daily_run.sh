#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
BACKUP_DIR="${ROOT}/.tibiachrono-highscore-full-daily-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"

backup_file() {
  local file="$1"
  if [ -f "$file" ]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$file" "$BACKUP_DIR/$file"
  fi
}

require_file() {
  local file="$1"
  if [ ! -f "$file" ]; then
    echo "Missing required file: $file" >&2
    exit 1
  fi
}

PROPS="src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java"
STATE_REPO="src/main/java/com/nathan/tibiastats/infrastructure/persistence/HighscoreScrapeStateRepository.java"
SCHEDULER="src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java"
SERVICE="src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java"
APP_DEV="src/main/resources/application-dev.yml"

for f in "$PROPS" "$STATE_REPO" "$SCHEDULER" "$SERVICE" "$APP_DEV"; do
  require_file "$f"
  backup_file "$f"
done

python3 - <<'PY'
from pathlib import Path

props = Path("src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java")
s = props.read_text()
s = s.replace("private int scopesPerRun = 20;", "private int scopesPerRun = 0;")
s = s.replace("private int scopesPerRun = 1;", "private int scopesPerRun = 0;")
old = '''    public int getScopesPerRun() {
        return Math.max(1, scopesPerRun);
    }

    public void setScopesPerRun(int scopesPerRun) {
        this.scopesPerRun = scopesPerRun;
    }
'''
new = '''    /**
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
'''
if old in s:
    s = s.replace(old, new)
elif "boolean isAllScopesPerRun()" not in s:
    raise SystemExit("Could not patch HighscoreScrapeProperties#getScopesPerRun")
props.write_text(s)

state = Path("src/main/java/com/nathan/tibiastats/infrastructure/persistence/HighscoreScrapeStateRepository.java")
s = state.read_text()
old = '''        return all.stream()
                .filter(scope -> allowedWorldIds.contains(scope.worldId()))
                .filter(scope -> allowedCategories.contains(scope.category().name()))
                .filter(scope -> allowedVocations.contains(scope.vocationFilterId()))
                .limit(Math.max(1, limit))
                .toList();
'''
new = '''        List<HighscoreScope> filtered = all.stream()
                .filter(scope -> allowedWorldIds.contains(scope.worldId()))
                .filter(scope -> allowedCategories.contains(scope.category().name()))
                .filter(scope -> allowedVocations.contains(scope.vocationFilterId()))
                .toList();

        if (limit <= 0) {
            return filtered;
        }

        return filtered.stream()
                .limit(limit)
                .toList();
'''
if old in s:
    s = s.replace(old, new)
elif "if (limit <= 0)" not in s:
    raise SystemExit("Could not patch HighscoreScrapeStateRepository#findNextScopes")
state.write_text(s)

scheduler = Path("src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java")
s = scheduler.read_text()
old = '''                "[HIGHSCORE_SCRAPER] Scheduler configured: enabled={}, cron={}, categories={}, vocations={}, maxPages={}, pageDelayMs={}, worldLimit={}, scopesPerRun={}, parallelism={}",
                properties.isEnabled(),
                properties.getCron(),
                properties.getCategories(),
                properties.getVocations(),
                properties.getMaxPages(),
                properties.getPageDelayMs(),
                properties.getWorldLimit(),
                properties.getScopesPerRun(),
                properties.getParallelism()
'''
new = '''                "[HIGHSCORE_SCRAPER] Scheduler configured: enabled={}, cron={}, categories={}, vocations={}, maxPages={}, pageDelayMs={}, worldLimit={}, scopesPerRun={}, allScopesPerRun={}, parallelism={}",
                properties.isEnabled(),
                properties.getCron(),
                properties.getCategories(),
                properties.getVocations(),
                properties.getMaxPages(),
                properties.getPageDelayMs(),
                properties.getWorldLimit(),
                properties.getScopesPerRun(),
                properties.isAllScopesPerRun(),
                properties.getParallelism()
'''
if old in s:
    s = s.replace(old, new)
elif "allScopesPerRun" not in s:
    raise SystemExit("Could not patch HighscoreScrapeScheduler logging")
scheduler.write_text(s)

service = Path("src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java")
s = service.read_text()
old = '''        log.info(
                "[HIGHSCORE_SCRAPER] Starting run: scopes={}, parallelism={}, maxPages={}, pageDelayMs={}, worlds={}, categories={}, vocations={}",
                scopes.size(),
                properties.getParallelism(),
                properties.getMaxPages(),
                properties.getPageDelayMs(),
                worlds.size(),
                categories.size(),
                vocationFilterIds.size()
        );
'''
new = '''        log.info(
                "[HIGHSCORE_SCRAPER] Starting run: selectedScopes={}, scopesPerRun={}, allScopesPerRun={}, parallelism={}, maxPages={}, pageDelayMs={}, worlds={}, categories={}, vocations={}",
                scopes.size(),
                properties.getScopesPerRun(),
                properties.isAllScopesPerRun(),
                properties.getParallelism(),
                properties.getMaxPages(),
                properties.getPageDelayMs(),
                worlds.size(),
                categories.size(),
                vocationFilterIds.size()
        );
'''
if old in s:
    s = s.replace(old, new)
elif "selectedScopes" not in s:
    raise SystemExit("Could not patch HighscoreService start logging")
service.write_text(s)

app = Path("src/main/resources/application-dev.yml")
s = app.read_text()
# Keep categories/vocations as-is, but make the development profile represent the intended final daily behavior.
s = s.replace('cron: "0 */5 * * * *"', 'cron: "0 0 7 * * *"')
s = s.replace('scopes-per-run: 20', 'scopes-per-run: 0')
app.write_text(s)
PY

echo "Applied highscore full daily run support. Backup saved to: $BACKUP_DIR"
echo "Meaning of tibiastats.scrape.highscores.scopes-per-run now:"
echo "  0  = process every configured scope in the scheduled run"
echo "  >0 = process only that many scopes per run"
