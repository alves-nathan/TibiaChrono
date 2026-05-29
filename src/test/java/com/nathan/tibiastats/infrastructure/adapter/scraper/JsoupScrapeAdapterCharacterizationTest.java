package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.ScrapePort.WorldSummary;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupScrapeAdapterCharacterizationTest {

    @Test
    void parsesWorldOverviewRowsAndImageLabelsFromFixtureWithoutNetworkAccess() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/fixtures/tibia/worlds-overview.html"));

        List<WorldSummary> worlds = new JsoupScrapeAdapter()
                .parseWorldsOverviewHtml(html, "https://www.tibia.com/community/?subtopic=worlds");

        assertThat(worlds).hasSize(2);
        assertThat(worlds.get(0)).isEqualTo(new WorldSummary(
                "Antica",
                "Optional PvP",
                "Europe",
                123,
                "Blocked",
                "Premium"
        ));
        assertThat(worlds.get(1)).isEqualTo(new WorldSummary(
                "Belobra",
                "Open PvP",
                "South America",
                45,
                "regular transfer",
                null
        ));
    }
}
