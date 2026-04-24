package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.ScrapePort;
import org.jsoup.Jsoup; import org.jsoup.nodes.Document; import org.jsoup.nodes.Element; import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*; import java.io.IOException;

@Component
public class JsoupScrapeAdapter implements ScrapePort {
    private static final String WORLDS_URL = "https://www.tibia.com/community/?subtopic=worlds";

    @Override
    public List<WorldSummary> fetchWorldsOverview() {
        try {
            Document doc = Jsoup.connect(WORLDS_URL).get();
            List<WorldSummary> out = new ArrayList<>();
            Elements rows = doc.select("div.TableContentContainer table.TableContent tr");
            for (Element tr : rows) {
                Elements tds = tr.select("td");
                if (tds.size() < 4) continue;
                String name = tds.get(0).text().trim();
                int online = parseIntSafe(tds.get(1).text().trim());
                String location = tds.get(2).text().trim();
                String pvp = tds.get(3).text();
                if (!name.isBlank() && !name.equalsIgnoreCase("World")) {
                    out.add(new WorldSummary(name, pvp, location, online));
                }
            }
            return out;
        } catch (IOException e){ throw new RuntimeException(e); }
    }

    @Override
    public WorldOnline fetchWorldPage(String worldName, World world) {
        try {
            String url = WORLDS_URL + "&world=" + worldName;
            Document doc = Jsoup.connect(url).get();
            int online = 0;
            List<String> players = new ArrayList<>();

            Elements rowsT1 = doc.select("table.Table1 div.InnerTableContainer tbody tr");
            for (Element tr : rowsT1){
                if(tr.text().contains("Players Online:")) {
                    online = Integer.parseInt(Objects.requireNonNull(tr.lastElementChild()).text());
                    continue;
                }
                if(tr.text().contains("Creation Date:")) {
                    if(world.getCreationDate() == null){
                        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                                .appendPattern("MMMM uuuu")
                                .parseDefaulting(ChronoField.DAY_OF_MONTH, 1) // dia padrão = 1
                                .toFormatter(Locale.ENGLISH);
                        world.setCreationDate(
                                LocalDate.parse(Objects.requireNonNull(tr.lastElementChild()).text(), formatter)
                        );
                        continue;
                    }
                }
                if(tr.text().contains("Online Record:")) {
                    if(world.getOnlineRecord() == null){
                        world.setOnlineRecord(Objects.requireNonNull(tr.lastElementChild()).text());
                        continue;
                    }
                }
                if(tr.text().contains("PvP Type:")) {
                    if(world.getPvpType() == null){
                        world.setPvpType(Objects.requireNonNull(tr.lastElementChild()).text());
                        continue;
                    }
                }
                if(tr.text().contains("Game World Type:")) {
                    if(world.getGameWorldType() == null){
                        world.setGameWorldType(Objects.requireNonNull(tr.lastElementChild()).text());
                    }
                }
            }



            Elements rowsT2 = doc.select("table.Table2 div.InnerTableContainer tr");
            for (Element tr : rowsT2){
                String nameTd = tr.select("td > a").text();
                if(!tr.text().contains("Name [sort] Level [sort] Vocation [sort]")) {

                    players.add(nameTd);
                }
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
        } catch (IOException e){ throw new RuntimeException(e); }
    }

    @Override
    public String getFormerName(String name) {
        try {
            String url = "https://www.tibia.com/community/?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(url).get();
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
            return Integer.parseInt(s.replaceAll("[^0-9]",""));
        } catch(Exception e){
            return 0;
        }
    }
}