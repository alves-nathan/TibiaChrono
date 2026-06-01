package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.GuildScrapePort.Invite;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.extractCharacterName;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.findJoiningDate;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.firstCharacterLink;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.normalize;

@Component
public class GuildInviteTableParser {
    List<Invite> parse(Document doc) {
        List<Invite> invites = new ArrayList<>();
        for (Element row : doc.select("tr")) {
            String rowText = row.text().toLowerCase(Locale.ROOT);
            if (!rowText.contains("invite") && !rowText.contains("invited")) continue;
            Element characterLink = firstCharacterLink(row);
            if (characterLink == null) continue;
            invites.add(new Invite(normalize(extractCharacterName(characterLink)), findJoiningDate(List.of(row.text())).orElse(null)));
        }
        return invites;
    }
}
