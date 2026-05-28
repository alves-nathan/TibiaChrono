# Scraper Strategy

Scrapers are the riskiest integration point because Tibia.com HTML and anti-bot behavior can change outside this project.

## Principles

1. Treat Tibia.com as an unreliable external dependency.
2. Prefer parser tests using local HTML fixtures before refactoring scraper code.
3. Keep global backoff/cooldown behavior outside parser code.
4. Avoid storing raw transient UI artifacts as canonical domain state.
5. Log enough context to diagnose parser drift without logging excessive HTML.

## Recommended test structure

```text
src/test/resources/fixtures/tibia/
├── worlds-overview.html
├── world-detail.html
├── highscores-experience.html
├── character-detail.html
└── guild-detail.html
```

## Backoff policy

HTTP 403/429 responses should trigger global cooldown for highscore scraping. Repeated failures should progressively increase cooldown up to a configured maximum.
