# Database Evolution and Legacy Areas

## Current position

Flyway is the source of truth for schema evolution. Hibernate should validate the schema rather than mutate it automatically.

## Known legacy or transitional areas

### `character_statrecords`

This table appears to represent the older stat history model. New highscore features should prefer the compact highscore tables unless a migration or compatibility requirement says otherwise.

### Highscore compact tables

The compact highscore model should be treated as the primary model for highscore history, current records and periods.

### Guild history tables

Guild membership tables are newer and should be protected with integration tests before changing parser or identity rules.

## Migration rules

- Use additive migrations for new columns/tables/indexes.
- Use corrective migrations for type fixes and constraints.
- Avoid destructive migrations until the application is stable and data has been backed up.
- Keep manual cleanup SQL under `src/main/resources/db/manual` and document when it should be used.

## Future cleanup candidates

- Review duplicated highscore category migrations.
- Review whether early V1 integer foreign keys are fully corrected by later migrations.
- Review legacy stat tables before any production baseline reset.
