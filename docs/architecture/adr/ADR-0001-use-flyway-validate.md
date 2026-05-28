# ADR-0001 - Use Flyway validate instead of Hibernate update

## Status

Accepted

## Context

The application uses Flyway migrations and has already accumulated corrective migrations. Allowing Hibernate `ddl-auto: update` can silently mutate the schema and hide migration problems.

## Decision

Use `spring.jpa.hibernate.ddl-auto=validate` as the safe default. Development and test profiles should also validate against Flyway-managed schemas.

## Consequences

- Schema drift fails fast during startup/tests.
- New schema changes require explicit Flyway migrations.
- Local development may require database reset when migrations change significantly.
