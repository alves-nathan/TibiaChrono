# ADR-0006 - Use HTML fixture characterization tests for scrapers

## Status

Accepted

## Context

Tibia.com HTML is an external dependency and can change without notice. Parser refactors are risky when they depend only on live network behavior or broad integration tests.

## Decision

Scraper adapters should expose package-private parsing seams that accept local HTML strings/documents. Tests should use fixtures stored under `src/test/resources/fixtures/tibia`.

Network fetching remains part of the adapter, but parsing behavior must be coverable without network access.

## Consequences

- Parser behavior can be locked before refactoring.
- Tests are faster and deterministic.
- New HTML edge cases can be captured as fixtures when bugs are found.
- Fetching, retry/backoff and parsing concerns become easier to separate over time.
