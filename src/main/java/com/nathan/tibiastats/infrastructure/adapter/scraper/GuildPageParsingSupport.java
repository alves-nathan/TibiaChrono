package com.nathan.tibiastats.infrastructure.adapter.scraper;

import org.jsoup.nodes.Element;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GuildPageParsingSupport {
    private static final Pattern CHARACTER_NAME_FROM_URL = Pattern.compile("[?&]name=([^&]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GUILD_NAME_FROM_URL = Pattern.compile("[?&]GuildName=([^&]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEVEL_PATTERN = Pattern.compile("^\\d{1,5}$");
    private static final List<String> VOCATION_WORDS = List.of(
            "No Vocation", "Elder Druid", "Master Sorcerer", "Elite Knight", "Royal Paladin", "Exalted Monk",
            "None", "Druid", "Sorcerer", "Knight", "Paladin", "Monk"
    );

    private GuildPageParsingSupport() {
    }

    static String extractGuildName(Element link) {
        String href = link.attr("href");
        Matcher matcher = GUILD_NAME_FROM_URL.matcher(href);
        if (matcher.find()) return decode(matcher.group(1));
        return link.text();
    }

    static String extractCharacterName(Element link) {
        if (link == null) return "";
        String href = link.attr("href");
        Matcher matcher = CHARACTER_NAME_FROM_URL.matcher(href);
        if (matcher.find()) return decode(matcher.group(1));
        return link.text();
    }

    static Element firstCharacterLink(Element row) {
        return row.select("a[href*=subtopic=characters], a[href*=characters]").stream()
                .filter(link -> normalize(extractCharacterName(link)).length() > 1)
                .findFirst()
                .orElse(null);
    }

    static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) return null;
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.ENGLISH),
                DateTimeFormatter.ISO_LOCAL_DATE
        );
        String cleaned = normalize(text).replace(",", "");
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(cleaned, formatter);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    static Optional<LocalDate> findJoiningDate(List<String> cells) {
        for (String cell : cells) {
            LocalDate date = parseDate(cell);
            if (date != null) return Optional.of(date);
        }
        return Optional.empty();
    }

    static Optional<String> findVocation(List<String> cells) {
        for (String cell : cells) {
            for (String vocation : VOCATION_WORDS) {
                if (cell.equalsIgnoreCase(vocation)) return Optional.of(vocation);
            }
        }
        return Optional.empty();
    }

    static Optional<Integer> findLevel(List<String> cells) {
        for (String cell : cells) {
            String normalized = normalize(cell);
            if (parseDate(normalized) != null) continue;
            if (isLevel(normalized)) return Optional.of(Integer.parseInt(normalized));
        }
        return Optional.empty();
    }

    static boolean isLevel(String value) {
        return LEVEL_PATTERN.matcher(normalize(value)).matches();
    }

    static List<String> vocationWords() {
        return VOCATION_WORDS;
    }

    static boolean isOnlineStatus(String value) {
        String lower = normalize(value).toLowerCase(Locale.ROOT);
        return lower.equals("online") || lower.equals("offline");
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    static String firstNonBlankOrNull(String... values) {
        String value = firstNonBlank(values);
        return value.isBlank() ? null : value;
    }

    static String normalize(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    static String removeWholeValue(String text, String value) {
        if (text == null || value == null || value.isBlank()) return text;
        String normalizedText = normalize(text);
        String normalizedValue = normalize(value);
        if (normalizedText.equalsIgnoreCase(normalizedValue)) return "";
        return normalize(normalizedText.replaceAll("(?i)(^|\\s)" + Pattern.quote(normalizedValue) + "(?=\\s|$)", " "));
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
