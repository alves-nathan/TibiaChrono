# ADR-0021: Access guild persistence through domain repository ports

## Status

Accepted

## Context

After splitting guild scraping and guild query responsibilities, several application services and read models still depended directly on `SpringGuildRepository`, an infrastructure persistence class. This kept the application layer coupled to the persistence adapter even when the code already had clearer service boundaries.

The guild persistence adapter has multiple responsibilities: guild catalog lookup and upsert, snapshots, memberships, membership events and invites. Exposing the concrete adapter to the application layer made those responsibilities harder to evolve independently.

## Decision

Introduce focused domain repository ports for guild persistence access:

- `GuildCatalogRepositoryPort`
- `GuildSnapshotRepositoryPort`
- `GuildMembershipRepositoryPort`
- `GuildMembershipEventRepositoryPort`
- `GuildInviteRepositoryPort`

`SpringGuildRepository` remains the infrastructure implementation, but application services and read models now depend on the domain ports instead of the concrete persistence class.

An ArchUnit rule prevents the application layer from depending directly on `SpringGuildRepository` again.

## Consequences

- Guild persistence access follows the same hexagonal direction used by the rest of the application.
- Application services can express narrower dependencies.
- The infrastructure repository can still aggregate the underlying Spring Data repositories internally.
- There are more port interfaces, but each represents a focused persistence capability.

## Follow-up

Apply the same pattern to highscore scraper state/stat writers and authentication persistence where direct infrastructure dependencies still exist in application services.
