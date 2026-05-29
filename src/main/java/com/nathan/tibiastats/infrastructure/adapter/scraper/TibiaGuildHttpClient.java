package com.nathan.tibiastats.infrastructure.adapter.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class TibiaGuildHttpClient {
    static final String BASE_URL = "https://www.tibia.com/community/?subtopic=guilds";
    private static final int TIMEOUT_MS = 30_000;
    private static final String USER_AGENT = "TibiaChrono/1.0 (+https://github.com/nathan)";

    public Document fetchGuildListDocument(String worldName) throws IOException {
        return Jsoup.connect(BASE_URL + "&world=" + encode(worldName))
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();
    }

    public Document fetchGuildDetailDocument(String guildName) throws IOException {
        return Jsoup.connect(BASE_URL + "&page=view&GuildName=" + encode(guildName))
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
