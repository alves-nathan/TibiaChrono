package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.HighscorePort.HighscoreRow;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupHighscoreAdapterCharacterizationTest {

    @Test
    void parsesHighscoreRowsFromFixtureWithoutNetworkAccess() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/fixtures/tibia/highscores-experience.html"));

        List<HighscoreRow> rows = new JsoupHighscoreAdapter()
                .parseHighscoresHtml(html, "https://www.tibia.com/community/?subtopic=highscores");

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).isEqualTo(new HighscoreRow(1, "Bubble", 12_345_678_901L));
        assertThat(rows.get(1)).isEqualTo(new HighscoreRow(2, "Lord Paulistinha", 11_111_111_111L));
        assertThat(rows.get(2)).isEqualTo(new HighscoreRow(3, "Traded Char (traded)", 10_000_000_000L));
    }
}
