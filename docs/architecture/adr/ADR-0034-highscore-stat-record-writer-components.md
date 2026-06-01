# ADR-0034: Split HighscoreStatRecordWriter into focused persistence collaborators

## Status

Accepted.

## Context

`HighscoreStatRecordWriter` is the infrastructure adapter behind `HighscoreStatRecordRepositoryPort`.
After the highscore storage model became compact, the adapter accumulated several independent responsibilities:

- route rows between experience-specific storage and compact storage;
- write daily experience snapshots;
- write experience ranks by vocation filter;
- maintain compact current records;
- maintain compact historical periods;
- map `StatCategory` values to compact numeric category codes.

Keeping all of these details in one class made the adapter harder to review and made future changes to one storage strategy riskier for the others.

## Decision

Keep `HighscoreStatRecordWriter` as the public Spring adapter and port implementation, but make it a facade.
The persistence details are delegated to focused infrastructure collaborators:

- `HighscoreExperienceStatRecordWriter` writes `highscore_exp_daily` and `highscore_exp_rank_daily` rows.
- `HighscoreCompactStatRecordWriter` writes `highscore_current_records` and `highscore_record_periods` rows.
- `HighscoreStatCategoryCodeMapper` owns the mapping between `StatCategory` and compact storage category codes.

The transaction boundary remains on `HighscoreStatRecordWriter.upsertBatch`, preserving existing behavior while reducing the class responsibilities.

## Consequences

The production highscore storage behavior remains unchanged, but storage responsibilities are now isolated by strategy.

Future changes to experience snapshots, compact records or category-code mappings should be made in their dedicated collaborators instead of re-expanding the facade.

An ArchUnit rule keeps `HighscoreStatRecordWriter` away from direct JDBC and SQL details so it remains a port adapter facade.
