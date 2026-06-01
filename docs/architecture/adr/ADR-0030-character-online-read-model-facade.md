# ADR-0030: CharacterOnlineReadModelService as a read-model facade

## Status

Accepted.

## Context

`CharacterOnlineReadModelService` had grown into a single read-model class responsible for three different query shapes:

- raw online history points;
- derived online sessions;
- world-level online summaries.

Each query has independent SQL, parameter handling and mapping concerns. Keeping all of them in one class made the facade harder to evolve and increased the risk of future coupling between unrelated read models.

## Decision

Keep `CharacterOnlineReadModelService` as the public read-model facade used by `ApiQueryService`, and move the concrete JDBC queries into focused collaborators:

- `CharacterOnlineHistoryReadModelService` for raw online samples;
- `CharacterOnlineSessionReadModelService` for session grouping;
- `CharacterOnlineWorldSummaryReadModelService` for aggregated world summaries;
- `CharacterOnlineJdbcSupport` for shared filters and mappers.

Add an ArchUnit rule preventing the facade from depending directly on JDBC and SQL implementation details.

## Consequences

The public API remains stable, while each read model can evolve independently. The facade stays small and delegates to focused services. SQL complexity remains in the query package, but no longer accumulates in a single class.
