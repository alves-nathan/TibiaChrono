# ADR-0004 - Separate read models from write-side application services

## Status

Accepted

## Context

TibiaChrono has analytical API endpoints that are naturally query-heavy. Some of these use SQL/JDBC directly because they need efficient joins, projections and aggregations over historical data.

Keeping these classes inside the general `application.service` package makes it harder to distinguish write-side orchestration from read-side query models. It also makes architectural rules too permissive because JDBC dependencies may look acceptable everywhere in the application layer.

## Decision

Read/query services must live under `com.nathan.tibiastats.application.query` and be marked with `@ReadModelComponent`.

Write-side application services remain under `com.nathan.tibiastats.application.service` and should not depend directly on Spring JDBC.

## Consequences

- JDBC remains allowed for explicit read models.
- Write-side use cases stay easier to review and refactor toward ports/adapters.
- ArchUnit can enforce the boundary and prevent accidental JDBC growth in application services.
- Existing large query services can be decomposed incrementally without changing endpoint behavior.
