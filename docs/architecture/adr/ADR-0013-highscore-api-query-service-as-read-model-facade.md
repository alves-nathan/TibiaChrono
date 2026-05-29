# ADR-0013: Keep HighscoreApiQueryService as a read-model facade

## Status

Accepted

## Context

`HighscoreApiQueryService` originally combined the public query contract used by REST controllers with all SQL for:

- experience daily snapshots;
- experience ranks;
- experience gains;
- character experience history;
- current non-experience highscores;
- historical non-experience highscore periods.

This made the class a large JDBC read model and increased the chance that future endpoint changes would modify unrelated SQL areas.

## Decision

`HighscoreApiQueryService` remains the public facade for highscore query endpoints, but it no longer owns SQL directly.

The SQL is split into focused read models:

- `HighscoreExperienceReadModelService` for experience-oriented views;
- `HighscoreRecordReadModelService` for current and historical non-experience highscore records;
- `HighscoreApiJdbcSupport` for shared mapping/category helpers.

The facade keeps the existing nested response records so controller contracts remain unchanged.

## Consequences

- REST controllers continue depending on one stable facade.
- SQL changes are isolated by query family.
- ArchUnit protects the facade from using JDBC or `java.sql` directly again.
- The nested response records stay in the facade for compatibility, even though mappers live in focused read models.
