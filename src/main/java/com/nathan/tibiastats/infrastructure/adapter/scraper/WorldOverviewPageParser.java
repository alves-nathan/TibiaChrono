package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.ScrapePort.WorldSummary;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.nathan.tibiastats.infrastructure.adapter.scraper.WorldPageParsingSupport.extractGameWorldType;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.WorldPageParsingSupport.extractTransferType;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.WorldPageParsingSupport.parseIntSafe;

@Component
public class WorldOverviewPageParser {
    public List<WorldSummary> parse(Document doc) {
        return parseDocument(doc);
    }

    public List<WorldSummary> parseHtml(String html, String sourceUrl) {
        return parseDocument(Jsoup.parse(html == null ? "" : html, sourceUrl));
    }

    private List<WorldSummary> parseDocument(Document doc) {
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
                    .map(WorldPageParsingSupport::cellTextIncludingImageLabels)
                    .collect(Collectors.joining(" "))
                    .trim();

            String transferType = extractTransferType(additionalInfo).orElse("Regular");
            String gameWorldType = extractGameWorldType(additionalInfo).orElse(null);

            worlds.add(new WorldSummary(name, pvp, location, online, transferType, gameWorldType));
        }

        return worlds;
    }
}
