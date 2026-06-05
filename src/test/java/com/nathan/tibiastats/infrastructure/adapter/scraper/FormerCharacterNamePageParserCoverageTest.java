package com.nathan.tibiastats.infrastructure.adapter.scraper;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormerCharacterNamePageParserCoverageTest {

    @Test
    void parseFallsBackToRequestedNameWhenFormerNamesLabelIsAbsent() {
        FormerCharacterNamePageParser parser = new FormerCharacterNamePageParser();

        String normalizedName = parser.parse(
                Jsoup.parse("<html><body><table class='TableContent'><tr><td>Name:</td><td>Current</td></tr></table></body></html>"),
                " Current   Name (traded) "
        );

        assertThat(normalizedName).isEqualTo("Current Name");
    }

    @Test
    void parseNormalizesFormerNamesCsvFromTibiaTableRow() {
        FormerCharacterNamePageParser parser = new FormerCharacterNamePageParser();
        String html = """
                <html><body>
                  <table class="TableContent">
                    <tr><td>Character Name:</td><td>Current Name</td></tr>
                    <tr><td>Former Names:</td><td> Old Name (traded), Another&nbsp;&nbsp;Name , </td></tr>
                  </table>
                </body></html>
                """;

        String formerNames = parser.parse(Jsoup.parse(html), "Current Name");

        assertThat(formerNames).isEqualTo("Old Name,Another Name");
    }
}
