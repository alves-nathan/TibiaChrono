package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.port.CharacterDetailPort.CharacterDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class CharacterDetailsValueParser {
    private final CharacterDetailsDateParser dateParser;

    public CharacterDetailsValueParser() {
        this(new CharacterDetailsDateParser());
    }

    @Autowired
    CharacterDetailsValueParser(CharacterDetailsDateParser dateParser) {
        this.dateParser = dateParser;
    }

    CharacterDetails toCharacterDetails(Map<String, String> fields, String requestedCharacterName) {
        String currentName = firstNonBlank(fields.get("name"), requestedCharacterName);
        List<String> formerNames = splitFormerNames(fields.get("former names"));
        CharacterEntity.Sex sex = parseSex(fields.get("sex"));
        String vocation = fields.get("vocation");
        Integer level = parseIntegerOrNull(fields.get("level"));
        Integer achievementPoints = parseIntegerOrNull(fields.get("achievement points"));
        String residence = fields.get("residence");
        OffsetDateTime lastLogin = dateParser.parseTibiaDateTime(fields.get("last login")).orElse(null);
        String accountStatus = fields.get("account status");
        Instant creationDate = dateParser.parseTibiaDateTime(fields.get("created"))
                .map(OffsetDateTime::toInstant)
                .orElse(null);
        String world = fields.get("world");

        return new CharacterDetails(
                currentName,
                formerNames,
                sex,
                vocation,
                level,
                achievementPoints,
                residence,
                lastLogin,
                accountStatus,
                creationDate,
                world
        );
    }

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    private List<String> splitFormerNames(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String name = part.trim();
            if (!name.isBlank()) {
                result.add(name);
            }
        }
        return result;
    }

    private CharacterEntity.Sex parseSex(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("male")) {
            return CharacterEntity.Sex.male;
        }
        if (normalized.startsWith("female")) {
            return CharacterEntity.Sex.female;
        }
        return null;
    }

    private Integer parseIntegerOrNull(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
