package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.GuildScrapePort.GuildListItem;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.blankToNull;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.extractGuildName;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.normalize;

@Component
public class GuildListPageParser {
    public List<GuildListItem> parse(Document doc, String worldName) {
        Map<String, GuildListItem> items = new LinkedHashMap<>();
        for (Element link : doc.select("a[href*=GuildName]")) {
            String name = normalize(extractGuildName(link));
            if (name.isBlank()) continue;
            String rowText = link.parents().stream()
                    .filter(e -> "tr".equalsIgnoreCase(e.tagName()))
                    .findFirst()
                    .map(Element::text)
                    .orElse("");
            boolean active = !rowText.toLowerCase(Locale.ROOT).contains("disband");
            items.putIfAbsent(
                    name.toLowerCase(Locale.ROOT),
                    new GuildListItem(name, worldName, active, blankToNull(rowText))
            );
        }
        return List.copyOf(items.values());
    }
}
