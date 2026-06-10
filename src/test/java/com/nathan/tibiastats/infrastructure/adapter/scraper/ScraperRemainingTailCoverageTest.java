package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.GuildScrapePort;
import com.nathan.tibiastats.domain.port.HighscorePort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScraperRemainingTailCoverageTest {
    @Test
    void worldParsingSupportCoversRemainingHelpersAndSuccessfulNumericParsing() {
        Element cell = Jsoup.parse("""
                <table><tr><td>Base
                  <img title=" " alt="Alt Label">
                  <img title="Title Label" alt="">
                </td></tr></table>
                """).selectFirst("td");
        Element row = Jsoup.parse("<table><tr><td>First</td><td> Last </td></tr></table>").selectFirst("tr");

        assertThat(WorldPageParsingSupport.cellTextIncludingImageLabels(cell)).isEqualTo("Base Alt Label Title Label");
        assertThat(WorldPageParsingSupport.lastCellText(row)).isEqualTo("Last");
        assertThat(WorldPageParsingSupport.blankToNull(null)).isNull();
        assertThat(WorldPageParsingSupport.blankToNull("  value  ")).isEqualTo("value");
        assertThat(WorldPageParsingSupport.parseIntegerOrNull("Level: 123")).isEqualTo(123);
        assertThat(WorldPageParsingSupport.parseIntSafe("Rank #42")).isEqualTo(42);
        assertThat(WorldPageParsingSupport.extractTransferType("world closed")).contains("Blocked");
    }

    @Test
    void characterProfileAndDetailsParsersCoverMissingAndEmptyFieldPaths() {
        CharacterDetailsPageParser pageParser = new CharacterDetailsPageParser();

        assertThat(pageParser.parseHtml(null, "Fallback Character"))
                .hasValueSatisfying(details -> assertThat(details.currentName()).isEqualTo("Fallback Character"));
        assertThat(pageParser.parseHtml("There is no character with this name.", "Missing Character")).isEmpty();

        CharacterProfileFieldsParser fieldsParser = new CharacterProfileFieldsParser();
        var labelFields = fieldsParser.collectCharacterFields(Jsoup.parse("""
                <table>
                  <tr><td class="LabelV">Lonely:</td></tr>
                  <tr><td class="LabelV">Name:</td><td>Real&nbsp;Name</td></tr>
                  <tr><td class="LabelV">Level:</td><td>321</td></tr>
                </table>
                """));
        assertThat(labelFields).containsEntry("name", "Real Name").containsEntry("level", "321");

        var fallbackFields = fieldsParser.collectCharacterFields(Jsoup.parse("""
                <table class="TableContent">
                  <tr><td> </td><td>ignored</td></tr>
                  <tr><td>Vocation:</td><td>Elite&nbsp;Knight</td></tr>
                </table>
                """));
        assertThat(fallbackFields).containsEntry("vocation", "Elite Knight");
    }

    @Test
    void highscoreParserKeepsOnlyRowsWithPositiveRankAndNameAndSafelyParsesInvalidValues() {
        String html = """
                <table class="TableContent">
                  <tr><td>Rank</td><td>Name</td><td>Value</td></tr>
                  <tr><td>not ranked</td><td>Ignored</td><td>100</td></tr>
                  <tr><td>1</td><td> </td><td>200</td></tr>
                  <tr><td>2</td><td>Valid Hero</td><td>999999999999999999999999</td></tr>
                  <tr><td>3</td><td>Normal Hero</td><td>12,345</td></tr>
                </table>
                """;

        List<HighscorePort.HighscoreRow> rows = new HighscorePageParser().parseHtml(html, "https://example.test");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).rank()).isEqualTo(2);
        assertThat(rows.get(0).name()).isEqualTo("Valid Hero");
        assertThat(rows.get(0).value()).isZero();
        assertThat(rows.get(1).rank()).isEqualTo(3);
        assertThat(rows.get(1).value()).isEqualTo(12345L);
    }

    @Test
    void guildDetailPageParserUsesMemberFallbackCountsWhenSummaryDoesNotProvideCounters() {
        String html = """
                <html><body>
                  <div class="BoxContent">Guild Information Raw Raw World: Antica Guild Description: Helpers</div>
                  <table class="TableContent">
                    <tr><td>Leader Rank:</td></tr>
                    <tr>
                      <td><a href="?subtopic=characters&amp;name=Online%20Hero">Online Hero</a></td>
                      <td>Knight</td><td>50</td><td>June 5 2026</td><td>Online</td>
                    </tr>
                  </table>
                </body></html>
                """;

        GuildScrapePort.GuildDetail detail = new GuildDetailPageParser().parseHtml(html, "Raw Raw");

        assertThat(detail.name()).isEqualTo("Raw Raw");
        assertThat(detail.worldName()).isEqualTo("Antica");
        assertThat(detail.memberCount()).isNotNull();
        assertThat(detail.onlineCount()).isNotNull();
        assertThat(detail.members()).singleElement().satisfies(member -> {
            assertThat(member.name()).isEqualTo("Online Hero");
            assertThat(member.rankName()).isEqualTo("Leader");
            assertThat(member.online()).isTrue();
        });
    }

    @Test
    void guildMemberParserCoversRankCleanupTitleSanitizationAndIgnoredRows() {
        String html = """
                <table>
                  <tr><td>Guild Members</td></tr>
                  <tr><td>Name and Title</td><td>Vocation</td><td>Level</td><td>Joining Date</td><td>Status</td></tr>
                  <tr><td>A</td></tr>
                  <tr><td>June 5 2026</td></tr>
                  <tr><td>Offline Rank</td></tr>
                  <tr>Loose text without cells</tr>
                  <tr><td>Vice Leader Rank:</td></tr>
                  <tr>
                    <td><a href="?subtopic=characters&amp;name=Ranked%20Hero">Ranked Hero</a></td>
                    <td>Druid</td><td>20</td><td>June 5 2026</td><td>Offline</td>
                  </tr>
                  <tr>
                    <td><span><a href="?subtopic=characters&amp;name=Titled%20Hero">Titled Hero</a></span>
                        The Strategist 300 Royal Paladin May 6 2026 Online</td>
                    <td>Royal Paladin</td><td>300</td><td>May 6 2026</td><td>Online</td>
                  </tr>
                </table>
                """;

        List<GuildScrapePort.Member> members = new GuildMemberTableParser().parse(Jsoup.parse(html));

        assertThat(members).hasSize(2);
        assertThat(members.get(0).name()).isEqualTo("Ranked Hero");
        assertThat(members.get(0).rankName()).isEqualTo("Vice Leader");
        assertThat(members.get(0).title()).isNull();
        assertThat(members.get(0).joinedOn()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(members.get(0).joinedOn()).isEqualTo(LocalDate.of(2026, 6, 5));

        assertThat(members.get(1).name()).isEqualTo("Titled Hero");
        assertThat(members.get(1).title()).contains("The Strategist");
        assertThat(members.get(1).vocation()).isEqualTo("Royal Paladin");
        assertThat(members.get(1).level()).isEqualTo(300);
        assertThat(members.get(1).online()).isTrue();
    }

    @Test
    void characterDetailsValueParserKeepsExplicitValuesAndFemaleSex() {
        var details = new CharacterDetailsValueParser().toCharacterDetails(Map.of(
                "name", "Current Name",
                "former names", "One, Two",
                "sex", "female character",
                "level", "450",
                "achievement points", "1,234",
                "residence", "Thais",
                "account status", "Premium Account",
                "world", "Antica"
        ), "Requested Name");

        assertThat(details.currentName()).isEqualTo("Current Name");
        assertThat(details.formerNames()).containsExactly("One", "Two");
        assertThat(details.sex().name()).isEqualTo("female");
        assertThat(details.level()).isEqualTo(450);
        assertThat(details.achievementPoints()).isEqualTo(1234);
        assertThat(details.residence()).isEqualTo("Thais");
        assertThat(details.accountStatus()).isEqualTo("Premium Account");
        assertThat(details.world()).isEqualTo("Antica");
    }
}
