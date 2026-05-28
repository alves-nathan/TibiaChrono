#!/usr/bin/env bash
set -Eeuo pipefail

BACKUP_SUFFIX=".bak-database-legacy-docs-$(date +%Y%m%d%H%M%S)"

if [[ ! -f "pom.xml" || ! -d "src/main/java" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

if [[ -d "docs/architecture" ]]; then
  cp -R "docs/architecture" "docs/architecture$BACKUP_SUFFIX"
fi

python3 - <<'PY'
from pathlib import Path

base = Path('docs/architecture')
adr = base / 'adr'
adr.mkdir(parents=True, exist_ok=True)

(base / '000-index.md').write_text('''# TibiaChrono Architecture Index

This folder records architecture decisions, legacy boundaries and quality rules for TibiaChrono.

## Core documents

- [Current architecture](001-current-architecture.md)
- [Package boundaries](002-package-boundaries.md)
- [Scraper strategy](003-scraper-strategy.md)
- [Highscore storage model](004-highscore-storage-model.md)
- [Legacy management](005-legacy-management.md)
- [Database evolution and legacy areas](database-evolution.md)

## ADRs

- [ADR-0001 - Use Flyway validate instead of Hibernate update](adr/ADR-0001-use-flyway-validate.md)
- [ADR-0002 - Allow JDBC read models for analytics](adr/ADR-0002-jdbc-read-models-for-analytics.md)
- [ADR-0003 - Keep compact highscore storage as the main model](adr/ADR-0003-highscore-compact-storage.md)
''')

(base / '001-current-architecture.md').write_text('''# Current Architecture

TibiaChrono is a Spring Boot application that scrapes public Tibia data, stores normalized history and exposes query APIs.

## Current structure

```text
com.nathan.tibiastats
├── application      # use cases, schedulers, orchestration and query services
├── config           # Spring/security/configuration wiring
├── domain           # entities, domain models and ports
├── infrastructure   # web adapters, scraper adapters and persistence adapters
└── util             # technical helpers
```

## Architectural direction

The intended direction is hexagonal architecture:

- `domain` owns core concepts and ports.
- `application` orchestrates use cases.
- `infrastructure` implements adapters for HTTP, scraping and persistence.
- `config` wires Spring concerns.

## Known deviations

Some application services still depend directly on infrastructure repositories or JDBC details. This is currently accepted as legacy and should be reduced incrementally through small patches.

## Recommended rule

New behavior should prefer ports in `domain.port` and adapters in `infrastructure`, unless the class is an explicit read model/query service.
''')

(base / '002-package-boundaries.md').write_text('''# Package Boundaries

## Domain

Allowed:

- Domain models
- Domain value objects
- Domain ports/interfaces
- Simple invariants

Avoid:

- Spring services/controllers/configuration
- Infrastructure repositories
- HTTP clients
- SQL/JDBC templates

## Application

Allowed:

- Use case orchestration
- Schedulers
- Transactional application workflows
- Calls to domain ports
- Read/query services while legacy query model is being extracted

Avoid:

- HTML parser details
- Controller-specific response shaping
- Direct repository usage in new code

## Infrastructure

Allowed:

- REST and GraphQL adapters
- Jsoup scraper adapters
- Spring Data repositories
- JDBC query adapters
- Persistence-specific mapping

## Config

Allowed:

- Spring configuration
- Security configuration
- Properties binding

Avoid:

- Business rules
- Scraper orchestration logic
''')

(base / '003-scraper-strategy.md').write_text('''# Scraper Strategy

Scrapers are the riskiest integration point because Tibia.com HTML and anti-bot behavior can change outside this project.

## Principles

1. Treat Tibia.com as an unreliable external dependency.
2. Prefer parser tests using local HTML fixtures before refactoring scraper code.
3. Keep global backoff/cooldown behavior outside parser code.
4. Avoid storing raw transient UI artifacts as canonical domain state.
5. Log enough context to diagnose parser drift without logging excessive HTML.

## Recommended test structure

```text
src/test/resources/fixtures/tibia/
├── worlds-overview.html
├── world-detail.html
├── highscores-experience.html
├── character-detail.html
└── guild-detail.html
```

## Backoff policy

HTTP 403/429 responses should trigger global cooldown for highscore scraping. Repeated failures should progressively increase cooldown up to a configured maximum.
''')

(base / '004-highscore-storage-model.md').write_text('''# Highscore Storage Model

The compact highscore model is the preferred model for new highscore features.

## Current intent

- Store daily highscore snapshots efficiently.
- Keep current records queryable without scanning excessive historical rows.
- Preserve enough history for timeline and ranking analysis.

## Legacy warning

Older character stat tables may still exist for compatibility or migration reasons. New features should avoid writing to legacy tables unless explicitly documented.

## Query guidance

JDBC read models are acceptable for high-volume analytical endpoints when:

- The SQL is isolated in query-focused classes.
- The endpoint contract is covered by integration tests.
- The query has explicit indexes or a documented migration plan.
''')

(base / '005-legacy-management.md').write_text('''# Legacy Management

TibiaChrono is evolving quickly through patch scripts. Legacy needs to be managed deliberately so fixes do not accumulate as hidden architecture debt.

## Patch policy

- Never edit already-applied Flyway migrations to fix production-like databases.
- Add new migrations for schema corrections.
- Keep patch scripts idempotent when possible.
- Every patch should print clear next steps.
- Prefer small, testable refactors over broad rewrites.

## Backup policy

Patch scripts may create timestamped `.bak-*` files for safety. These files are local artifacts and should not be committed.

## Deprecation policy

When a table, endpoint or service path becomes legacy:

1. Document why it is legacy.
2. Document the replacement.
3. Add tests around the replacement before removing the legacy path.
4. Remove legacy code only after consumers are migrated.
''')

(base / 'database-evolution.md').write_text('''# Database Evolution and Legacy Areas

## Current position

Flyway is the source of truth for schema evolution. Hibernate should validate the schema rather than mutate it automatically.

## Known legacy or transitional areas

### `character_statrecords`

This table appears to represent the older stat history model. New highscore features should prefer the compact highscore tables unless a migration or compatibility requirement says otherwise.

### Highscore compact tables

The compact highscore model should be treated as the primary model for highscore history, current records and periods.

### Guild history tables

Guild membership tables are newer and should be protected with integration tests before changing parser or identity rules.

## Migration rules

- Use additive migrations for new columns/tables/indexes.
- Use corrective migrations for type fixes and constraints.
- Avoid destructive migrations until the application is stable and data has been backed up.
- Keep manual cleanup SQL under `src/main/resources/db/manual` and document when it should be used.

## Future cleanup candidates

- Review duplicated highscore category migrations.
- Review whether early V1 integer foreign keys are fully corrected by later migrations.
- Review legacy stat tables before any production baseline reset.
''')

(adr / 'ADR-0001-use-flyway-validate.md').write_text('''# ADR-0001 - Use Flyway validate instead of Hibernate update

## Status

Accepted

## Context

The application uses Flyway migrations and has already accumulated corrective migrations. Allowing Hibernate `ddl-auto: update` can silently mutate the schema and hide migration problems.

## Decision

Use `spring.jpa.hibernate.ddl-auto=validate` as the safe default. Development and test profiles should also validate against Flyway-managed schemas.

## Consequences

- Schema drift fails fast during startup/tests.
- New schema changes require explicit Flyway migrations.
- Local development may require database reset when migrations change significantly.
''')

(adr / 'ADR-0002-jdbc-read-models-for-analytics.md').write_text('''# ADR-0002 - Allow JDBC read models for analytics

## Status

Accepted with constraints

## Context

Some analytical endpoints need SQL that is more efficient and explicit than ORM traversal.

## Decision

JDBC-based read models are allowed for analytics and timeline queries when isolated from core write workflows.

## Constraints

- Keep SQL in query-focused classes or adapters.
- Cover endpoint behavior with integration tests.
- Prefer named parameters and explicit timestamp casts when PostgreSQL null inference is ambiguous.
- Do not mix scraping orchestration and analytical SQL in the same class.

## Consequences

- Query performance can be optimized directly.
- The architecture must clearly separate read models from domain use cases.
''')

(adr / 'ADR-0003-highscore-compact-storage.md').write_text('''# ADR-0003 - Keep compact highscore storage as the main model

## Status

Accepted

## Context

Highscore scraping can generate a large amount of historical data. The application needs efficient storage for daily snapshots, current records and periods.

## Decision

Use the compact highscore storage model as the primary highscore model for new features.

## Consequences

- New highscore APIs should query compact tables first.
- Legacy stat tables should be treated as compatibility or migration concerns.
- Integration tests should protect highscore API contracts before storage refactors.
''')
PY

cat <<'MSG'

Done.
Architecture and database legacy documentation added under docs/architecture.

Next steps:
  Review docs/architecture/000-index.md
  Commit docs together with the quality gates so future patches have explicit architecture constraints.
MSG
