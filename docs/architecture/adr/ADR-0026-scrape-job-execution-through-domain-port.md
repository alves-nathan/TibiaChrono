# ADR-0026: Access scrape job execution persistence through a domain repository port

## Status

Accepted

## Context

`ScrapeJobService` is an application service responsible for registering scraper job lifecycle events. Before this change, it depended directly on `ScrapeJobExecutionRepository`, a Spring Data repository located in `infrastructure.persistence`.

That direct dependency weakened the application/infrastructure boundary and made scraper orchestration less aligned with the repository-port pattern already used by worlds, characters, guilds, highscore persistence and authentication.

## Decision

Introduce `ScrapeJobExecutionRepositoryPort` in `domain.port` and make `ScrapeJobExecutionRepository` implement it as the infrastructure adapter.

`ScrapeJobService` now depends on the domain port instead of the concrete Spring Data repository. An ArchUnit rule prevents the application layer from depending directly on `ScrapeJobExecutionRepository` again.

## Consequences

- The scraper job lifecycle use case no longer imports infrastructure persistence directly.
- The concrete persistence implementation remains in `infrastructure.persistence`.
- Future job lifecycle storage changes can be made behind the port.
- The boundary rule keeps the incremental architecture refactor from regressing.
