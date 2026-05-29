# ADR-0010 - Split highscore character resolution and retry policy from orchestration

## Status

Accepted.

## Context

`HighscoreService` orchestrates a highscore scrape run. Over time it accumulated secondary responsibilities that are important but not the orchestration itself:

- normalizing highscore character names, including `(traded)` suffix cleanup;
- resolving or creating character identities before persisting stat rows;
- serializing concurrent identity creation by normalized name;
- classifying transient HTTP failures;
- computing retry delays and jitter;
- logging long retry sleeps with a heartbeat.

Keeping all these concerns inline made the service harder to reason about and increased the chance that future highscore changes would accidentally alter identity or retry behavior.

## Decision

Keep `HighscoreService` as the highscore run orchestrator and extract supporting policies into dedicated application services:

- `HighscoreCharacterResolver` owns highscore character name normalization and identity resolution.
- `HighscoreFetchRetryPolicy` owns transient-failure classification, retry-delay calculation and retry sleep heartbeat logging.

`HighscoreService` continues to decide when a scope/page is processed, but delegates these focused concerns to collaborators.

## Consequences

- Highscore character identity behavior can now be tested/refined independently from the scrape orchestration loop.
- Retry/backoff rules are isolated from persistence and scope execution logic.
- `HighscoreService` remains a facade/orchestrator for the highscore scrape workflow, but no longer owns every low-level policy.
- Future extraction targets are clearer: page fetching/window processing, scope worker coordination and result aggregation.
