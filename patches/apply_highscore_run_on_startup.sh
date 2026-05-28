#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
SCHEDULER="$ROOT/src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java"
PROPS="$ROOT/src/main/java/com/nathan/tibiastats/config/HighscoreScrapeProperties.java"
APP_DEV="$ROOT/src/main/resources/application-dev.yml"
BACKUP="$ROOT/.tibiachrono-highscore-startup-run-backup-$(date +%Y%m%d%H%M%S)"

mkdir -p "$BACKUP"

for file in "$SCHEDULER" "$PROPS" "$APP_DEV"; do
  if [[ -f "$file" ]]; then
    mkdir -p "$BACKUP/$(dirname "${file#$ROOT/}")"
    cp "$file" "$BACKUP/${file#$ROOT/}"
  fi
done

cat > "$SCHEDULER" <<'JAVA'
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
                "[HIGHSCORE_SCRAPER] Scheduler configured: enabled={}, cron={}, zone={}, runOnStartup={}, startupDelayMs={}, categories={}, vocations={}, maxPages={}, pageDelayMs={}, worldLimit={}, scopesPerRun={}, allScopesPerRun={}, parallelism={}",
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
                properties.getParallelism()
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
JAVA

cat > "$PROPS" <<'JAVA'
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
    private String zone = "America/Sao_Paulo";
    private boolean runOnStartup = false;
    private long startupDelayMs = 0;
    private String categories = "EXPERIENCE";
    private String vocations = "0";
    private int maxPages = 100;
    private int pageDelayMs = 500;
    private int worldLimit = 0;
    private int scopesPerRun = 0;
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
JAVA

python3 - <<'PY' "$APP_DEV"
from pathlib import Path
import sys
p = Path(sys.argv[1])
text = p.read_text()
lines = text.splitlines()

# Find the highscores block under tibiastats.scrape. This is indentation-based and preserves existing config.
start = None
for i, line in enumerate(lines):
    if line.strip() == 'highscores:' and line.startswith('    '):
        start = i
        break

if start is None:
    raise SystemExit('Could not find tibiastats.scrape.highscores block in application-dev.yml')

end = len(lines)
for i in range(start + 1, len(lines)):
    line = lines[i]
    if line and not line.startswith('      ') and not line.startswith('    #'):
        # A new block with indentation <= four spaces starts.
        if not line.startswith('      '):
            end = i
            break

block = lines[start:end]
existing = {}
for idx, line in enumerate(block):
    stripped = line.strip()
    if ':' in stripped and not stripped.startswith('#'):
        key = stripped.split(':', 1)[0]
        existing[key] = idx

required = {
    'zone': '      zone: "America/Sao_Paulo"',
    'run-on-startup': '      run-on-startup: true',
    'startup-delay-ms': '      startup-delay-ms: 0',
}

# Set existing values or insert after cron/zone where possible.
for key, value in required.items():
    if key in existing:
        block[existing[key]] = value
    else:
        insert_after_keys = ['cron', 'zone', 'run-on-startup']
        insert_pos = 1
        for after_key in insert_after_keys:
            if after_key in existing:
                insert_pos = max(insert_pos, existing[after_key] + 1)
        block.insert(insert_pos, value)
        # recompute indices after insert
        existing = {}
        for idx, line in enumerate(block):
            stripped = line.strip()
            if ':' in stripped and not stripped.startswith('#'):
                existing[stripped.split(':', 1)[0]] = idx

lines = lines[:start] + block + lines[end:]
p.write_text('\n'.join(lines) + '\n')
PY

# Keep Java files as normal source files instead of executable scripts.
chmod 0644 "$SCHEDULER" "$PROPS" || true

echo "Highscore startup run support applied. Backup created at: $BACKUP"
echo "Current highscore config:"
grep -n "highscores:" -A18 "$APP_DEV" || true
