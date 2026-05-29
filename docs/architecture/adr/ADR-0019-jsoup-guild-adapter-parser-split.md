# ADR-0019: Split JsoupGuildAdapter into transport and parser collaborators

## Status

Accepted

## Context

`JsoupGuildAdapter` had accumulated two responsibilities:

1. fetching Tibia guild pages over HTTP; and
2. parsing guild list/detail HTML, members, invites, ranks, titles and metadata.

This made the adapter harder to reason about and increased the risk of changing network behavior while adjusting parser rules, or changing parser behavior while adjusting transport behavior.

## Decision

Keep `JsoupGuildAdapter` as the public `GuildScrapePort` adapter, but move focused responsibilities to collaborators:

- `TibiaGuildHttpClient`: HTTP access and Tibia guild URL construction;
- `GuildListPageParser`: guild list page parsing;
- `GuildDetailPageParser`: guild detail, members and invites parsing;
- `GuildPageParsingSupport`: shared normalization, URL decoding, date parsing and hashing helpers.

`JsoupGuildAdapter` remains responsible only for coordinating HTTP fetches, delegating parsing and translating checked `IOException` failures into application-level runtime failures.

## Consequences

- Guild parser behavior remains covered by the existing HTML fixture characterization test.
- Parser changes can now be made without editing the transport-facing adapter.
- Future parser extraction can split member rows and invite rows further without changing the `GuildScrapePort` contract.
- An ArchUnit rule prevents `JsoupGuildAdapter` from depending directly on Jsoup DOM parsing classes again.
