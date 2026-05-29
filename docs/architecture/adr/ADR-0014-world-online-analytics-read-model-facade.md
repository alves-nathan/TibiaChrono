# ADR-0014: Keep WorldOnlineAnalyticsService as a read-model facade

## Status

Accepted.

## Context

World online analytics evolved to expose several read-side use cases through a single public service:

- bucketed online history for one world;
- summary metrics for one world;
- comparison buckets for multiple worlds;
- rankings by peak, average, growth and latest online count.

The original implementation mixed endpoint-facing methods, validation, SQL construction and row mapping in `WorldOnlineAnalyticsService`. This made the service harder to review and increased the chance of future analytics additions turning the facade into another large JDBC class.

## Decision

`WorldOnlineAnalyticsService` remains the public facade used by web adapters and keeps the existing response records for API compatibility.

The query implementation is split into focused read models:

- `WorldOnlineBucketReadModelService` for bucket and compare queries;
- `WorldOnlineSummaryReadModelService` for summary queries;
- `WorldOnlineRankingReadModelService` for ranking queries;
- `WorldOnlineAnalyticsJdbcSupport` for shared validation, SQL range helpers, enum parsing and row mapping.

An ArchUnit rule prevents `WorldOnlineAnalyticsService` from depending directly on Spring JDBC or `java.sql` types.

## Consequences

- Controllers keep depending on the same facade and DTO records.
- Future world analytics queries should be added to a focused read model, not to the facade.
- Shared JDBC helpers remain package-private inside the query package.
- This follows the same incremental read-model facade pattern already used for API query, timeline and highscore query services.
