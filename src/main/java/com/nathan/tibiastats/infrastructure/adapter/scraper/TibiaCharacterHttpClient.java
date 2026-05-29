package com.nathan.tibiastats.infrastructure.adapter.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class TibiaCharacterHttpClient {
    private static final String CHARACTER_PAGE_URL_TEMPLATE =
            "https://www.tibia.com/community/?subtopic=characters&name=%s";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; TibiaChronoBot/1.0; +https://localhost)";
    private static final int TIMEOUT_MS = 15_000;

    public Document fetchCharacterDetailsDocument(String characterName) throws IOException {
        String url = CHARACTER_PAGE_URL_TEMPLATE.formatted(URLEncoder.encode(characterName, StandardCharsets.UTF_8));
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();
    }
}
