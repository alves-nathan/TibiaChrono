# ADR-0023: Access highscore persistence through domain repository ports

## Status

Accepted.

## Context

The highscore scraping flow had already been split into orchestration, planning, fetching, retry/backoff and character resolution collaborators. However, some application services still depended directly on infrastructure persistence classes:

- `HighscoreScrapeStateRepository`
- `HighscoreStatRecordWriter`

That made the application layer aware of JDBC-backed infrastructure details and leaked infrastructure nested record types into orchestration code.

## Decision

Highscore scrape state and stat record storage must be accessed through domain ports:

- `HighscoreScrapeStateRepositoryPort`
- `HighscoreStatRecordRepositoryPort`

The shared records used by the application and persistence implementation now live in the domain model:

- `HighscoreScope`
- `HighscoreHttpBackoffState`
- `HighscoreStatRow`

The infrastructure classes remain the Spring/JDBC implementations of those ports.

`HighscoreService` remains an orchestration facade. It delegates state updates and stat storage to application collaborators instead of depending directly on ports or infrastructure classes.

## Consequences

- Highscore application code no longer imports highscore persistence implementation classes.
- Infrastructure persistence details stay behind domain contracts.
- `HighscoreService` continues to satisfy the previous orchestration boundary rule that prevents direct domain-port dependencies.
- The architecture boundary is enforced by ArchUnit rules that block application dependencies on the highscore persistence implementations.
