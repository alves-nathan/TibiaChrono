#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
BACKUP_DIR="$ROOT/.tibiachrono-highscore-scheduler-fix-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"

backup_file() {
  local file="$1"
  if [ -f "$file" ]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$file" "$BACKUP_DIR/$file"
  fi
}

backup_file "src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java"
backup_file "src/main/resources/application-dev.yml"
backup_file "docker-compose.dev.yml"

cat > src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java <<'JAVA'
package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.HighscoreService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HighscoreScrapeScheduler {
    private static final Logger log = LoggerFactory.getLogger(HighscoreScrapeScheduler.class);
    private static final String LOG_PREFIX = "[HIGHSCORE_SCRAPER]";

    private final HighscoreService service;

    @Value("${tibiastats.scrape.highscores.enabled:true}")
    private boolean enabled;

    @Value("${tibiastats.scrape.highscores.cron:0 0 7 * * *}")
    private String cron;

    public HighscoreScrapeScheduler(HighscoreService service) {
        this.service = service;
    }

    @PostConstruct
    public void logConfiguration() {
        log.info("{} Scheduler configured: enabled={}, cron={}", LOG_PREFIX, enabled, cron);
    }

    @Scheduled(cron = "${tibiastats.scrape.highscores.cron:0 0 7 * * *}")
    public void run() {
        if (!enabled) {
            log.info("{} Scheduler tick ignored because highscores scraper is disabled. cron={}", LOG_PREFIX, cron);
            return;
        }

        log.info("{} Scheduler tick started. cron={}", LOG_PREFIX, cron);
        service.updateAllHighscores();
    }
}
JAVA

python3 - <<'PY'
from pathlib import Path
import re

app_dev = Path('src/main/resources/application-dev.yml')
s = app_dev.read_text()
new_block = '''    highscores:\n      enabled: true\n      cron: "0 */2 * * * *" # dev/test: every 2 minutes\n      categories: "EXPERIENCE"\n      vocations: "0"\n      max-pages: 2\n      page-delay-ms: 1000\n      world-limit: 1\n'''
# Replace highscores block under tibiastats.scrape until the next sibling under tibiastats, currently jwt.
s2 = re.sub(r'    highscores:\n(?:      .*\n)+(?=  jwt:)', new_block, s)
if s2 == s:
    raise SystemExit('Could not replace highscores block in src/main/resources/application-dev.yml')
app_dev.write_text(s2)

compose = Path('docker-compose.dev.yml')
s = compose.read_text()
replacements = {
    r'TIBIASTATS_SCRAPE_HIGHSCORES_CRON:\s*"[^"]*"': 'TIBIASTATS_SCRAPE_HIGHSCORES_CRON: "0 */2 * * * *"',
    r'TIBIASTATS_SCRAPE_HIGHSCORES_ENABLED:\s*"[^"]*"': 'TIBIASTATS_SCRAPE_HIGHSCORES_ENABLED: "true"',
    r'TIBIASTATS_SCRAPE_HIGHSCORES_CATEGORIES:\s*"[^"]*"': 'TIBIASTATS_SCRAPE_HIGHSCORES_CATEGORIES: "EXPERIENCE"',
    r'TIBIASTATS_SCRAPE_HIGHSCORES_VOCATIONS:\s*"[^"]*"': 'TIBIASTATS_SCRAPE_HIGHSCORES_VOCATIONS: "0"',
    r'TIBIASTATS_SCRAPE_HIGHSCORES_MAX_PAGES:\s*"[^"]*"': 'TIBIASTATS_SCRAPE_HIGHSCORES_MAX_PAGES: "2"',
    r'TIBIASTATS_SCRAPE_HIGHSCORES_PAGE_DELAY_MS:\s*"[^"]*"': 'TIBIASTATS_SCRAPE_HIGHSCORES_PAGE_DELAY_MS: "1000"',
    r'TIBIASTATS_SCRAPE_HIGHSCORES_WORLD_LIMIT:\s*"[^"]*"': 'TIBIASTATS_SCRAPE_HIGHSCORES_WORLD_LIMIT: "1"',
}
for pattern, repl in replacements.items():
    s2, n = re.subn(pattern, repl, s)
    if n == 0:
        # Insert missing variable after character details batch size.
        if 'TIBIASTATS_SCRAPE_CHARACTER_DETAILS_BATCH_SIZE:' in s:
            indent = '      '
            key = repl.split(':', 1)[0]
            s = re.sub(r'(      TIBIASTATS_SCRAPE_CHARACTER_DETAILS_BATCH_SIZE:\s*"[^"]*"\n)', r'\1' + indent + repl + '\n', s)
            continue
        raise SystemExit(f'Could not find or insert {pattern} in docker-compose.dev.yml')
    s = s2
compose.write_text(s)
PY

chmod 644 src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java src/main/resources/application-dev.yml docker-compose.dev.yml

echo "Highscore scheduler visibility/test config applied. Backup created at: $BACKUP_DIR"
