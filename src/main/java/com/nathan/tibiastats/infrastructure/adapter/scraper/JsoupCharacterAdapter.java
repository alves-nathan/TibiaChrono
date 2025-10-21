package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.CharacterDetailPort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JsoupCharacterAdapter implements CharacterDetailPort {

    // Example URL pattern for character info page (you must confirm)
    private static final String CHARACTER_PAGE_URL_TEMPLATE =
            "https://www.tibia.com/community/?subtopic=characters&name=%s&world=%s";

    @Override
    public NameDetails fetchNameDetails(String worldName, String characterName) {
        String url = String.format(CHARACTER_PAGE_URL_TEMPLATE,
                encode(characterName), encode(worldName));
        try {
            Document doc = Jsoup.connect(url).get();

            // Assume page shows current name (possibly in a header) and a section “Former Names: X, Y, Z”
            // You need to locate the element containing former names in the HTML.
            // Here's a placeholder approach:

            // 1. Current name is the same as `characterName` requested (or parsed from page)
            String current = characterName;

            List<String> formerList = new ArrayList<>();
            // Try to find a label or table row containing “Former names” or “Former Name(s)”
            Elements labels = doc.select("td:matchesOwn(Former Names:?)");
            for (Element label : labels) {
                // Possibly the next sibling or nearby element has the list
                Element sibling = label.nextElementSibling();
                if (sibling != null) {
                    String text = sibling.text();
                    // assume comma-separated
                    String[] parts = text.split(",\\s*");
                    for (String nm : parts) {
                        if (!nm.isBlank()) {
                            formerList.add(nm.trim());
                        }
                    }
                }
            }

            return new NameDetails(current, formerList);
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch character page " + url, e);
        }
    }

    private String encode(String s) {
        return s.replace(" ", "+");  // simplistic; better to URL-encode
    }
}
