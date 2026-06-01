# ADR-0027: Enforce a global persistence boundary for core application layers

## Status

Accepted

## Context

The application has gradually moved persistence access behind domain ports. Guild,
highscore, authentication and scrape job execution flows no longer need to depend
on concrete Spring Data or JDBC persistence adapters directly.

Keeping only per-repository ArchUnit rules makes the boundary explicit, but it
also means every new repository can accidentally be consumed by application,
configuration or REST code before a specific rule is added.

## Decision

Add a global ArchUnit rule preventing these layers from depending directly on
`infrastructure.persistence`:

- `application`
- `config`
- `infrastructure.adapter.web.rest`

Persistence adapters remain in infrastructure and may implement domain ports.
Application services, configuration and REST controllers must depend on domain
ports, domain models or application services instead.

## Consequences

- New persistence adapters cannot leak into application, config or REST flows.
- Future persistence access must introduce a domain port first.
- Specific historical rules remain useful documentation for important boundaries,
  while the global rule prevents regressions for new repositories.
- Some infrastructure components outside REST may still depend on persistence when
  they are adapter implementations or framework integration code.
