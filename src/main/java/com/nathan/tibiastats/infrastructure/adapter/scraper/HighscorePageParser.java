package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.HighscorePort.HighscoreRow;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class HighscorePageParser {
    public List<HighscoreRow> parseHtml(String html, String sourceUrl) {
        return parse(Jsoup.parse(html, sourceUrl));
    }

    public List<HighscoreRow> parse(Document doc) {
        List<HighscoreRow> rows = new ArrayList<>();
        Elements tableRows = doc.select("table.TableContent tr");
        for (Element tr : tableRows) {
            Elements tds = tr.select("td");
            if (tds.size() < 3) {
                continue;
            }

            int rank = parseIntSafe(tds.get(0).text());
            String name = tds.get(1).text().trim();
            long value = parseLongSafe(tds.get(tds.size() - 1).text());

            if (rank > 0 && !name.isBlank()) {
                rows.add(new HighscoreRow(rank, name, value));
            }
        }
        return rows;
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseLongSafe(String value) {
        try {
            String cleaned = value.replaceAll("[^0-9]", "");
            return cleaned.isBlank() ? 0L : Long.parseLong(cleaned);
        } catch (Exception e) {
            return 0L;
        }
    }
}
