package com.nathan.tibiastats.infrastructure.adapter.scraper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class CharacterDetailsDateParser {
    private static final Logger log = LoggerFactory.getLogger(CharacterDetailsDateParser.class);
    private static final String LOG_PREFIX = "[CHARACTER_DETAILS_SCRAPER]";

    private static final DateTimeFormatter SHORT_MONTH_DATE_TIME =
            DateTimeFormatter.ofPattern("MMM d yyyy, HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter LONG_MONTH_DATE_TIME =
            DateTimeFormatter.ofPattern("MMMM d yyyy, HH:mm:ss", Locale.ENGLISH);

    Optional<OffsetDateTime> parseTibiaDateTime(String value) {
        String normalized = normalizeValue(value);
        if (normalized.isBlank() || normalized.equalsIgnoreCase("never logged in.")) {
            return Optional.empty();
        }

        String[] parts = normalized.split("\\s+");
        if (parts.length < 2) {
            return Optional.empty();
        }

        String zoneAbbreviation = parts[parts.length - 1];
        String dateTimePart = normalized.substring(0, normalized.length() - zoneAbbreviation.length()).trim();
        ZoneOffset offset = zoneOffsetFor(zoneAbbreviation);

        for (DateTimeFormatter formatter : List.of(SHORT_MONTH_DATE_TIME, LONG_MONTH_DATE_TIME)) {
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(dateTimePart, formatter);
                return Optional.of(OffsetDateTime.of(localDateTime, offset));
            } catch (DateTimeParseException ignored) {
                // Try the next accepted Tibia date format.
            }
        }

        log.debug("{} Could not parse Tibia date/time: '{}'", LOG_PREFIX, value);
        return Optional.empty();
    }

    private ZoneOffset zoneOffsetFor(String abbreviation) {
        if (abbreviation == null) {
            return ZoneOffset.UTC;
        }

        return switch (abbreviation.trim().toUpperCase(Locale.ROOT)) {
            case "CEST" -> ZoneOffset.ofHours(2);
            case "CET" -> ZoneOffset.ofHours(1);
            case "BRT" -> ZoneOffset.ofHours(-3);
            case "UTC", "GMT" -> ZoneOffset.UTC;
            default -> ZoneOffset.UTC;
        };
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }
}
