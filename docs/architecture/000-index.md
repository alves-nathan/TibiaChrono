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
- [ADR-0011 - Keep HighscoreService as orchestration only](adr/ADR-0011-highscore-service-as-orchestrator.md)
- [ADR-0012: CharacterTimelineService as a read-model facade](adr/ADR-0012-character-timeline-read-model-facade.md)
- [ADR-0013: HighscoreApiQueryService as a read-model facade](adr/ADR-0013-highscore-api-query-service-as-read-model-facade.md)
- [ADR-0014: WorldOnlineAnalyticsService as a read-model facade](adr/ADR-0014-world-online-analytics-read-model-facade.md)
- [ADR-0015: GuildScrapeService as an orchestration facade](adr/ADR-0015-guild-scrape-service-as-orchestrator.md)
- [ADR-0016: ScrapeService as world scrape orchestration facade](adr/ADR-0016-scrape-service-as-world-scrape-orchestrator.md)
- [ADR-0017: CharacterNamingService as an identity facade](adr/ADR-0017-character-naming-service-as-identity-facade.md)
- [ADR-0018: GuildQueryService as a read-model facade](adr/ADR-0018-guild-query-service-as-read-model-facade.md)
- [ADR-0019: Split JsoupGuildAdapter into transport and parser collaborators](adr/ADR-0019-jsoup-guild-adapter-parser-split.md)
- [ADR-0020: Split Jsoup world and character adapters into transport and parser collaborators](adr/ADR-0020-jsoup-world-character-adapter-parser-split.md)
- [ADR-0021: Access guild persistence through domain repository ports](adr/ADR-0021-guild-persistence-through-domain-ports.md)
- [ADR-0022: AdminScraperService as an admin scraper facade](adr/ADR-0022-admin-scraper-service-as-facade.md)
- [ADR-0023: Access highscore persistence through domain repository ports](adr/ADR-0023-highscore-persistence-through-domain-ports.md)
- [ADR-0024: Split JsoupHighscoreAdapter into transport and parser collaborators](adr/ADR-0024-jsoup-highscore-adapter-parser-split.md)
