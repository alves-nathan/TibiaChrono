# ADR-0018: GuildQueryService as a read-model facade

## Status

Accepted

## Context

`GuildQueryService` is the public read-side entry point used by REST endpoints for guild catalog, membership, event and character guild-history queries.
It previously depended directly on the guild persistence adapter and the character repository port while also mapping response records.

That made the facade responsible for several unrelated read-model concerns:

- listing and loading guild catalog records;
- loading guild membership snapshots and history;
- resolving character names for event filters;
- querying guild membership events;
- mapping domain objects to endpoint response records.

## Decision

Keep `GuildQueryService` as the public read-model facade and split data access into focused read-model services:

- `GuildCatalogReadModelService` loads guild catalog data and resolves guild ids.
- `GuildMembershipReadModelService` loads membership snapshots/history and resolves character ids for guild queries.
- `GuildMembershipEventReadModelService` loads membership events.

`GuildQueryService` keeps the public response records and delegates all repository-backed lookup work to the focused collaborators.
An ArchUnit rule prevents the facade from depending directly on repository ports or persistence adapters.

## Consequences

- Guild REST endpoints keep the same JSON contract.
- Data access for guild queries is isolated from response mapping.
- Future SQL/JPA changes for guild read models can be made in focused classes.
- Repository access is still allowed in the focused read-model services until repository ports/read models are further refined.
