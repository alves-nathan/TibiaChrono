package com.nathan.tibiastats.infrastructure.adapter.scraper;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ScraperBatch58RemainingCoverageTest {
    @Test
    void guildDetailSummaryParserCoversTableValuesValidLogoLabeledCountsAndLinkFallbackName() {
        String html = """
                <html><body>
                  <h1>Guild Information Guilds World:</h1>
                  <a href="?subtopic=guilds&amp;GuildName=Raw%20Raw"></a>
                  <img src="/guildlogos/rawraw.gif">
                  <table>
                    <tr><td>World:</td><td>Antica</td></tr>
                    <tr><td>Guild Description:</td><td>Brazilian helpers</td></tr>
                    <tr><td>Homepage:</td><td>https://rawraw.example</td></tr>
                    <tr><td>Founded:</td><td>June 5 2026</td></tr>
                  </table>
                  <div>Members: 7 Online: 2</div>
                </body></html>
                """;

        GuildDetailSummaryParser.Summary summary =
                new GuildDetailSummaryParser().parse(Jsoup.parse(html, "https://www.tibia.com/community/"), null);

        assertThat(summary.name()).isEqualTo("Raw Raw");
        assertThat(summary.world()).isEqualTo("Antica");
        assertThat(summary.description()).isEqualTo("Brazilian helpers");
        assertThat(summary.homepage()).isEqualTo("https://rawraw.example");
        assertThat(summary.logoUrl()).endsWith("/guildlogos/rawraw.gif");
        assertThat(summary.foundedAt()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(summary.memberCount()).isEqualTo(7);
        assertThat(summary.onlineCount()).isEqualTo(2);
        assertThat(summary.rawHash()).isNotBlank();
    }

    @Test
    void guildMemberParserRejectsDateStatusLevelAndHeaderOnlyTitles() {
        String html = """
                <table>
                  <tr><td>Member Rank:</td></tr>
                  <tr>
                    <td><a href="?subtopic=characters&amp;name=Plain%20Hero">Plain Hero</a> Knight 100 June 5 2026 Offline</td>
                    <td>Knight</td><td>100</td><td>June 5 2026</td><td>Offline</td>
                  </tr>
                  <tr>
                    <td><a href="?subtopic=characters&amp;name=Name%20Only">Name Only</a></td>
                    <td>Sorcerer</td><td>50</td><td>June 6 2026</td><td>Online</td>
                  </tr>
                </table>
                """;

        var members = new GuildMemberTableParser().parse(Jsoup.parse(html));

        assertThat(members).hasSize(2);
        assertThat(members.get(0).title()).isEqualTo("June");
        assertThat(members.get(0).rankName()).isEqualTo("Member");
        assertThat(members.get(0).online()).isTrue();
        assertThat(members.get(1).title()).isNull();
        assertThat(members.get(1).online()).isTrue();
    }
}
