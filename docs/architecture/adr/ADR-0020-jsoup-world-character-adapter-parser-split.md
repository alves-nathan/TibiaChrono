# ADR-0020: Split Jsoup world and character adapters into transport and parser collaborators

## Status
Accepted

## Context

`JsoupScrapeAdapter` and `JsoupCharacterAdapter` were responsible for both HTTP access and HTML parsing.
This made the adapter classes harder to maintain and increased the chance that future changes would mix transport concerns, Tibia page parsing details and domain-port mapping in the same class.

The guild scraper already follows a cleaner adapter shape where the adapter is a facade and page-specific parsing is delegated to parser collaborators.
The world and character scrapers should follow the same direction.

## Decision

Keep `JsoupScrapeAdapter` and `JsoupCharacterAdapter` as infrastructure adapters/facades for their ports.
Move HTTP access and DOM parsing details into focused collaborators:

- `TibiaWorldHttpClient`
- `WorldOverviewPageParser`
- `WorldDetailPageParser`
- `FormerCharacterNamePageParser`
- `WorldPageParsingSupport`
- `TibiaCharacterHttpClient`
- `CharacterDetailsPageParser`

Add ArchUnit rules to prevent `JsoupScrapeAdapter` and `JsoupCharacterAdapter` from depending directly on Jsoup DOM packages.

## Consequences

The adapters now expose the same port behavior with smaller orchestration-only classes.
HTML parsing is isolated in parser classes and remains covered by existing characterization tests.
Future Tibia layout changes should be handled in parser collaborators rather than in the adapter facade.
