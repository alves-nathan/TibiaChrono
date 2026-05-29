# ADR-0024: Split JsoupHighscoreAdapter into transport and parser collaborators

## Status

Accepted

## Context

`JsoupHighscoreAdapter` used to combine HTTP transport, Tibia highscore URL construction, HTML parsing and value normalization in one class. This made the adapter harder to test and evolve because parser details could leak back into the public infrastructure adapter.

The project already uses facade-style scraper adapters for guild, world and character pages. The highscore scraper should follow the same boundary so the adapter remains responsible for the `HighscorePort` contract while parsing stays in dedicated collaborators.

## Decision

Split the highscore scraper adapter into:

- `JsoupHighscoreAdapter`: facade/adapter for `HighscorePort`.
- `TibiaHighscoreHttpClient`: Tibia highscore URL construction and HTTP access.
- `HighscorePageParser`: Jsoup DOM parsing and highscore row extraction.

Add an ArchUnit rule preventing `JsoupHighscoreAdapter` from depending directly on Jsoup DOM packages (`org.jsoup.nodes..` and `org.jsoup.select..`).

## Consequences

The adapter remains smaller and transport-oriented. Parser behavior stays isolated and can continue to be covered by fixture characterization tests without network access. Future highscore HTML changes should be handled in `HighscorePageParser`, not in the adapter facade.
