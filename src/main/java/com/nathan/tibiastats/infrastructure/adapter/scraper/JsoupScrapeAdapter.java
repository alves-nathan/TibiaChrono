package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.ScrapePort;
import org.jsoup.Jsoup; import org.jsoup.nodes.Document; import org.jsoup.nodes.Element; import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*; import java.io.IOException;

@Component
public class JsoupScrapeAdapter implements ScrapePort {
    private static final String WORLDS_URL = "https://www.tibia.com/community/?subtopic=worlds";

    @Override
    public List<WorldSummary> fetchWorldsOverview() {
        try {
            Document doc = Jsoup.connect(WORLDS_URL).get();
            // TODO: Adjust selectors to Tibia HTML structure
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
    public WorldOnline fetchWorldPage(String worldName) {
        try {
            String url = WORLDS_URL + "&world=" + worldName;
            Document doc = Jsoup.connect(url).get();
            // TODO: Adjust selectors to Tibia HTML structure
            int online = 0; List<String> players = new ArrayList<>();
            Element onlineEl = doc.selectFirst("td:contains(Players Online) + td");
            if (onlineEl != null) online = parseIntSafe(onlineEl.text());
            Elements rows = doc.select("table.Table2 div.InnerTableContainer tr");
            for (Element tr : rows){
                Element nameTd = tr.selectFirst("td a:last-of-type");
                if (nameTd != null) players.add(nameTd.text());
            }
            return new WorldOnline(worldName, online, players);
        } catch (IOException e){ throw new RuntimeException(e); }
    }

    @Override
    public boolean isFormerName(String oldName, String newName) {
        try {
            String url = "https://www.tibia.com/community/?subtopic=characters&name=" + URLEncoder.encode(oldName, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(url).get();
            // adjust selector to where “Former Names” appear on character page
            Elements links = doc.select("div.FormerNames a");  // example
            for (Element a : links) {
                if (newName.equalsIgnoreCase(a.text().trim())) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            // log error and treat as not former name
            return false;
        }
    }

    private int parseIntSafe(String s){
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]",""));
        } catch(Exception e){
            return 0;
        }
    }
}