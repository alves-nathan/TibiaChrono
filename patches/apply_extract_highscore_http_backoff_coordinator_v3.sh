#!/usr/bin/env bash
set -Eeuo pipefail

PATCH_NAME="highscore-http-backoff-coordinator-v3"
BACKUP_DIR="patches/.backups/${PATCH_NAME}-$(date +%Y%m%d%H%M%S)"

if [[ ! -f "pom.xml" || ! -d "src/main/java" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

required_files=(
  "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java"
  "src/main/java/com/nathan/tibiastats/infrastructure/persistence/HighscoreScrapeStateRepository.java"
)

mkdir -p "$BACKUP_DIR"
for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "ERROR: required file not found: $file" >&2
    exit 1
  fi
  mkdir -p "$BACKUP_DIR/$(dirname "$file")"
  cp "$file" "$BACKUP_DIR/$file"
done

python3 - <<'PY'
from pathlib import Path
import re

root = Path('.')
service = root / 'src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java'
text = service.read_text()

coordinator = root / 'src/main/java/com/nathan/tibiastats/application/service/HighscoreHttpBackoffCoordinator.java'
coordinator.write_text('''package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.infrastructure.persistence.HighscoreScrapeStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class HighscoreHttpBackoffCoordinator {
    private static final Logger log = LoggerFactory.getLogger(HighscoreHttpBackoffCoordinator.class);

    private final HighscoreScrapeStateRepository stateRepository;
    private final AtomicLong globalHttpCooldownUntilMs = new AtomicLong(0);
    private final AtomicLong lastCooldownLogAtMs = new AtomicLong(0);
    private final Object httpBackoffLock = new Object();

    public HighscoreHttpBackoffCoordinator(HighscoreScrapeStateRepository stateRepository) {
        this.stateRepository = stateRepository;
    }

    public HighscoreScrapeStateRepository.HighscoreHttpBackoffState getState() {
        return stateRepository.getHttpBackoffState();
    }

    public HighscoreScrapeStateRepository.HighscoreHttpBackoffState resetManually() {
        stateRepository.resetHttpBackoffAfterSuccess();
        globalHttpCooldownUntilMs.set(0);
        lastCooldownLogAtMs.set(0);
        return stateRepository.getHttpBackoffState();
    }

    public boolean isActive(String planName) {
        HighscoreScrapeStateRepository.HighscoreHttpBackoffState backoff = stateRepository.getHttpBackoffState();
        Instant now = Instant.now();
        if (backoff == null || !backoff.isActive(now)) {
            return false;
        }

        long untilMs = backoff.cooldownUntil() == null ? 0L : backoff.cooldownUntil().toEpochMilli();
        globalHttpCooldownUntilMs.getAndUpdate(value -> Math.max(value, untilMs));
        log.warn(
                "[HIGHSCORE_SCRAPER] Skipping highscore plan because global HTTP backoff is active: plan={}, remainingMs={}, cooldownUntil={}, consecutiveFailures={}, currentCooldownMs={}, lastReason={}",
                planName,
                backoff.remainingMs(now),
                backoff.cooldownUntil(),
                backoff.consecutiveFailures(),
                backoff.currentCooldownMs(),
                backoff.lastReason()
        );
        return true;
    }

    public void resetAfterSuccessfulRun(String planName, int successScopes, int emptyScopes) {
        HighscoreScrapeStateRepository.HighscoreHttpBackoffState backoff = stateRepository.getHttpBackoffState();
        if (backoff == null || (backoff.consecutiveFailures() <= 0 && backoff.cooldownUntil() == null)) {
            return;
        }

        stateRepository.resetHttpBackoffAfterSuccess();
        globalHttpCooldownUntilMs.set(0);
        log.info(
                "[HIGHSCORE_SCRAPER] Global highscore HTTP backoff reset after successful run: plan={}, successScopes={}, emptyScopes={}",
                planName,
                successScopes,
                emptyScopes
        );
    }

    public void awaitCooldown(HighscoreScrapeProperties.Plan plan) {
        while (true) {
            long now = System.currentTimeMillis();
            long until = globalHttpCooldownUntilMs.get();
            long waitMs = until - now;
            if (waitMs <= 0) {
                return;
            }
            logCooldownHeartbeat(plan, waitMs);
            sleepMs(Math.min(waitMs, 1000));
        }
    }

    public void activate(HighscoreScrapeProperties.Plan plan, String reason) {
        long initialCooldownMs = plan.getForbiddenInitialCooldownMs();
        if (initialCooldownMs <= 0) {
            return;
        }

        synchronized (httpBackoffLock) {
            HighscoreScrapeStateRepository.HighscoreHttpBackoffState current = stateRepository.getHttpBackoffState();
            Instant now = Instant.now();
            if (current != null && current.isActive(now)) {
                globalHttpCooldownUntilMs.getAndUpdate(value -> Math.max(value, current.cooldownUntil().toEpochMilli()));
                log.warn(
                        "[HIGHSCORE_SCRAPER] HTTP backoff already active after Tibia response. remainingMs={}, consecutiveFailures={}, currentCooldownMs={}, reason={}",
                        current.remainingMs(now),
                        current.consecutiveFailures(),
                        current.currentCooldownMs(),
                        reason
                );
                return;
            }

            HighscoreScrapeStateRepository.HighscoreHttpBackoffState backoff = stateRepository.activateHttpBackoff(
                    initialCooldownMs,
                    plan.getForbiddenMaxCooldownMs(),
                    plan.getForbiddenCooldownMultiplier(),
                    reason
            );
            long untilMs = backoff.cooldownUntil() == null ? 0L : backoff.cooldownUntil().toEpochMilli();
            globalHttpCooldownUntilMs.getAndUpdate(value -> Math.max(value, untilMs));
            log.warn(
                    "[HIGHSCORE_SCRAPER] Global highscore backoff activated. All highscore plans disabled until cooldown expires: cooldownMs={}, cooldownUntil={}, consecutiveFailures={}, maxCooldownMs={}, multiplier={}, reason={}",
                    backoff.currentCooldownMs(),
                    backoff.cooldownUntil(),
                    backoff.consecutiveFailures(),
                    plan.getForbiddenMaxCooldownMs(),
                    plan.getForbiddenCooldownMultiplier(),
                    reason
            );
        }
    }

    private void logCooldownHeartbeat(HighscoreScrapeProperties.Plan plan, long remainingMs) {
        long now = System.currentTimeMillis();
        long intervalMs = plan.getCooldownLogIntervalMs();
        long lastLog = lastCooldownLogAtMs.get();
        if (intervalMs > 0 && now - lastLog >= intervalMs && lastCooldownLogAtMs.compareAndSet(lastLog, now)) {
            log.info("[HIGHSCORE_SCRAPER] HTTP cooldown still active: remainingMs={}", Math.max(0, remainingMs));
        }
    }

    private void sleepMs(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting during highscore HTTP backoff", ex);
        }
    }
}
''')

if 'HighscoreHttpBackoffCoordinator' not in text:
    text = text.replace(
        '    private final HighscoreScrapeStateRepository stateRepository;\n    private final HighscoreStatRecordWriter statRecordWriter;\n',
        '    private final HighscoreScrapeStateRepository stateRepository;\n    private final HighscoreStatRecordWriter statRecordWriter;\n    private final HighscoreHttpBackoffCoordinator httpBackoffCoordinator;\n'
    )
    text = text.replace('    private final AtomicLong globalHttpCooldownUntilMs = new AtomicLong(0);\n', '')
    text = text.replace('    private final AtomicLong lastCooldownLogAtMs = new AtomicLong(0);\n', '')
    text = text.replace('    private final Object httpBackoffLock = new Object();\n', '')
    text = text.replace(
        '''            HighscoreScrapeStateRepository stateRepository,
            HighscoreStatRecordWriter statRecordWriter
    ) {''',
        '''            HighscoreScrapeStateRepository stateRepository,
            HighscoreStatRecordWriter statRecordWriter,
            HighscoreHttpBackoffCoordinator httpBackoffCoordinator
    ) {'''
    )
    text = text.replace(
        '''        this.stateRepository = stateRepository;
        this.statRecordWriter = statRecordWriter;
''',
        '''        this.stateRepository = stateRepository;
        this.statRecordWriter = statRecordWriter;
        this.httpBackoffCoordinator = httpBackoffCoordinator;
'''
    )
else:
    # If a previous partial application added the class name, still remove obsolete fields if present.
    text = text.replace('    private final AtomicLong globalHttpCooldownUntilMs = new AtomicLong(0);\n', '')
    text = text.replace('    private final AtomicLong lastCooldownLogAtMs = new AtomicLong(0);\n', '')
    text = text.replace('    private final Object httpBackoffLock = new Object();\n', '')

text = text.replace('if (isHttpBackoffActive(planName)) {', 'if (httpBackoffCoordinator.isActive(planName)) {')
text = text.replace('return stateRepository.getHttpBackoffState();', 'return httpBackoffCoordinator.getState();')
text = re.sub(
    r'    public HighscoreScrapeStateRepository\.HighscoreHttpBackoffState resetHttpBackoffManually\(\) \{.*?\n    \}',
    '    public HighscoreScrapeStateRepository.HighscoreHttpBackoffState resetHttpBackoffManually() {\n'
    '        return httpBackoffCoordinator.resetManually();\n'
    '    }',
    text,
    flags=re.S
)
text = text.replace('resetHttpBackoffAfterSuccessfulRun(planName, success, empty);', 'httpBackoffCoordinator.resetAfterSuccessfulRun(planName, success, empty);')
text = text.replace('awaitGlobalHttpCooldown(plan);', 'httpBackoffCoordinator.awaitCooldown(plan);')
text = text.replace('activateGlobalHttpCooldown(plan, rootMessage(ex));', 'httpBackoffCoordinator.activate(plan, rootMessage(ex));')

text = re.sub(
    r'\n\s*private boolean isHttpBackoffActive\(String planName\) \{.*?\n\s*private void throttleRequestWithJitter',
    '\n\n    private void throttleRequestWithJitter',
    text,
    flags=re.S
)
text = re.sub(
    r'\n\s*private void awaitGlobalHttpCooldown\(HighscoreScrapeProperties\.Plan plan\) \{.*?\n\s*private void sleepWithRetryHeartbeat',
    '\n\n    private void sleepWithRetryHeartbeat',
    text,
    flags=re.S
)

# Collapse excessive blank lines introduced by method removal without formatting the whole file.
text = re.sub(r'\n{4,}', '\n\n\n', text)
service.write_text(text)
PY

find src/main/java src/test/java -name '*.java' -type f -exec chmod 0644 {} +

cat <<MSG
Done. Highscore HTTP backoff coordinator extraction applied.
Backup directory: $BACKUP_DIR
Next step: make test
MSG
