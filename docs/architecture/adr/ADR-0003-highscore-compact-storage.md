# ADR-0003 - Keep compact highscore storage as the main model

## Status

Accepted

## Context

Highscore scraping can generate a large amount of historical data. The application needs efficient storage for daily snapshots, current records and periods.

## Decision

Use the compact highscore storage model as the primary highscore model for new features.

## Consequences

- New highscore APIs should query compact tables first.
- Legacy stat tables should be treated as compatibility or migration concerns.
- Integration tests should protect highscore API contracts before storage refactors.
