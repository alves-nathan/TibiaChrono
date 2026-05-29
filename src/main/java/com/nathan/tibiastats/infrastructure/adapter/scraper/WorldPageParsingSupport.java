package com.nathan.tibiastats.infrastructure.adapter.scraper;

import org.jsoup.nodes.Element;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

final class WorldPageParsingSupport {
    private WorldPageParsingSupport() {
    }

    static String cellTextIncludingImageLabels(Element cell) {
        StringBuilder value = new StringBuilder(cell.text().trim());
        for (Element img : cell.select("img")) {
            appendIfPresent(value, img.attr("title"));
            appendIfPresent(value, img.attr("alt"));
        }
        return value.toString().trim();
    }

    static Optional<String> extractTransferType(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        if (normalized.contains("blocked") || normalized.contains("closed")) {
            return Optional.of("Blocked");
        }

        if (normalized.contains("locked")) {
            return Optional.of("Locked");
        }

        if (normalized.contains("transfer")) {
            return Optional.of(value.trim());
        }

        return Optional.empty();
    }

    static Optional<String> extractGameWorldType(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        if (normalized.contains("premium")) {
            return Optional.of("Premium");
        }

        if (normalized.contains("experimental")) {
            return Optional.of("Experimental");
        }

        if (normalized.contains("restricted")) {
            return Optional.of("Restricted");
        }

        if (normalized.contains("tournament")) {
            return Optional.of("Tournament");
        }

        return Optional.empty();
    }

    static String lastCellText(Element row) {
        return Objects.requireNonNull(row.lastElementChild()).text().trim();
    }

    static String blankToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
    }

    static Integer parseIntegerOrNull(String value) {
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

    static int parseIntSafe(String value) {
        Integer parsed = parseIntegerOrNull(value);
        return parsed == null ? 0 : parsed;
    }

    private static void appendIfPresent(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(value.trim());
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
