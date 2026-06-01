# ADR-0028: HighscoreService as a highscore run facade

## Status

Accepted

## Context

`HighscoreService` had already been reduced through previous refactors, but it still contained the complete run execution flow: scope selection, worker scheduling, per-scope page scraping, row mapping and result aggregation.

That made the public service responsible for both the API-facing highscore use case and the low-level execution mechanics of a highscore run.

## Decision

Keep `HighscoreService` as the public facade for highscore scraping operations and move the execution mechanics to focused collaborators:

- `HighscoreRunCoordinator` coordinates one highscore run, selects scopes, starts workers, aggregates results and handles successful-run backoff reset.
- `HighscoreScopeWorker` consumes scopes from a shared queue and reports worker-level counters.
- `HighscoreScopeScraper` performs one scope scrape, fetches page windows, resolves characters, persists stat rows and records scope state.

`HighscoreService` remains responsible for:

- checking global highscore enablement;
- checking plan enablement;
- checking global HTTP backoff;
- preventing concurrent highscore runs;
- exposing manual backoff state operations.

## Consequences

The highscore write flow has smaller units with clearer responsibilities and better testability.

The public facade no longer depends directly on lower-level run internals such as the page fetcher, scope planner, character resolver or stat storage service.

An ArchUnit rule prevents `HighscoreService` from regressing into directly depending on the low-level run collaborators again.
