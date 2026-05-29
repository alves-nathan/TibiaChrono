# ADR-0008: Keep ApiQueryService as an incremental read-model facade

## Status

Accepted

## Context

`ApiQueryService` started as a broad query service for REST and GraphQL endpoints. As the API grew, it accumulated unrelated JDBC queries for worlds, character identity, legacy highscores, scrape jobs, and online activity. This made the class large and made it harder to reason about query ownership.

The public controllers already depend on `ApiQueryService`, so removing it in a single change would create unnecessary churn across the web adapters.

## Decision

Keep `ApiQueryService` as a compatibility facade while extracting cohesive read models behind it.

The facade should:

- preserve the existing public query methods used by controllers;
- delegate to focused read-model services;
- avoid direct JDBC, SQL strings, `ResultSet` mapping, and parameter binding;
- retain DTO records used by the web layer until a later DTO migration is justified.

Focused read models own their SQL and mapper details:

- `WorldReadModelService` for world lists/details;
- `CharacterIdentityReadModelService` for character lookup and name history;
- `CharacterOnlineReadModelService` for online history/session analytics;
- `ScrapeJobReadModelService` for scrape job reporting;
- `LegacyHighscoreReadModelService` for `character_statrecords` based endpoints.

## Consequences

Positive:

- controllers remain stable while query internals evolve;
- each read model becomes easier to test, review, and replace;
- `ApiQueryService` becomes a small compatibility layer instead of a SQL-heavy service;
- ArchUnit can protect the facade from re-accumulating direct JDBC usage.

Trade-offs:

- DTO records still live in `ApiQueryService` for compatibility;
- read models temporarily reference facade DTO records;
- a future change can move DTOs to package-level records once the facade is no longer needed.
