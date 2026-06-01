# ADR-0029: CharacterDetailsService as a character details facade

## Status
Accepted

## Context
`CharacterDetailsService` coordinated several responsibilities at once: selecting the next active character names to refresh, calling the remote character details port, handling retry/attempt statuses, reconciling official current/former names, and persisting profile fields.

That made the public service harder to reason about and harder to protect with architecture rules. The scheduler and admin manual run flow only need a stable public entry point for a character detail batch.

## Decision
Keep `CharacterDetailsService` as the public facade for character detail scraping and move the internal responsibilities to focused collaborators:

- `CharacterDetailsBatchSelector`: selects the next active character names and applies the configured batch size.
- `CharacterDetailsBatchProcessor`: fetches remote details for the selected names and builds the batch result counters.
- `CharacterDetailsPersistenceService`: handles transactional persistence of profile fields, attempt status and official name reconciliation.

An ArchUnit rule prevents `CharacterDetailsService` from depending directly on domain ports, application config or transaction infrastructure.

## Consequences
The public scheduler/admin API remains unchanged through `updateMissingDetailsBatch()`.

The character detail flow is now easier to evolve independently: selection, remote fetch/process orchestration and persistence/reconciliation can be tested and refactored separately.

Future changes should keep `CharacterDetailsService` as a thin facade and add behavior to the focused collaborators instead of growing the facade again.
