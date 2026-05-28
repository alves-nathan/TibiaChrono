# Current Architecture

TibiaChrono is a Spring Boot application that scrapes public Tibia data, stores normalized history and exposes query APIs.

## Current structure

```text
com.nathan.tibiastats
├── application      # use cases, schedulers, orchestration and query services
├── config           # Spring/security/configuration wiring
├── domain           # entities, domain models and ports
├── infrastructure   # web adapters, scraper adapters and persistence adapters
└── util             # technical helpers
```

## Architectural direction

The intended direction is hexagonal architecture:

- `domain` owns core concepts and ports.
- `application` orchestrates use cases.
- `infrastructure` implements adapters for HTTP, scraping and persistence.
- `config` wires Spring concerns.

## Known deviations

Some application services still depend directly on infrastructure repositories or JDBC details. This is currently accepted as legacy and should be reduced incrementally through small patches.

## Recommended rule

New behavior should prefer ports in `domain.port` and adapters in `infrastructure`, unless the class is an explicit read model/query service.
