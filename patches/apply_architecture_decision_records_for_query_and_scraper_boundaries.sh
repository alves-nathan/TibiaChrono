#!/usr/bin/env bash
set -Eeuo pipefail

PATCH_NAME="architecture-decision-records-query-scraper-boundaries"
BACKUP_DIR="patches/.backups/${PATCH_NAME}-$(date +%Y%m%d%H%M%S)"

if [[ ! -f "pom.xml" || ! -d "docs/architecture" ]]; then
  echo "ERROR: run this script from the project root after the architecture docs patch." >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
for file in \
  docs/architecture/000-index.md \
  docs/architecture/adr/ADR-0004-separate-read-models-from-write-side-services.md \
  docs/architecture/adr/ADR-0005-extract-highscore-http-backoff-coordinator.md \
  docs/architecture/adr/ADR-0006-use-html-fixture-characterization-tests-for-scrapers.md; do
  if [[ -f "$file" ]]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$file" "$BACKUP_DIR/$file"
  fi
done

python3 - <<'PY'
from pathlib import Path

adr = Path('docs/architecture/adr')
adr.mkdir(parents=True, exist_ok=True)

(adr / 'ADR-0004-separate-read-models-from-write-side-services.md').write_text('''# ADR-0004 - Separate read models from write-side application services

## Status

Accepted

## Context

TibiaChrono has analytical API endpoints that are naturally query-heavy. Some of these use SQL/JDBC directly because they need efficient joins, projections and aggregations over historical data.

Keeping these classes inside the general `application.service` package makes it harder to distinguish write-side orchestration from read-side query models. It also makes architectural rules too permissive because JDBC dependencies may look acceptable everywhere in the application layer.

## Decision

Read/query services must live under `com.nathan.tibiastats.application.query` and be marked with `@ReadModelComponent`.

Write-side application services remain under `com.nathan.tibiastats.application.service` and should not depend directly on Spring JDBC.

## Consequences

- JDBC remains allowed for explicit read models.
- Write-side use cases stay easier to review and refactor toward ports/adapters.
- ArchUnit can enforce the boundary and prevent accidental JDBC growth in application services.
- Existing large query services can be decomposed incrementally without changing endpoint behavior.
''')

(adr / 'ADR-0005-extract-highscore-http-backoff-coordinator.md').write_text('''# ADR-0005 - Extract highscore HTTP backoff coordination

## Status

Accepted

## Context

The highscore scraper must handle remote rate limiting and blocking responses such as HTTP 403/429. The original orchestration mixed scraping workflow, scope processing, request pacing and global cooldown state in one large service.

That made it harder to reason about the cooldown policy and increased the risk of future changes accidentally bypassing the global protection.

## Decision

Global HTTP backoff/cooldown state for highscore scraping is owned by `HighscoreHttpBackoffCoordinator`.

The highscore orchestration service asks the coordinator whether a run should continue and notifies it about success or rate-limit failures.

## Consequences

- Cooldown logic becomes isolated and testable.
- Future scraper plans share one global protection mechanism.
- The highscore service can be decomposed further without duplicating rate-limit state.
''')

(adr / 'ADR-0006-use-html-fixture-characterization-tests-for-scrapers.md').write_text('''# ADR-0006 - Use HTML fixture characterization tests for scrapers

## Status

Accepted

## Context

Tibia.com HTML is an external dependency and can change without notice. Parser refactors are risky when they depend only on live network behavior or broad integration tests.

## Decision

Scraper adapters should expose package-private parsing seams that accept local HTML strings/documents. Tests should use fixtures stored under `src/test/resources/fixtures/tibia`.

Network fetching remains part of the adapter, but parsing behavior must be coverable without network access.

## Consequences

- Parser behavior can be locked before refactoring.
- Tests are faster and deterministic.
- New HTML edge cases can be captured as fixtures when bugs are found.
- Fetching, retry/backoff and parsing concerns become easier to separate over time.
''')

index = Path('docs/architecture/000-index.md')
if index.exists():
    text = index.read_text()
    lines = [
        '- [ADR-0004 - Separate read models from write-side services](adr/ADR-0004-separate-read-models-from-write-side-services.md)',
        '- [ADR-0005 - Extract highscore HTTP backoff coordinator](adr/ADR-0005-extract-highscore-http-backoff-coordinator.md)',
        '- [ADR-0006 - Use HTML fixture characterization tests for scrapers](adr/ADR-0006-use-html-fixture-characterization-tests-for-scrapers.md)',
    ]
    for line in lines:
        if line not in text:
            text += '\n' + line + '\n'
    index.write_text(text)
PY

cat <<MSG
Done. Architecture decision records for query/scraper boundaries applied.
Backup directory: $BACKUP_DIR
MSG
