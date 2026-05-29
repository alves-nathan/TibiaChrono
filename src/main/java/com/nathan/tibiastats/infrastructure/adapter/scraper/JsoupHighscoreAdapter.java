package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.port.HighscorePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class JsoupHighscoreAdapter implements HighscorePort {
    private final TibiaHighscoreHttpClient httpClient;
    private final HighscorePageParser parser;

    public JsoupHighscoreAdapter() {
        this(new TibiaHighscoreHttpClient(), new HighscorePageParser());
    }

    @Autowired
    JsoupHighscoreAdapter(TibiaHighscoreHttpClient httpClient, HighscorePageParser parser) {
        this.httpClient = httpClient;
        this.parser = parser;
    }

    @Override
    public List<HighscoreRow> fetchHighscores(String world, StatCategory category, int vocationId, int page) {
        try {
            var highscorePage = httpClient.fetchHighscoresPage(world, category, vocationId, page);
            return parser.parseHtml(highscorePage.html(), highscorePage.sourceUrl());
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch highscores: world=" + world
                    + ", category=" + category
                    + ", vocationId=" + vocationId
                    + ", page=" + page, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching highscores: world=" + world
                    + ", category=" + category
                    + ", vocationId=" + vocationId
                    + ", page=" + page, e);
        }
    }

    List<HighscoreRow> parseHighscoresHtml(String html, String sourceUrl) {
        return parser.parseHtml(html, sourceUrl);
    }
}
