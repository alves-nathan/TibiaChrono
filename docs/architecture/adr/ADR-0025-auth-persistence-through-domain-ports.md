# ADR-0025: Access authentication persistence through domain repository ports

## Status

Accepted.

## Context

The authentication flow previously depended directly on Spring Data repositories from
application services, security configuration and the REST authentication adapter.
That made authentication persistence a concrete infrastructure concern in places
that only need user, refresh-token and token-blacklist operations.

## Decision

Introduce focused domain ports for authentication persistence:

- `UserAccountRepositoryPort`
- `RefreshTokenRepositoryPort`
- `BlacklistedTokenRepositoryPort`

The Spring Data repositories remain in `infrastructure.persistence` and implement
those ports. `TokenService`, `DbUserDetailsService` and `AuthController` now depend
on the ports instead of the concrete repositories.

Architecture rules prevent authentication flows in application, config and REST
adapter packages from depending directly on those persistence repositories again.

## Consequences

- Authentication use cases are easier to test and reason about.
- Infrastructure remains the owner of Spring Data details.
- Future authentication refactors can happen behind stable domain ports.
