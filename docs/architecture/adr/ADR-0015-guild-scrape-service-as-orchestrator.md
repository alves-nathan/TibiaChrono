# ADR-0015: Keep GuildScrapeService as an orchestration facade

## Status

Accepted.

## Context

`GuildScrapeService` originally coordinated the scheduled guild scraping flow and also contained several lower-level responsibilities:

- target selection for worlds and guilds to refresh;
- guild catalog upsert and world association;
- guild detail snapshot persistence;
- membership open/update/close/transfer reconciliation;
- invite reconciliation;
- character snapshot enrichment while processing members.

That made the service harder to review and increased the risk of future changes mixing orchestration, repository access and domain reconciliation rules in the same class.

## Decision

Keep `GuildScrapeService` as the public application facade for guild scraping operations and split implementation details into focused services:

- `GuildScrapeTargetPlanner` selects world and guild names to scrape;
- `GuildCatalogService` handles guild list upsert and catalog metadata;
- `GuildDetailScrapeService` handles guild detail snapshots, memberships, transfers and invites.

An ArchUnit rule prevents `GuildScrapeService` from depending directly on `domain.port` and `infrastructure.persistence` packages. Lower-level collaborators may still use repositories and ports while the facade remains orchestration-only.

## Consequences

- Controllers/schedulers/tests can keep using `GuildScrapeService`.
- Membership reconciliation remains behaviorally equivalent but is isolated from orchestration.
- Future work can refine rename handling and guild membership rules without growing the facade again.
- The next architecture step can introduce domain/application ports for guild persistence if needed, without changing the public service contract.
