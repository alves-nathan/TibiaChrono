# ADR-0033: Split CharacterDetailsPageParser into focused profile parser collaborators

## Status

Accepted

## Context

`CharacterDetailsPageParser` had accumulated multiple responsibilities while parsing Tibia.com character detail pages:

- identifying not-found pages;
- collecting profile fields from table markup;
- normalizing field labels and values;
- parsing former names, sex, level and achievement points;
- parsing Tibia date/time values and timezone abbreviations;
- building the `CharacterDetails` domain port response.

This made the parser harder to evolve when new character detail sections are added, such as deaths, achievements or additional historical names.

## Decision

Keep `CharacterDetailsPageParser` as the parser facade and move profile parsing details to focused collaborators:

- `CharacterProfileFieldsParser` collects normalized profile fields from the Tibia.com DOM;
- `CharacterDetailsValueParser` maps profile fields into the `CharacterDetails` response, including former names and scalar values;
- `CharacterDetailsDateParser` owns Tibia date/time parsing and timezone conversion.

`CharacterDetailsPageParser` remains responsible only for HTML/document entry points, not-found handling, logging and delegation.

## Consequences

- Future sections can be added as dedicated parser collaborators without growing the facade.
- DOM row details stay isolated in field-specific parser classes.
- Date/time parsing is reusable and easier to test or extend.
- ArchUnit now prevents `CharacterDetailsPageParser` from depending directly on row-level DOM details such as `Element` and `Elements`.
