package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.CharacterNameNormalizer;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
public class FormerCharacterNamePageParser {
    public String parse(Document doc, String name) {
        Elements rows = doc.select("table.TableContent tr");
        if (!doc.text().contains("Former Names:")) {
            return CharacterNameNormalizer.normalize(name);
        }
        for (Element line : rows) {
            if (line.text().contains("Former Names:")) {
                return CharacterNameNormalizer.normalizeCsv(line.select("td:last-child").text());
            }
        }
        return CharacterNameNormalizer.normalize(name);
    }
}
