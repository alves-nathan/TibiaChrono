package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.GuildScrapePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class JsoupGuildAdapter implements GuildScrapePort {
    private final TibiaGuildHttpClient httpClient;
    private final GuildListPageParser listParser;
    private final GuildDetailPageParser detailParser;

    public JsoupGuildAdapter() {
        this(new TibiaGuildHttpClient(), new GuildListPageParser(), new GuildDetailPageParser());
    }

    @Autowired
    JsoupGuildAdapter(
            TibiaGuildHttpClient httpClient,
            GuildListPageParser listParser,
            GuildDetailPageParser detailParser
    ) {
        this.httpClient = httpClient;
        this.listParser = listParser;
        this.detailParser = detailParser;
    }

    @Override
    public List<GuildListItem> fetchGuildList(String worldName) {
        try {
            return listParser.parse(httpClient.fetchGuildListDocument(worldName), worldName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch guild list for world '" + worldName + "'", e);
        }
    }

    @Override
    public GuildDetail fetchGuildDetail(String guildName) {
        try {
            return detailParser.parse(httpClient.fetchGuildDetailDocument(guildName), guildName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch guild detail for '" + guildName + "'", e);
        }
    }

    GuildDetail parseGuildDetailHtml(String html, String guildName) {
        return detailParser.parseHtml(html, guildName);
    }
}
