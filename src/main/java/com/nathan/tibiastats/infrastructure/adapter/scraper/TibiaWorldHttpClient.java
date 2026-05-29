package com.nathan.tibiastats.infrastructure.adapter.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class TibiaWorldHttpClient {
    static final String WORLDS_URL = "https://www.tibia.com/community/?subtopic=worlds";
    private static final String CHARACTER_URL = "https://www.tibia.com/community/?name=%s";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; TibiaChronoBot/1.0; +https://localhost)";
    private static final int TIMEOUT_MS = 15_000;

    public Document fetchWorldsOverviewDocument() throws IOException {
        return Jsoup.connect(WORLDS_URL)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();
    }

    public Document fetchWorldPageDocument(String worldName) throws IOException {
        String url = WORLDS_URL + "&world=" + encode(worldName);
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();
    }

    public Document fetchCharacterDocument(String name) throws IOException {
        return Jsoup.connect(CHARACTER_URL.formatted(encode(name)))
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
