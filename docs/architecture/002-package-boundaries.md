# Package Boundaries

## Domain

Allowed:

- Domain models
- Domain value objects
- Domain ports/interfaces
- Simple invariants

Avoid:

- Spring services/controllers/configuration
- Infrastructure repositories
- HTTP clients
- SQL/JDBC templates

## Application

Allowed:

- Use case orchestration
- Schedulers
- Transactional application workflows
- Calls to domain ports
- Read/query services while legacy query model is being extracted

Avoid:

- HTML parser details
- Controller-specific response shaping
- Direct repository usage in new code

## Infrastructure

Allowed:

- REST and GraphQL adapters
- Jsoup scraper adapters
- Spring Data repositories
- JDBC query adapters
- Persistence-specific mapping

## Config

Allowed:

- Spring configuration
- Security configuration
- Properties binding

Avoid:

- Business rules
- Scraper orchestration logic
