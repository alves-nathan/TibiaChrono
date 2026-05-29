# ADR-0007 - Keep ApiQueryService as a facade while splitting read models

## Status

Accepted.

## Context

`ApiQueryService` historically became the central read model entry point for REST and GraphQL APIs. It contains world, character, legacy highscore, scrape job and character online activity queries.

Splitting all callers at once would create a broad, high-risk change across controllers, GraphQL resolvers and application services. The codebase already has integration tests around these endpoints, but the safer path is to split the implementation incrementally while preserving the public facade methods.

## Decision

Keep `ApiQueryService` as a compatibility facade for existing callers and move cohesive query groups into dedicated read model services over time.

Initial extractions:

- `CharacterOnlineReadModelService` owns online points, online sessions and online world summaries.
- `ScrapeJobReadModelService` owns scrape job list queries.
- `ApiQueryService` delegates to these services while its public API remains stable.

Shared JDBC helpers should live in `JdbcReadModelSupport` when they are useful for extracted query services.

## Consequences

Benefits:

- Controllers and GraphQL resolvers do not need to change immediately.
- Large query classes can be reduced in smaller, safer steps.
- New read model services can be tested and evolved independently.
- The package boundary remains explicit through `@ReadModelService` and ArchUnit rules.

Trade-offs:

- `ApiQueryService` temporarily remains a facade with nested response records.
- Some DTO records remain nested under `ApiQueryService` until a future API DTO extraction is worth the churn.

## Follow-up

Future refactors should continue extracting cohesive query groups:

- world queries;
- character identity/profile queries;
- legacy highscore queries;
- API DTO records, if the facade becomes mostly empty.
