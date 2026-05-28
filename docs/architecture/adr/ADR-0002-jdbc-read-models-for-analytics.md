# ADR-0002 - Allow JDBC read models for analytics

## Status

Accepted with constraints

## Context

Some analytical endpoints need SQL that is more efficient and explicit than ORM traversal.

## Decision

JDBC-based read models are allowed for analytics and timeline queries when isolated from core write workflows.

## Constraints

- Keep SQL in query-focused classes or adapters.
- Cover endpoint behavior with integration tests.
- Prefer named parameters and explicit timestamp casts when PostgreSQL null inference is ambiguous.
- Do not mix scraping orchestration and analytical SQL in the same class.

## Consequences

- Query performance can be optimized directly.
- The architecture must clearly separate read models from domain use cases.
