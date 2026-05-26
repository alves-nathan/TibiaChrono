package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.World;
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
            List<WorldSummary> out = new ArrayList<>();
            Elements rows = doc.select("div.TableContentContainer table.TableContent tr");
            for (Element tr : rows) {
                Elements tds = tr.select("td");
                if (tds.size() < 4) continue;
                String name = tds.get(0).text().trim();
                int online = parseIntSafe(tds.get(1).text().trim());
                String location = tds.get(2).text().trim();
                String pvp = tds.get(3).text().trim();

                // The worlds overview table contains extra columns after PvP Type.
                // In the current Tibia layout, Transfer Type is the last column.
                String transferType = tds.size() >= 7 ? tds.get(6).text().trim() : null;

                if (!name.isBlank() && !name.equalsIgnoreCase("World")) {
                    out.add(new WorldSummary(name, pvp, location, online, blankToNull(transferType)));
                }
            }
            return out;
        } catch (IOException e){ throw new RuntimeException(e); }
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
            List<OnlineCharacter> players = new ArrayList<>();

            Elements rowsT1 = doc.select("table.Table1 div.InnerTableContainer tbody tr");
            for (Element tr : rowsT1){
                String rowText = tr.text();
                Element valueCell = tr.lastElementChild();
                if (valueCell == null) {
                    continue;
                }

                if(rowText.contains("Players Online:")) {
                    online = parseIntSafe(valueCell.text());
                    continue;
                }
                if(rowText.contains("Creation Date:")) {
                    if(world.getCreationDate() == null){
                        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                                .appendPattern("MMMM uuuu")
                                .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                                .toFormatter(Locale.ENGLISH);
                        world.setCreationDate(LocalDate.parse(valueCell.text().trim(), formatter));
                        continue;
                    }
                }
                if(rowText.contains("Online Record:")) {
                    if(world.getOnlineRecord() == null){
                        world.setOnlineRecord(valueCell.text().trim());
                        continue;
                    }
                }
                if(rowText.contains("PvP Type:")) {
                    if(world.getPvpType() == null){
                        world.setPvpType(valueCell.text().trim());
                        continue;
                    }
                }
                if(rowText.contains("Transfer Type:") || rowText.contains("World Transfer Type:")) {
                    world.setTransferType(valueCell.text().trim());
                    continue;
                }
                if(rowText.contains("Game World Type:")) {
                    world.setGameWorldType(valueCell.text().trim());
                }
            }

            Elements rowsT2 = doc.select("table.Table2 div.InnerTableContainer tr");
            for (Element tr : rowsT2){
                String rowText = tr.text();
                if (rowText.contains("Name [sort] Level [sort] Vocation [sort]")) {
                    continue;
                }

                Elements tds = tr.select("td");
                Element nameLink = tr.selectFirst("td > a[href*=?name=]");
                if (nameLink == null) {
                    continue;
                }

                String name = nameLink.text().trim();
                if (name.isBlank()) {
                    continue;
                }

                Integer level = null;
                String vocation = null;
                if (tds.size() >= 3) {
                    level = parseNullableInt(tds.get(1).text());
                    vocation = blankToNull(tds.get(2).text().trim());
                }

                players.add(new OnlineCharacter(name, level, vocation));
            }
            return new WorldOnline(
                    worldName,
                    online,
                    players,
                    world.getOnlineRecord(),
                    world.getCreationDate(),
                    blankToNull(world.getTransferType()),
                    blankToNull(world.getGameWorldType())
            );
        } catch (IOException e){ throw new RuntimeException(e); }
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
            if (!doc.text().contains("Former Names:")) return name;
            for (Element line : tr) {
                if (line.text().contains("Former Names:")) {
                    return line.select("td:last-child").text();
                }
            }
            return name;
        } catch (IOException e){ throw new RuntimeException(e); }
    }

    private int parseIntSafe(String s){
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch(Exception e){
            return 0;
        }
    }

    private Integer parseNullableInt(String s){
        try {
            String digits = s.replaceAll("[^0-9]", "");
            return digits.isBlank() ? null : Integer.parseInt(digits);
        } catch(Exception e){
            return null;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
