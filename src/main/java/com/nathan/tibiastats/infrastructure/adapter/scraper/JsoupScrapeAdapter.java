package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.model.CharacterNameNormalizer;
import com.nathan.tibiastats.domain.port.ScrapePort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class JsoupScrapeAdapter implements ScrapePort {
    private static final String WORLDS_URL = "https://www.tibia.com/community/?subtopic=worlds";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; TibiaChronoBot/1.0; +https://localhost)";
    private static final int TIMEOUT_MS = 15000;

    @Override
    public List<WorldSummary> fetchWorldsOverview() {
        try {
            Document doc = Jsoup.connect(WORLDS_URL)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            List<WorldSummary> worlds = new ArrayList<>();
            Elements rows = doc.select("div.TableContentContainer table.TableContent tr");

            for (Element tr : rows) {
                Elements tds = tr.select("> td");
                if (tds.size() < 4) {
                    continue;
                }

                String name = tds.get(0).text().trim();
                if (name.isBlank() || name.equalsIgnoreCase("World")) {
                    continue;
                }

                int online = parseIntSafe(tds.get(1).text());
                String location = tds.get(2).text().trim();
                String pvp = tds.get(3).text().trim();
                String additionalInfo = tds.stream()
                        .skip(4)
                        .map(this::cellTextIncludingImageLabels)
                        .collect(Collectors.joining(" "))
                        .trim();

                String transferType = extractTransferType(additionalInfo).orElse("Regular");
                String gameWorldType = extractGameWorldType(additionalInfo).orElse(null);

                worlds.add(new WorldSummary(name, pvp, location, online, transferType, gameWorldType));
            }

            return worlds;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public WorldOnline fetchWorldPage(String worldName, World world) {
        try {
            String url = WORLDS_URL + "&world=" + URLEncoder.encode(worldName, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            int online = 0;
            List<OnlineCharacterSnapshot> players = new ArrayList<>();

            Elements rowsT1 = doc.select("table.Table1 div.InnerTableContainer tbody tr");
            for (Element tr : rowsT1) {
                String rowText = tr.text();
                String value = lastCellText(tr);

                if (rowText.contains("Players Online:")) {
                    online = parseIntSafe(value);
                    continue;
                }

                if (rowText.contains("Creation Date:") && world.getCreationDate() == null) {
                    DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                            .appendPattern("MMMM uuuu")
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter(Locale.ENGLISH);
                    world.setCreationDate(LocalDate.parse(value, formatter));
                    continue;
                }

                if (rowText.contains("Online Record:") && world.getOnlineRecord() == null) {
                    world.setOnlineRecord(value);
                    continue;
                }

                if (rowText.contains("PvP Type:") && world.getPvpType() == null) {
                    world.setPvpType(value);
                    continue;
                }

                if (rowText.contains("Transfer Type:")) {
                    world.setTransferType(value);
                    continue;
                }

                if (rowText.contains("Game World Type:")) {
                    world.setGameWorldType(value);
                }
            }

            Elements rowsT2 = doc.select("table.Table2 div.InnerTableContainer tr");
            for (Element tr : rowsT2) {
                String rowText = tr.text();
                if (rowText.contains("Name [sort] Level [sort] Vocation [sort]")) {
                    continue;
                }

                Elements cols = tr.select("> td");
                if (cols.isEmpty()) {
                    continue;
                }

                String name = tr.select("a[href*=?name=]").text().trim();
                if (name.isBlank()) {
                    continue;
                }

                Integer level = cols.size() > 1 ? parseIntegerOrNull(cols.get(1).text()) : null;
                String vocation = cols.size() > 2 ? blankToNull(cols.get(2).text()) : null;

                players.add(new OnlineCharacterSnapshot(name, level, vocation));
            }

            return new WorldOnline(
                    worldName,
                    online,
                    players,
                    world.getOnlineRecord(),
                    world.getCreationDate(),
                    world.getTransferType(),
                    world.getGameWorldType()
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getFormerName(String name) {
        try {
            String url = "https://www.tibia.com/community/?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            Elements tr = doc.select("table.TableContent tr");
            if (!doc.text().contains("Former Names:")) {
                return CharacterNameNormalizer.normalize(name);
            }
            for (Element line : tr) {
                if (line.text().contains("Former Names:")) {
                    return CharacterNameNormalizer.normalizeCsv(line.select("td:last-child").text());
                }
            }
            return CharacterNameNormalizer.normalize(name);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String cellTextIncludingImageLabels(Element cell) {
        StringBuilder value = new StringBuilder(cell.text().trim());
        for (Element img : cell.select("img")) {
            appendIfPresent(value, img.attr("title"));
            appendIfPresent(value, img.attr("alt"));
        }
        return value.toString().trim();
    }

    private Optional<String> extractTransferType(String value) {
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

    private Optional<String> extractGameWorldType(String value) {
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

    private String lastCellText(Element row) {
        return Objects.requireNonNull(row.lastElementChild()).text().trim();
    }

    private void appendIfPresent(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(value.trim());
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
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

    private int parseIntSafe(String value) {
        return parseIntegerOrNull(value) == null ? 0 : parseIntegerOrNull(value);
    }
}
