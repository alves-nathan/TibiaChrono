# ADR-0017: CharacterNamingService as an identity facade

## Status

Accepted

## Context

Character identity is shared by world scraping, guild scraping, character details scraping and highscore scraping.
The public application entry point for this behavior is `CharacterNamingService`, but the service previously mixed several responsibilities:

- parsing and normalizing former-name lists;
- resolving plain observed names using the former-name safe window;
- reconciling official Tibia.com current/former names;
- merging duplicated character identities and reassigning references;
- updating active and inactive `CharacterName` records.

That made future rename-related fixes riskier, especially for cases where a character can temporarily have more than one active name while the official profile has not yet been reconciled.

## Decision

Keep `CharacterNamingService` as the public facade used by application flows, and split the implementation into focused services:

- `CharacterNameParser` parses and normalizes current/former names.
- `CharacterObservedNameResolver` resolves plain observed names and owns the safe former-name lookup window.
- `CharacterIdentityReconciliationService` applies official current/former-name reconciliation rules.
- `CharacterIdentityMergeService` merges duplicated character identities and reassigns references.

`CharacterNamingService` must not depend directly on repository ports or persistence adapters. It should delegate identity work to the focused collaborators.

## Consequences

- Rename/identity logic is easier to reason about and test incrementally.
- The public service API remains stable for existing callers.
- Repository access is still allowed in the focused identity services until repository ports are further refined.
- A new ArchUnit rule prevents `CharacterNamingService` from regressing into a repository-backed god service.
