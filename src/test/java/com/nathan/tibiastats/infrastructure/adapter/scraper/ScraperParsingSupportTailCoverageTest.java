package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScraperParsingSupportTailCoverageTest {
    @Test
    void worldPageParsingSupportCoversTransferWorldTypeAndNumberFallbacks() {
        Element imageCell = Jsoup.parse("""
                <table><tr><td>
                  <img title="Premium" alt="Experimental">
                </td></tr></table>
                """).selectFirst("td");

        assertThat(WorldPageParsingSupport.cellTextIncludingImageLabels(imageCell)).isEqualTo("Premium Experimental");

        assertThat(WorldPageParsingSupport.extractTransferType("   ")).isEmpty();
        assertThat(WorldPageParsingSupport.extractTransferType("world transfer blocked")).contains("Blocked");
        assertThat(WorldPageParsingSupport.extractTransferType("World is locked")).contains("Locked");
        assertThat(WorldPageParsingSupport.extractTransferType("Regular transfer allowed")).contains("Regular transfer allowed");
        assertThat(WorldPageParsingSupport.extractTransferType("regular world")).isEmpty();

        assertThat(WorldPageParsingSupport.extractGameWorldType(null)).isEmpty();
        assertThat(WorldPageParsingSupport.extractGameWorldType("Premium PvP")).contains("Premium");
        assertThat(WorldPageParsingSupport.extractGameWorldType("Experimental")).contains("Experimental");
        assertThat(WorldPageParsingSupport.extractGameWorldType("Restricted")).contains("Restricted");
        assertThat(WorldPageParsingSupport.extractGameWorldType("Tournament")).contains("Tournament");
        assertThat(WorldPageParsingSupport.extractGameWorldType("Regular")).isEmpty();

        assertThat(WorldPageParsingSupport.parseIntegerOrNull(null)).isNull();
        assertThat(WorldPageParsingSupport.parseIntegerOrNull("not available")).isNull();
        assertThat(WorldPageParsingSupport.parseIntegerOrNull("999999999999999999999999")).isNull();
        assertThat(WorldPageParsingSupport.parseIntSafe("not available")).isZero();
    }

    @Test
    void guildPageParsingSupportCoversFallbacksAndEdgeHelpers() {
        Element guildLinkWithoutQuery = Jsoup.parse("<a href=\"?subtopic=guilds\">Displayed Guild</a>").selectFirst("a");
        Element characterLinkWithoutQuery = Jsoup.parse("<a href=\"?subtopic=characters\">Displayed Character</a>").selectFirst("a");
        Element rowWithoutCharacter = Jsoup.parse("<table><tr><td>No character link</td></tr></table>").selectFirst("tr");

        assertThat(GuildPageParsingSupport.extractGuildName(guildLinkWithoutQuery)).isEqualTo("Displayed Guild");
        assertThat(GuildPageParsingSupport.extractCharacterName(null)).isEmpty();
        assertThat(GuildPageParsingSupport.extractCharacterName(characterLinkWithoutQuery)).isEqualTo("Displayed Character");
        assertThat(GuildPageParsingSupport.firstCharacterLink(rowWithoutCharacter)).isNull();

        assertThat(GuildPageParsingSupport.parseDate(null)).isNull();
        assertThat(GuildPageParsingSupport.findJoiningDate(List.of("not a date", "June 5 2026")))
                .contains(LocalDate.of(2026, 6, 5));
        assertThat(GuildPageParsingSupport.findVocation(List.of("unknown", "Knight"))).contains("Knight");
        assertThat(GuildPageParsingSupport.findLevel(List.of("May 1 2026", "450"))).contains(450);

        assertThat(GuildPageParsingSupport.firstNonBlank(" ", null, "\t")).isEmpty();
        assertThat(GuildPageParsingSupport.firstNonBlankOrNull(" ", null)).isNull();
        assertThat(GuildPageParsingSupport.blankToNull(" ")).isNull();
        assertThat(GuildPageParsingSupport.removeWholeValue(null, "rank")).isNull();
        assertThat(GuildPageParsingSupport.removeWholeValue("Rank", "Rank")).isEmpty();
        assertThat(GuildPageParsingSupport.removeWholeValue("Leader Rank Member", "Rank")).isEqualTo("Leader Member");
        assertThat(GuildPageParsingSupport.sha256("guild")).hasSize(64);
    }

    @Test
    void guildDetailSummaryParserUsesGuildLinkFallbackCountsBeforeWordsAndSkipsInvalidLogos() {
        String html = """
                <html><body>
                  <div class="BoxContent">Navigation</div>
                  <a href="?subtopic=guilds&amp;GuildName=Fallback%20Guild">Wrong Text</a>
                  <table>
                    <tr><td>Homepage:</td><td>https://fallback.test</td></tr>
                  </table>
                  <div>
                    World: Antica Guild Description: Fellowship
                    Founded: June 5 2026 12 members 4 online
                  </div>
                  <img src="/images/global/header/headline-guilds.gif">
                  <img src="/images/global/strings/headline.gif">
                  <img src="/images/global/buttons/button.gif">
                </body></html>
                """;

        var summary = new GuildDetailSummaryParser().parse(Jsoup.parse(html, "https://www.tibia.com/"), "");

        assertThat(summary.name()).isEqualTo("Fallback Guild");
        assertThat(summary.world()).isEqualTo("Antica");
        assertThat(summary.description()).isEqualTo("Fellowship");
        assertThat(summary.homepage()).isEqualTo("https://fallback.test");
        assertThat(summary.foundedAt()).isNull();
        assertThat(summary.memberCount()).isEqualTo(12);
        assertThat(summary.onlineCount()).isEqualTo(4);
        assertThat(summary.logoUrl()).isNull();
        assertThat(summary.rawHash()).hasSize(64);
    }

    @Test
    void characterDetailsValueParserCoversDefaultConstructorNullsUnknownSexAndOverflowingNumbers() {
        CharacterDetailsValueParser parser = new CharacterDetailsValueParser();

        var emptyDetails = parser.toCharacterDetails(Map.of(), "Requested Character");
        assertThat(emptyDetails.currentName()).isEqualTo("Requested Character");
        assertThat(emptyDetails.formerNames()).isEmpty();
        assertThat(emptyDetails.sex()).isNull();
        assertThat(emptyDetails.level()).isNull();
        assertThat(emptyDetails.achievementPoints()).isNull();

        Map<String, String> fields = new HashMap<>();
        fields.put("name", "Official Character");
        fields.put("sex", "male character");
        fields.put("former names", "   ");
        fields.put("level", "999999999999999999999999");
        fields.put("achievement points", "999999999999999999999999");

        var maleDetails = parser.toCharacterDetails(fields, "Requested Character");
        assertThat(maleDetails.currentName()).isEqualTo("Official Character");
        assertThat(maleDetails.sex()).isEqualTo(CharacterEntity.Sex.male);
        assertThat(maleDetails.level()).isNull();
        assertThat(maleDetails.achievementPoints()).isNull();

        var unknownSex = parser.toCharacterDetails(Map.of("sex", "unknown"), "Requested Character");
        assertThat(unknownSex.sex()).isNull();
    }
}
