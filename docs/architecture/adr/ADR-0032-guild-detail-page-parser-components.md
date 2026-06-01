# ADR-0032: Split GuildDetailPageParser into focused detail parser collaborators

## Status

Accepted.

## Context

`GuildDetailPageParser` had accumulated several parsing responsibilities for the Tibia guild detail page:

- guild summary metadata such as world, homepage, description, logo, founded date and counters;
- member table parsing;
- rank and title cleanup for member rows;
- invited character parsing;
- shared date, level, vocation and character-link parsing helpers.

This made the parser difficult to change safely because a small change to rank/title parsing could also affect the page summary or invite parsing.

## Decision

Keep `GuildDetailPageParser` as the detail page facade and split the concrete parsing concerns into focused collaborators:

- `GuildDetailSummaryParser` parses guild metadata and counters;
- `GuildMemberTableParser` parses member rows, rank names, titles, levels, vocations, dates and online status;
- `GuildInviteTableParser` parses invited characters;
- `GuildPageParsingSupport` keeps shared low-level parsing helpers.

The facade remains responsible for assembling the final `GuildDetail` response and preserving fallback counter behavior when the page does not expose member/online counts explicitly.

An ArchUnit rule prevents `GuildDetailPageParser` from depending again on row-level DOM details such as `org.jsoup.nodes.Element` and `org.jsoup.select.*`.

## Consequences

- Member/rank/title parsing can evolve independently from guild summary parsing.
- Invite parsing has a dedicated collaborator.
- `GuildDetailPageParser` stays small and easier to reason about.
- Low-level DOM row traversal remains isolated in specialized parser classes.
