# Coverage strategy

The goal of TibiaChrono coverage work is to raise confidence in behavior, not to chase artificial 100% coverage.

## Current baseline

The current JaCoCo report after the architecture hardening phase shows approximately:

- 56% instruction coverage
- 34% branch coverage
- 78 automated tests
- 40 ArchUnit architecture rules

The biggest uncovered areas are application services, domain model branches, schedulers and GraphQL/web glue.

## Target progression

Use progressive targets instead of a single jump to 100%:

1. Raise total line/instruction coverage to roughly 65% with low-cost unit tests for domain and application behavior.
2. Raise to roughly 70% by covering application services, retry/backoff flows and read-model edge cases.
3. Evaluate 75%+ only after the critical flows are covered and the remaining misses are mostly low-value glue code.

## What should be covered first

Prioritize tests for:

- domain normalization, invariants and factory methods;
- scraper parsing with HTML fixtures;
- highscore throttle, retry, backoff and persistence behavior;
- read-model filters, pagination and empty-result behavior;
- REST/GraphQL contracts that are part of the supported public API.

## What should not drive the strategy

Avoid writing brittle tests only to touch lines in:

- simple getters/setters and JPA-only structural entities;
- Spring framework wiring with no behavior beyond annotation glue;
- generated or local-only artifacts;
- one-line records that are already covered indirectly, unless they contain normalization or clamping logic.

## Gate policy

Keep the JaCoCo minimum below the current real coverage until the new tests are measured in CI/local Docker.
After each test hardening batch:

1. run `make test && make qa`;
2. run `make test-coverage`;
3. inspect the package/class hotspots;
4. raise the minimum coverage only to a value safely below the measured coverage.

Do not raise the gate in the same patch that adds broad new tests unless the measured result is known.

## Measured milestone after coverage batch 37

After the service persistence/status coverage batch and its repair, the local Docker test runner measured:

- 162 automated tests;
- approximately 74% instruction coverage;
- approximately 50% branch coverage;
- approximately 73% line coverage;
- `application.service` at approximately 77% instruction coverage.

The JaCoCo bundle line coverage gate was raised to 70%, which is intentionally below the measured line coverage to avoid fragile failures while still preventing large regressions.

## Measured milestone after coverage batch 38

After scheduler, REST controller and mediator coverage tests, the local Docker test runner measured:

- 180 automated tests;
- approximately 77% instruction coverage;
- approximately 51% branch coverage;
- approximately 76% line coverage;
- `application.scheduler` at approximately 85% instruction coverage;
- `application.mediator` at 100% instruction coverage;
- `infrastructure.adapter.web.rest` at approximately 75% instruction coverage.

The JaCoCo bundle line coverage gate was raised to 75%, still below the measured line coverage and backed by focused tests for schedulers, REST adapters and mediator behavior.

## Measured milestone after coverage batch 39

After parser and highscore persistence tail coverage tests, the local Docker test runner measured:

- 190 automated tests;
- approximately 79% instruction coverage;
- approximately 56% branch coverage;
- approximately 78% line coverage;
- `infrastructure.adapter.scraper` at approximately 80% instruction coverage;
- `infrastructure.persistence` at approximately 79% instruction coverage.

The JaCoCo bundle line coverage gate was raised to 78%, still slightly below the measured line coverage and backed by focused tests for parser branches and highscore persistence collaborators.

## Measured milestone after coverage batch 40

After query facade, guild read model and command record coverage tests, the local Docker test runner measured:

- 197 automated tests;
- approximately 80% instruction coverage;
- approximately 57% branch coverage;
- approximately 80% line coverage;
- `application.command` at 100% instruction coverage;
- `application.query` at approximately 80% instruction coverage;
- `domain.model` at approximately 84% instruction coverage.

The JaCoCo bundle line coverage gate was raised to 80%, aligned with the measured batch 40 line coverage and reinforced by the additional highscore/domain tail tests in batch 41.
