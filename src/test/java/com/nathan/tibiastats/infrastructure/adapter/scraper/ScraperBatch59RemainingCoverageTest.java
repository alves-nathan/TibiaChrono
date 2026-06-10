package com.nathan.tibiastats.infrastructure.adapter.scraper;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScraperBatch59RemainingCoverageTest {
    @Test
    void worldOverviewParserCoversDocumentEntryShortRowsHeaderAndAdditionalInfo() {
        String html = """
                <div class="TableContentContainer">
                  <table class="TableContent">
                    <tr><td>Too short</td><td>1</td></tr>
                    <tr><td>World</td><td>Players</td><td>Location</td><td>PvP</td></tr>
                    <tr>
                      <td>Antica</td><td>321</td><td>EU</td><td>Open PvP</td>
                      <td><img alt="blocked"> blocked transfer experimental</td>
                    </tr>
                  </table>
                </div>
                """;

        var worlds = new WorldOverviewPageParser().parse(Jsoup.parse(html));

        assertThat(worlds).singleElement().satisfies(world -> {
            assertThat(world.name()).isEqualTo("Antica");
            assertThat(world.playersOnline()).isEqualTo(321);
            assertThat(world.transferType()).containsIgnoringCase("blocked");
        });
    }

    @Test
    void guildSummaryUsesLinkFallbackSkipsPlaceholderLogoAndParsesCountsBeforeWords() {
        String html = """
                <html><body>
                  <h1>Guild Information Guilds World:</h1>
                  <a href="?subtopic=guilds&amp;GuildName=Fallback%20Guild"></a>
                  <img src="/images/strings/headline.gif">
                  <div>
                    World: Antica
                    Guild Description: Neutral group
                    Founded: June 5 2026
                    There are 12 members in this guild. 4 online.
                  </div>
                </body></html>
                """;

        var summary = new GuildDetailSummaryParser()
                .parse(Jsoup.parse(html, "https://www.tibia.com/community/"), null);

        assertThat(summary.name()).isEqualTo("Fallback Guild");
        assertThat(summary.world()).isEqualTo("Antica");
        assertThat(summary.logoUrl()).isNull();
        assertThat(summary.memberCount()).isEqualTo(12);
        assertThat(summary.onlineCount()).isEqualTo(4);
    }

    @Test
    void guildMemberParserCoversLooseCharacterLinkWithoutTableCells() {
        String html = """
                <table>
                  <tr><td>Member Rank:</td></tr>
                  <tr><a href="?subtopic=characters&amp;name=Loose%20Hero">Loose Hero</a></tr>
                </table>
                """;

        var members = new GuildMemberTableParser().parse(Jsoup.parse(html));

        assertThat(members).isEmpty();
    }

    @Test
    void guildParsingSupportAndCharacterDateHelpersCoverRemainingDefensiveBranches() throws Throwable {
        assertThat(GuildPageParsingSupport.extractCharacterName(null)).isEmpty();
        assertThat(GuildPageParsingSupport.parseDate(null)).isNull();
        assertThat(GuildPageParsingSupport.parseDate("2026-06-05")).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(GuildPageParsingSupport.parseDate("not a date")).isNull();
        assertThat(GuildPageParsingSupport.firstNonBlank(null, " ", "Fallback")).isEqualTo("Fallback");
        assertThat(GuildPageParsingSupport.firstNonBlankOrNull(null, " ")).isNull();
        assertThat(GuildPageParsingSupport.normalize(null)).isEmpty();
        assertThat(GuildPageParsingSupport.blankToNull(" ")).isNull();
        assertThat(GuildPageParsingSupport.removeWholeValue(null, "Hero")).isNull();
        assertThat(GuildPageParsingSupport.removeWholeValue("Hero Knight", "Hero")).isEqualTo("Knight");
        assertThat(GuildPageParsingSupport.sha256(null)).hasSize(64);

        CharacterDetailsDateParser dateParser = new CharacterDetailsDateParser();
        assertThat(dateParser.parseTibiaDateTime("never logged in.")).isEmpty();
        assertThat(dateParser.parseTibiaDateTime("short")).isEmpty();
        assertThat(dateParser.parseTibiaDateTime("June 5 2026, 10:15:30 BRT"))
                .hasValueSatisfying(value -> assertThat(value.getOffset()).isEqualTo(ZoneOffset.ofHours(-3)));
        assertThat(invoke(dateParser, "zoneOffsetFor", new Class<?>[]{String.class}, new Object[]{null}))
                .isEqualTo(ZoneOffset.UTC);

        CharacterProfileFieldsParser fieldsParser = new CharacterProfileFieldsParser();
        assertThat(fieldsParser.collectCharacterFields(Jsoup.parse("""
                <table class="TableContent">
                  <tr><td>Level:</td><td>100</td></tr>
                  <tr><td>Level:</td><td>200</td></tr>
                  <tr><td>Empty:</td><td> </td></tr>
                </table>
                """))).containsEntry("level", "100");
        assertThat(invoke(fieldsParser, "normalizeLabel", new Class<?>[]{String.class}, new Object[]{null}))
                .isEqualTo("");
        assertThat(invoke(fieldsParser, "normalizeValue", new Class<?>[]{String.class}, new Object[]{null}))
                .isEqualTo("");
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) throws Throwable {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }
}
