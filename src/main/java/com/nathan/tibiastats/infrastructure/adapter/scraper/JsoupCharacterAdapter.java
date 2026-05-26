package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.port.CharacterDetailPort;
import com.nathan.tibiastats.domain.model.CharacterNameNormalizer;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class JsoupCharacterAdapter implements CharacterDetailPort {
    private static final Logger log = LoggerFactory.getLogger(JsoupCharacterAdapter.class);
    private static final String LOG_PREFIX = "[CHARACTER_DETAILS_SCRAPER]";

    private static final String CHARACTER_PAGE_URL_TEMPLATE =
            "https://www.tibia.com/community/?subtopic=characters&name=%s";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; TibiaChronoBot/1.0; +https://localhost)";
    private static final int TIMEOUT_MS = 15000;

    private static final DateTimeFormatter SHORT_MONTH_DATE_TIME =
            DateTimeFormatter.ofPattern("MMM d yyyy, HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter LONG_MONTH_DATE_TIME =
            DateTimeFormatter.ofPattern("MMMM d yyyy, HH:mm:ss", Locale.ENGLISH);

    @Override
    public NameDetails fetchNameDetails(String worldName, String characterName) {
        return fetchCharacterDetails(characterName)
                .map(details -> new NameDetails(details.currentName(), details.formerNames()))
                .orElseGet(() -> new NameDetails(characterName, List.of()));
    }

    @Override
    public Optional<CharacterDetails> fetchCharacterDetails(String characterName) {
        String url = CHARACTER_PAGE_URL_TEMPLATE.formatted(URLEncoder.encode(characterName, StandardCharsets.UTF_8));

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            if (isCharacterNotFound(doc)) {
                log.warn("{} Character not found on Tibia.com: {}", LOG_PREFIX, characterName);
                return Optional.empty();
            }

            Map<String, String> fields = collectCharacterFields(doc);
            if (fields.isEmpty()) {
                log.warn("{} No character profile fields parsed for {}. title='{}'", LOG_PREFIX, characterName, doc.title());
            } else {
                log.debug("{} Parsed fields for {}: {}", LOG_PREFIX, characterName, fields.keySet());
            }

            String currentName = firstNonBlank(fields.get("name"), characterName);
            List<String> formerNames = splitFormerNames(fields.get("former names"));
            CharacterEntity.Sex sex = parseSex(fields.get("sex"));
            String vocation = fields.get("vocation");
            Integer level = parseIntegerOrNull(fields.get("level"));
            Integer achievementPoints = parseIntegerOrNull(fields.get("achievement points"));
            String residence = fields.get("residence");
            OffsetDateTime lastLogin = parseTibiaDateTime(fields.get("last login")).orElse(null);
            String accountStatus = fields.get("account status");
            Instant creationDate = parseTibiaDateTime(fields.get("created"))
                    .map(OffsetDateTime::toInstant)
                    .orElse(null);
            String world = fields.get("world");

            return Optional.of(new CharacterDetails(
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
            ));
        } catch (HttpStatusException e) {
            throw new RuntimeException(
                    "Failed to fetch character details for " + characterName + ": HTTP " + e.getStatusCode(),
                    e
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch character details for " + characterName, e);
        }
    }

    private Map<String, String> collectCharacterFields(Document doc) {
        Map<String, String> fields = new LinkedHashMap<>();

        // Tibia profile rows usually use a LabelV cell followed by the value cell.
        Elements labelCells = doc.select("td.LabelV");
        for (Element labelCell : labelCells) {
            Element valueCell = labelCell.nextElementSibling();
            addField(fields, labelCell, valueCell);
        }

        // Fallback for small layout changes or cached/static HTML variants.
        if (fields.isEmpty()) {
            Elements rows = doc.select("table.TableContent tr");
            for (Element row : rows) {
                Elements cols = row.select("> td");
                if (cols.size() >= 2) {
                    addField(fields, cols.get(0), cols.get(1));
                }
            }
        }

        return fields;
    }

    private void addField(Map<String, String> fields, Element labelCell, Element valueCell) {
        if (labelCell == null || valueCell == null) {
            return;
        }

        String label = normalizeLabel(labelCell.text());
        String value = normalizeValue(valueCell.text());

        if (!label.isBlank() && !value.isBlank()) {
            fields.putIfAbsent(label, value);
        }
    }

    private boolean isCharacterNotFound(Document doc) {
        String text = doc.text().toLowerCase(Locale.ROOT);
        return text.contains("could not find character")
                || text.contains("character does not exist")
                || text.contains("no character with this name");
    }

    private String normalizeLabel(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(':', ' ')
                .replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", " ");
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

    private Optional<OffsetDateTime> parseTibiaDateTime(String value) {
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
