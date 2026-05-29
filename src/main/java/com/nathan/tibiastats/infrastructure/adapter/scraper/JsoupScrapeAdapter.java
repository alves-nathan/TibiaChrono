package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.ScrapePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class JsoupScrapeAdapter implements ScrapePort {
    private final TibiaWorldHttpClient httpClient;
    private final WorldOverviewPageParser overviewParser;
    private final WorldDetailPageParser detailParser;
    private final FormerCharacterNamePageParser formerNameParser;

    public JsoupScrapeAdapter() {
        this(
                new TibiaWorldHttpClient(),
                new WorldOverviewPageParser(),
                new WorldDetailPageParser(),
                new FormerCharacterNamePageParser()
        );
    }

    @Autowired
    JsoupScrapeAdapter(
            TibiaWorldHttpClient httpClient,
            WorldOverviewPageParser overviewParser,
            WorldDetailPageParser detailParser,
            FormerCharacterNamePageParser formerNameParser
    ) {
        this.httpClient = httpClient;
        this.overviewParser = overviewParser;
        this.detailParser = detailParser;
        this.formerNameParser = formerNameParser;
    }

    @Override
    public List<WorldSummary> fetchWorldsOverview() {
        try {
            return overviewParser.parse(httpClient.fetchWorldsOverviewDocument());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    List<WorldSummary> parseWorldsOverviewHtml(String html, String sourceUrl) {
        return overviewParser.parseHtml(html, sourceUrl);
    }

    @Override
    public WorldOnline fetchWorldPage(String worldName, World world) {
        try {
            return detailParser.parse(httpClient.fetchWorldPageDocument(worldName), worldName, world);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getFormerName(String name) {
        try {
            return formerNameParser.parse(httpClient.fetchCharacterDocument(name), name);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
