# ADR-0009 - Split highscore orchestration helpers out of HighscoreService

## Status

Accepted

## Context

`HighscoreService` is the main use case for the highscore scraper. It coordinates worlds, categories, vocations,
parallel workers, page fetching, persistence and character identity reconciliation.

As the scraper gained production-safety features, the service also accumulated low-level operational concerns:

- global HTTP 403/429 backoff;
- cooldown persistence and manual reset;
- request start pacing;
- jitter between requests;
- rolling request-budget enforcement.

Keeping all those concerns inline makes `HighscoreService` harder to review and increases the chance that future scraper
changes accidentally bypass safety controls.

## Decision

Keep `HighscoreService` as the highscore scrape orchestrator, but move cross-cutting operational controls into dedicated
application services:

- `HighscoreHttpBackoffCoordinator` owns global HTTP backoff state, cooldown waiting and manual reset behavior;
- `HighscoreRequestThrottle` owns request pacing, jitter and rolling-window request-budget enforcement.

`HighscoreService` should invoke these collaborators before fetching pages, but it should not reimplement their state
machines inline.

## Consequences

Positive:

- highscore safety controls become easier to test and reason about;
- `HighscoreService` gets smaller and more focused on orchestration;
- future changes to request budget/backoff are localized;
- logs identify whether the scraper is waiting because of backoff or request-budget throttling.

Trade-offs:

- the highscore flow has more collaborating services;
- these services are JVM-local, so multi-instance deployments would still need a distributed lock/rate-budget strategy.

## Guardrails

- Keep cooldown state persistence in `HighscoreScrapeStateRepository` through `HighscoreHttpBackoffCoordinator`.
- Keep request-budget defaults conservative enough for safe production operation.
- Avoid adding new `AtomicLong`, request-budget queues or HTTP backoff locks directly to `HighscoreService`.
