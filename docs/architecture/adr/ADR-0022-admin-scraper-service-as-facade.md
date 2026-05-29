# ADR-0022: AdminScraperService as an admin scraper facade

## Status

Accepted

## Context

`AdminScraperService` exposed the public admin scraper use cases consumed by `AdminScraperController`, but it also assembled scraper status responses, mapped highscore HTTP backoff state, validated highscore plans and coordinated asynchronous manual runs.

That made the service harder to evolve because a single class mixed endpoint-facing facade responsibilities with status read-model composition and manual-run orchestration details.

## Decision

Keep `AdminScraperService` as the public application facade for admin scraper endpoints and extract focused collaborators:

- `AdminScraperStatusService` assembles scraper status, job status and highscore plan status views.
- `ManualScraperRunCoordinator` owns manual-run state, duplicate-run protection and background job execution.
- `HighscoreBackoffStatusMapper` maps the persisted highscore HTTP backoff state to the admin response view.

The response records remain nested in `AdminScraperService` to preserve the REST controller contract and JSON shape. Scrape job views are copied into admin-specific response records so the facade does not expose query-service implementation types.

An ArchUnit rule now prevents `AdminScraperService` from growing back into a configuration, persistence or query-aware implementation class.

## Consequences

- The REST controller continues to depend on one facade.
- Manual-run orchestration state is isolated in one collaborator.
- Status composition can evolve independently from manual run triggering.
- The facade is protected against direct dependencies on configuration classes, persistence repositories and query services.
