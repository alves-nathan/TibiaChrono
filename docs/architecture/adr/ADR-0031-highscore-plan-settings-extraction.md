# ADR-0031: Extract shared highscore plan settings

## Status

Accepted.

## Context

`HighscoreScrapeProperties` supports two binding modes:

- a legacy single-plan configuration at `tibiastats.scrape.highscores`
- a named plan map at `tibiastats.scrape.highscores.plans.*`

Before this decision, both the root properties class and the nested `Plan` class duplicated the same fields, validation rules, CSV parsing and summary logic. This made future changes to highscore request limits, retry policy, cooldown behavior and plan defaults error-prone because every change had to be mirrored in two places.

## Decision

Move the shared highscore plan fields and validation rules to `HighscorePlanSettings`.

`HighscoreScrapeProperties` now remains responsible only for Spring Boot binding, legacy-to-plan conversion and effective plan selection. The nested `HighscoreScrapeProperties.Plan` class extends the shared settings type so existing callers and configuration keys remain compatible.

## Consequences

- Request budget, retry, cooldown, category and vocation parsing rules have a single implementation.
- Existing code can continue to use `HighscoreScrapeProperties.Plan`.
- Legacy single-plan binding remains supported through `toLegacyPlan()`.
- A dedicated ArchUnit rule prevents `HighscoreScrapeProperties` from taking parsing/model responsibilities back from `HighscorePlanSettings`.
