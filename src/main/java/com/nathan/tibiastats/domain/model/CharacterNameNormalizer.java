package com.nathan.tibiastats.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Normalizes raw character names scraped from Tibia.com.
 * Tibia may append UI/status tags such as "(traded)" next to a character name.
 * Those tags are metadata and must not become part of character_names.name.
 */
public final class CharacterNameNormalizer {
    private static final Pattern TRADED_SUFFIX = Pattern.compile("\\s*\\((?i:traded)\\)\\s*$");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    private CharacterNameNormalizer() {}

    public static String normalize(String rawName) {
        if (rawName == null) {
            return null;
        }

        String normalized = rawName.replace('\u00A0', ' ').trim();

        String previous;
        do {
            previous = normalized;
            normalized = TRADED_SUFFIX.matcher(normalized).replaceAll("").trim();
        } while (!Objects.equals(previous, normalized));

        return MULTIPLE_SPACES.matcher(normalized).replaceAll(" ").trim();
    }

    public static boolean isBlank(String rawName) {
        String normalized = normalize(rawName);
        return normalized == null || normalized.isBlank();
    }

    public static boolean sameName(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);

        if (normalizedLeft == null || normalizedRight == null) {
            return normalizedLeft == normalizedRight;
        }

        return normalizedLeft.equalsIgnoreCase(normalizedRight);
    }

    public static List<String> normalizeMany(List<String> rawNames) {
        if (rawNames == null || rawNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> normalizedNames = new ArrayList<>();
        for (String rawName : rawNames) {
            String normalized = normalize(rawName);
            if (normalized != null && !normalized.isBlank()) {
                normalizedNames.add(normalized);
            }
        }
        return normalizedNames;
    }

    public static List<String> normalizeCsvToList(String rawNames) {
        if (rawNames == null || rawNames.isBlank()) {
            return Collections.emptyList();
        }

        List<String> normalizedNames = new ArrayList<>();
        for (String rawName : rawNames.split(",")) {
            String normalized = normalize(rawName);
            if (normalized != null && !normalized.isBlank()) {
                normalizedNames.add(normalized);
            }
        }
        return normalizedNames;
    }

    public static String normalizeCsv(String rawNames) {
        return String.join(",", normalizeCsvToList(rawNames));
    }

    public static String normalizedKey(String rawName) {
        String normalized = normalize(rawName);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
}
