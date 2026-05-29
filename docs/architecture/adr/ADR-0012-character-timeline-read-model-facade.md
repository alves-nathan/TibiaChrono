# ADR-0012: CharacterTimelineService as a read-model facade

## Status
Accepted

## Context
`CharacterTimelineService` had accumulated direct SQL for deaths, world history, guild history, core character events, experience history, non-experience highscores, and online-session assembly.

That made it difficult to evolve timeline behavior safely because a single class mixed orchestration, SQL read models, result-set mapping, highscore category normalization, and API-facing response records.

## Decision
Keep `CharacterTimelineService` as the stable API-facing facade and split the SQL-heavy timeline reads into focused read-model services:

- `CharacterHistoryReadModelService` for deaths, world history, and guild history.
- `CharacterTimelineCoreReadModelService` for core character timeline events.
- `CharacterTimelineHighscoreReadModelService` for experience and non-experience highscore timeline events.
- `CharacterTimelineJdbcSupport` for shared JDBC parameter and result-set mapping helpers.

`CharacterTimelineService` remains responsible for composing the final unified timeline, applying the final sort/filter/limit rules, and preserving the public nested response records used by controllers.

## Consequences
Positive:

- Smaller classes with clearer reasons to change.
- SQL-heavy logic is isolated in dedicated read models.
- The facade can be protected by ArchUnit from reintroducing direct JDBC dependencies.
- Public API contracts remain unchanged because the nested response records stay in `CharacterTimelineService`.

Trade-offs:

- More classes in `application.query`.
- The focused read models intentionally depend on `CharacterTimelineService` nested record types until API DTOs are moved to dedicated files.

## Follow-up
A future refactor can move timeline response records to top-level DTOs when controller/API compatibility is reviewed.
