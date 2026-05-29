# ADR-0011 - Keep HighscoreService as orchestration only

## Status

Accepted

## Context

The highscore scraper is one of the highest-risk areas in TibiaChrono because it coordinates external HTTP scraping, rolling request budgets, retry/backoff behavior, scope scheduling, character identity resolution and compact highscore persistence.

Earlier refactors extracted the HTTP backoff coordinator, request throttle, character resolver and retry policy. This ADR completes the next step in that direction by keeping `HighscoreService` focused on orchestration and moving scope selection plus page fetching details into dedicated collaborators.

## Decision

`HighscoreService` should remain the high-level orchestrator for a highscore run. It may coordinate workers, scope execution and final run aggregation, but it should not directly depend on external scraper/domain ports such as `HighscorePort` or `WorldRepositoryPort`.

The following collaborators own the detailed responsibilities:

- `HighscoreScopePlanner`: world/category/vocation scope selection and registration in scrape state.
- `HighscorePageFetcher`: one highscore page fetch attempt loop, including HTTP cooldown, request throttle, request budget, retry handling and rate-limit activation.
- `HighscoreCharacterResolver`: normalization and identity resolution for highscore character rows.
- `HighscoreFetchRetryPolicy`: transient failure classification, retry delay calculation and retry heartbeat logging.
- `HighscoreHttpBackoffCoordinator`: persisted/global HTTP 403/429 cooldown state.
- `HighscoreRequestThrottle`: pacing, jitter and rolling request budget.

An ArchUnit rule protects the boundary by preventing `HighscoreService` from depending directly on `..domain.port..`.

## Consequences

- `HighscorePageFetcher` converts port-level highscore rows into application-level page rows before returning data to the orchestrator. This keeps `HighscoreService` independent from `domain.port` DTOs.

Positive:

- The highscore orchestration flow becomes easier to reason about.
- External HTTP fetch mechanics can evolve without expanding `HighscoreService`.
- Scope planning and page fetching become separately testable seams.
- Future changes can continue extracting scope execution/window persistence without touching request retry logic.

Trade-offs:

- More small classes exist in `application.service`.
- The orchestration flow now requires following collaborators by name, so naming and ADR documentation are important.

## Follow-up

Potential future extractions:

- `HighscoreScopeExecutor` for the scope/page-window loop.
- `HighscoreRunCoordinator` for worker lifecycle and aggregation.
- Dedicated unit tests for `HighscoreScopePlanner`, `HighscorePageFetcher` and `HighscoreRequestThrottle` using fake ports/repositories.
