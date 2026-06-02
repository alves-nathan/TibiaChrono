package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.ScrapePort.OnlineCharacterSnapshot;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParserComponentCoverageTest {

    @Test
    void worldDetailParserParsesMetadataAndOnlineCharactersFromFixture() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/fixtures/tibia/world-antica.html"));
        World world = new World("Antica", null, "Europe");

        var result = new WorldDetailPageParser().parse(Jsoup.parse(html), "Antica", world);

        assertThat(result.world()).isEqualTo("Antica");
        assertThat(result.playersOnline()).isEqualTo(2);
        assertThat(result.onlineRecord()).isEqualTo("1050 players (on Jan 01 2020)");
        assertThat(result.creationDate()).isEqualTo(LocalDate.of(2004, 1, 1));
        assertThat(result.transferType()).isEqualTo("Regular");
        assertThat(result.gameWorldType()).isEqualTo("Premium");
        assertThat(world.getPvpType()).isEqualTo("Open PvP");
        assertThat(result.players())
                .extracting(OnlineCharacterSnapshot::name)
                .containsExactly("Knight One", "Druid Two");
        assertThat(result.players())
                .anySatisfy(player -> {
                    assertThat(player.name()).isEqualTo("Knight One");
                    assertThat(player.level()).isEqualTo(400);
                    assertThat(player.vocation()).isEqualTo("Elite Knight");
                });
    }

    @Test
    void worldDetailParserKeepsStableWorldFieldsAndIgnoresInvalidPlayerRows() {
        World world = new World("Antica", "Existing PvP", "Europe");
        world.setCreationDate(LocalDate.of(2000, 1, 1));
        world.setOnlineRecord("Existing record");
        String html = """
                <!doctype html>
                <html><body>
                  <table class="Table1"><tr><td><div class="InnerTableContainer"><table><tbody>
                    <tr><td>Players Online:</td><td>not available</td></tr>
                    <tr><td>Creation Date:</td><td>May 2006</td></tr>
                    <tr><td>Online Record:</td><td>Should not replace</td></tr>
                    <tr><td>PvP Type:</td><td>Open PvP</td></tr>
                    <tr><td>Transfer Type:</td><td>Locked</td></tr>
                    <tr><td>Game World Type:</td><td>Experimental</td></tr>
                  </tbody></table></div></td></tr></table>
                  <table class="Table2"><tr><td><div class="InnerTableContainer"><table>
                    <tr><td>Name [sort]</td><td>Level [sort]</td><td>Vocation [sort]</td></tr>
                    <tr><td>Missing link</td><td>100</td><td>Knight</td></tr>
                    <tr><td><a href="?subtopic=characters&amp;name=Name%20Only">Name Only</a></td><td>unknown</td><td> </td></tr>
                  </table></div></td></tr></table>
                </body></html>
                """;

        var result = new WorldDetailPageParser().parse(Jsoup.parse(html), "Antica", world);

        assertThat(result.playersOnline()).isZero();
        assertThat(result.creationDate()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(result.onlineRecord()).isEqualTo("Existing record");
        assertThat(world.getPvpType()).isEqualTo("Existing PvP");
        assertThat(result.transferType()).isEqualTo("Locked");
        assertThat(result.gameWorldType()).isEqualTo("Experimental");
        assertThat(result.players()).singleElement().satisfies(player -> {
            assertThat(player.name()).isEqualTo("Name Only");
            assertThat(player.level()).isNull();
            assertThat(player.vocation()).isNull();
        });
    }

    @Test
    void characterDateParserHandlesKnownZonesAndEmptyValues() {
        CharacterDetailsDateParser parser = new CharacterDetailsDateParser();

        assertThat(parser.parseTibiaDateTime("May 27 2026, 21:15:33 BRT"))
                .contains(OffsetDateTime.of(2026, 5, 27, 21, 15, 33, 0, ZoneOffset.ofHours(-3)));
        assertThat(parser.parseTibiaDateTime("June 2 2026, 10:20:30 GMT"))
                .contains(OffsetDateTime.of(2026, 6, 2, 10, 20, 30, 0, ZoneOffset.UTC));
        assertThat(parser.parseTibiaDateTime(null)).isEmpty();
        assertThat(parser.parseTibiaDateTime("never logged in.")).isEmpty();
        assertThat(parser.parseTibiaDateTime("invalid date value")).isEmpty();
    }

    @Test
    void characterProfileFieldsParserUsesFallbackRowsAndDetectsNotFoundPages() {
        CharacterProfileFieldsParser parser = new CharacterProfileFieldsParser();
        String html = """
                <html><body>
                  <table class="TableContent">
                    <tr><td>Name:</td><td>Fallback Char</td></tr>
                    <tr><td>Vocation:</td><td>Royal Paladin</td></tr>
                    <tr><td>Level:</td><td>321</td></tr>
                  </table>
                </body></html>
                """;

        Map<String, String> fields = parser.collectCharacterFields(Jsoup.parse(html));

        assertThat(fields)
                .containsEntry("name", "Fallback Char")
                .containsEntry("vocation", "Royal Paladin")
                .containsEntry("level", "321");
        assertThat(parser.isCharacterNotFound(Jsoup.parse("Could not find character"))).isTrue();
        assertThat(parser.isCharacterNotFound(Jsoup.parse("Character does not exist"))).isTrue();
        assertThat(parser.isCharacterNotFound(Jsoup.parse("Character profile"))).isFalse();
    }

    @Test
    void characterDetailsValueParserNormalizesFallbacksFormerNamesNumbersAndDates() {
        CharacterDetailsValueParser parser = new CharacterDetailsValueParser(new CharacterDetailsDateParser());

        var details = parser.toCharacterDetails(Map.of(
                "former names", " First Old , , Second Old ",
                "sex", "female character",
                "vocation", "Elder Druid",
                "level", "unknown",
                "achievement points", "12,345",
                "residence", "Carlin",
                "last login", "never logged in.",
                "account status", "Free Account",
                "created", "April 3 2020, 08:00:00 UTC",
                "world", "Bona"
        ), "Requested Char");

        assertThat(details.currentName()).isEqualTo("Requested Char");
        assertThat(details.formerNames()).containsExactly("First Old", "Second Old");
        assertThat(details.sex()).isEqualTo(CharacterEntity.Sex.female);
        assertThat(details.level()).isNull();
        assertThat(details.achievementPoints()).isEqualTo(12345);
        assertThat(details.lastLogin()).isNull();
        assertThat(details.creationDate()).isEqualTo(Instant.parse("2020-04-03T08:00:00Z"));
        assertThat(details.world()).isEqualTo("Bona");
    }

    @Test
    void guildListParserDeduplicatesGuildLinksAndMarksDisbandedRowsInactive() {
        String html = """
                <html><body><table>
                  <tr><td><a href="?subtopic=guilds&amp;GuildName=Raw%20Raw">Raw Raw</a></td><td>Neutral guild</td></tr>
                  <tr><td><a href="?subtopic=guilds&amp;GuildName=Raw%20Raw">Raw Raw duplicate</a></td><td>Ignored duplicate</td></tr>
                  <tr><td><a href="?subtopic=guilds&amp;GuildName=Retired%20Guild">Retired Guild</a></td><td>This guild has disbanded.</td></tr>
                </table></body></html>
                """;

        var result = new GuildListPageParser().parse(Jsoup.parse(html), "Antica");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(item -> item.name()).containsExactly("Raw Raw", "Retired Guild");
        assertThat(result).anySatisfy(item -> {
            assertThat(item.name()).isEqualTo("Raw Raw");
            assertThat(item.worldName()).isEqualTo("Antica");
            assertThat(item.active()).isTrue();
            assertThat(item.description()).contains("Neutral guild");
        });
        assertThat(result).anySatisfy(item -> {
            assertThat(item.name()).isEqualTo("Retired Guild");
            assertThat(item.active()).isFalse();
        });
    }

    @Test
    void guildDetailSummaryParserExtractsTextFallbacksCountsAndLogo() {
        String html = """
                <html><body>
                  <div class="BoxContent">
                    Guild Information Moon Guard World: Secura Guild Description: Helpers
                    Founded: May 4 2020 Members: 12 members 3 online
                  </div>
                  <table>
                    <tr><td>Homepage:</td><td>https://moon.test</td></tr>
                  </table>
                  <img src="/images/global/header/headline-guilds.gif">
                  <img src="/guildlogos/moon.gif">
                </body></html>
                """;

        var summary = new GuildDetailSummaryParser().parse(Jsoup.parse(html, "https://www.tibia.com/"), "");

        assertThat(summary.name()).isEqualTo("Moon Guard");
        assertThat(summary.world()).isEqualTo("Secura");
        assertThat(summary.description()).isEqualTo("Helpers");
        assertThat(summary.homepage()).isEqualTo("https://moon.test");
        assertThat(summary.foundedAt()).isEqualTo(LocalDate.of(2020, 5, 4));
        assertThat(summary.memberCount()).isEqualTo(12);
        assertThat(summary.onlineCount()).isEqualTo(3);
        assertThat(summary.logoUrl()).endsWith("/guildlogos/moon.gif");
        assertThat(summary.rawHash()).hasSize(64);
    }
}
