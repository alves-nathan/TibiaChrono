# ADR-0005 - Extract highscore HTTP backoff coordination

## Status

Accepted

## Context

The highscore scraper must handle remote rate limiting and blocking responses such as HTTP 403/429. The original orchestration mixed scraping workflow, scope processing, request pacing and global cooldown state in one large service.

That made it harder to reason about the cooldown policy and increased the risk of future changes accidentally bypassing the global protection.

## Decision

Global HTTP backoff/cooldown state for highscore scraping is owned by `HighscoreHttpBackoffCoordinator`.

The highscore orchestration service asks the coordinator whether a run should continue and notifies it about success or rate-limit failures.

## Consequences

- Cooldown logic becomes isolated and testable.
- Future scraper plans share one global protection mechanism.
- The highscore service can be decomposed further without duplicating rate-limit state.
