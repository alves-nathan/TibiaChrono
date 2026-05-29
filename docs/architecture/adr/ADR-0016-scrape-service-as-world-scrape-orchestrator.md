# ADR-0016: Keep ScrapeService as the world scrape orchestration facade

## Status

Accepted

## Context

`ScrapeService` is the public application service used by schedulers and admin endpoints to refresh world online data.
Over time it accumulated four responsibilities in the same class:

- fetching the world overview and each world page through `ScrapePort`;
- preparing application-level scrape snapshots;
- persisting world metadata and scrape snapshots;
- resolving/updating online character identity, level and vocation.

This made the world scraper harder to evolve and made the facade depend directly on domain ports.

## Decision

Keep `ScrapeService` as a thin orchestrator and split the workflow into focused application services:

- `WorldScrapeClient` fetches overview/pages through the scrape port and maps scraper DTOs to application snapshots.
- `WorldScrapePersistenceService` persists world metadata, scrape records and scrape players inside the transaction.
- `OnlineCharacterSnapshotService` resolves online character identity and applies level/vocation updates observed in world pages.
- `WorldScrapeSnapshot` carries application-level records between the scraper client, facade and persistence service.

An ArchUnit rule prevents `ScrapeService` from depending directly on domain ports or infrastructure repositories.

## Consequences

The world scrape workflow keeps the same public contract while reducing the responsibilities of the facade.
Future changes to identity reconciliation, scrape persistence or scraper DTO mapping can be made in focused classes without growing `ScrapeService` again.
