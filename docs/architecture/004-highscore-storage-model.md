# Highscore Storage Model

The compact highscore model is the preferred model for new highscore features.

## Current intent

- Store daily highscore snapshots efficiently.
- Keep current records queryable without scanning excessive historical rows.
- Preserve enough history for timeline and ranking analysis.

## Legacy warning

Older character stat tables may still exist for compatibility or migration reasons. New features should avoid writing to legacy tables unless explicitly documented.

## Query guidance

JDBC read models are acceptable for high-volume analytical endpoints when:

- The SQL is isolated in query-focused classes.
- The endpoint contract is covered by integration tests.
- The query has explicit indexes or a documented migration plan.
