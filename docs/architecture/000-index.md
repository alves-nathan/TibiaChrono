# TibiaChrono Architecture Index

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

- [ADR-0004 - Separate read models from write-side services](adr/ADR-0004-separate-read-models-from-write-side-services.md)

- [ADR-0005 - Extract highscore HTTP backoff coordinator](adr/ADR-0005-extract-highscore-http-backoff-coordinator.md)

- [ADR-0006 - Use HTML fixture characterization tests for scrapers](adr/ADR-0006-use-html-fixture-characterization-tests-for-scrapers.md)

- [ADR-0007 - Keep ApiQueryService as a facade while splitting read models](adr/ADR-0007-keep-api-query-service-as-facade-while-splitting-read-models.md)
- [ADR-0008: Keep ApiQueryService as an incremental read-model facade](adr/ADR-0008-api-query-service-as-incremental-read-model-facade.md)

## Architecture Decision Records

- [ADR-0009 - Split highscore orchestration helpers out of HighscoreService](adr/ADR-0009-highscore-orchestration-service-split.md)
- [ADR-0010 - Split highscore character resolution and retry policy from orchestration](adr/ADR-0010-highscore-character-resolution-and-retry-policy.md)
