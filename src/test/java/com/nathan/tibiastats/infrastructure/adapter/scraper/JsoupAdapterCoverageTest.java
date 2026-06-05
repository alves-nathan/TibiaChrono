package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.CharacterDetailPort.CharacterDetails;
import com.nathan.tibiastats.domain.port.GuildScrapePort.GuildDetail;
import com.nathan.tibiastats.domain.port.GuildScrapePort.GuildListItem;
import com.nathan.tibiastats.domain.port.HighscorePort.HighscoreRow;
import com.nathan.tibiastats.domain.port.ScrapePort.OnlineCharacterSnapshot;
import com.nathan.tibiastats.domain.port.ScrapePort.WorldOnline;
import com.nathan.tibiastats.domain.port.ScrapePort.WorldSummary;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsoupAdapterCoverageTest {

    @Test
    void characterAdapterReturnsNameDetailsFromParsedCharacterDetails() throws Exception {
        TibiaCharacterHttpClient httpClient = mock(TibiaCharacterHttpClient.class);
        CharacterDetailsPageParser parser = mock(CharacterDetailsPageParser.class);
        JsoupCharacterAdapter adapter = new JsoupCharacterAdapter(httpClient, parser);
        Document doc = Jsoup.parse("<html></html>");
        CharacterDetails details = characterDetails("Current Name", List.of("Old Name"));
        when(httpClient.fetchCharacterDetailsDocument("Requested Name")).thenReturn(doc);
        when(parser.parse(doc, "Requested Name")).thenReturn(Optional.of(details));

        var result = adapter.fetchNameDetails("Antica", "Requested Name");

        assertThat(result.currentName()).isEqualTo("Current Name");
        assertThat(result.formerNames()).containsExactly("Old Name");
        verify(httpClient).fetchCharacterDetailsDocument("Requested Name");
    }

    @Test
    void characterAdapterFallsBackToRequestedNameWhenParserReturnsEmpty() throws Exception {
        TibiaCharacterHttpClient httpClient = mock(TibiaCharacterHttpClient.class);
        CharacterDetailsPageParser parser = mock(CharacterDetailsPageParser.class);
        JsoupCharacterAdapter adapter = new JsoupCharacterAdapter(httpClient, parser);
        Document doc = Jsoup.parse("<html></html>");
        when(httpClient.fetchCharacterDetailsDocument("Missing Name")).thenReturn(doc);
        when(parser.parse(doc, "Missing Name")).thenReturn(Optional.empty());

        var result = adapter.fetchNameDetails("Antica", "Missing Name");

        assertThat(result.currentName()).isEqualTo("Missing Name");
        assertThat(result.formerNames()).isEmpty();
    }

    @Test
    void characterAdapterWrapsHttpStatusAndIoFailures() throws Exception {
        TibiaCharacterHttpClient httpClient = mock(TibiaCharacterHttpClient.class);
        CharacterDetailsPageParser parser = mock(CharacterDetailsPageParser.class);
        JsoupCharacterAdapter adapter = new JsoupCharacterAdapter(httpClient, parser);
        when(httpClient.fetchCharacterDetailsDocument("Forbidden"))
                .thenThrow(new HttpStatusException("forbidden", 403, "https://example.test"));
        when(httpClient.fetchCharacterDetailsDocument("Broken"))
                .thenThrow(new IOException("network down"));

        assertThatThrownBy(() -> adapter.fetchCharacterDetails("Forbidden"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("HTTP 403");
        assertThatThrownBy(() -> adapter.fetchCharacterDetails("Broken"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to fetch character details for Broken");
    }

    @Test
    void scrapeAdapterDelegatesOverviewWorldPageAndFormerNameParsing() throws Exception {
        TibiaWorldHttpClient httpClient = mock(TibiaWorldHttpClient.class);
        WorldOverviewPageParser overviewParser = mock(WorldOverviewPageParser.class);
        WorldDetailPageParser detailParser = mock(WorldDetailPageParser.class);
        FormerCharacterNamePageParser formerNameParser = mock(FormerCharacterNamePageParser.class);
        JsoupScrapeAdapter adapter = new JsoupScrapeAdapter(httpClient, overviewParser, detailParser, formerNameParser);
        Document overviewDoc = Jsoup.parse("<html></html>");
        Document worldDoc = Jsoup.parse("<html></html>");
        Document characterDoc = Jsoup.parse("<html></html>");
        World world = new World("Antica", "Optional PvP", "Europe");
        List<WorldSummary> summaries = List.of(new WorldSummary("Antica", "Optional PvP", "Europe", 42, null, null));
        WorldOnline online = new WorldOnline(
                "Antica",
                1,
                List.of(new OnlineCharacterSnapshot("Knight", 300, "Elite Knight")),
                null,
                LocalDate.parse("2020-01-01"),
                null,
                null
        );
        when(httpClient.fetchWorldsOverviewDocument()).thenReturn(overviewDoc);
        when(httpClient.fetchWorldPageDocument("Antica")).thenReturn(worldDoc);
        when(httpClient.fetchCharacterDocument("Old Name")).thenReturn(characterDoc);
        when(overviewParser.parse(overviewDoc)).thenReturn(summaries);
        when(detailParser.parse(worldDoc, "Antica", world)).thenReturn(online);
        when(formerNameParser.parse(characterDoc, "Old Name")).thenReturn("Former Name");

        assertThat(adapter.fetchWorldsOverview()).isSameAs(summaries);
        assertThat(adapter.fetchWorldPage("Antica", world)).isSameAs(online);
        assertThat(adapter.getFormerName("Old Name")).isEqualTo("Former Name");
    }

    @Test
    void scrapeAdapterWrapsIoFailuresFromEveryFetchPath() throws Exception {
        TibiaWorldHttpClient httpClient = mock(TibiaWorldHttpClient.class);
        JsoupScrapeAdapter adapter = new JsoupScrapeAdapter(
                httpClient,
                mock(WorldOverviewPageParser.class),
                mock(WorldDetailPageParser.class),
                mock(FormerCharacterNamePageParser.class)
        );
        when(httpClient.fetchWorldsOverviewDocument()).thenThrow(new IOException("overview"));
        when(httpClient.fetchWorldPageDocument("Antica")).thenThrow(new IOException("world"));
        when(httpClient.fetchCharacterDocument("Name")).thenThrow(new IOException("character"));

        assertThatThrownBy(adapter::fetchWorldsOverview)
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IOException.class);
        assertThatThrownBy(() -> adapter.fetchWorldPage("Antica", new World("Antica", null, null)))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IOException.class);
        assertThatThrownBy(() -> adapter.getFormerName("Name"))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void highscoreAdapterDelegatesFetchToHttpClientAndParser() throws Exception {
        TibiaHighscoreHttpClient httpClient = mock(TibiaHighscoreHttpClient.class);
        HighscorePageParser parser = mock(HighscorePageParser.class);
        JsoupHighscoreAdapter adapter = new JsoupHighscoreAdapter(httpClient, parser);
        var page = new TibiaHighscoreHttpClient.HighscorePage("<html></html>", "https://example.test/highscores");
        List<HighscoreRow> rows = List.of(new HighscoreRow(1, "Bubble", 123L));
        when(httpClient.fetchHighscoresPage("Antica", StatCategory.EXPERIENCE, 0, 1)).thenReturn(page);
        when(parser.parseHtml(page.html(), page.sourceUrl())).thenReturn(rows);

        assertThat(adapter.fetchHighscores("Antica", StatCategory.EXPERIENCE, 0, 1)).isSameAs(rows);
    }

    @Test
    void highscoreAdapterWrapsIoFailureAndRestoresInterruptFlag() throws Exception {
        TibiaHighscoreHttpClient httpClient = mock(TibiaHighscoreHttpClient.class);
        HighscorePageParser parser = mock(HighscorePageParser.class);
        JsoupHighscoreAdapter adapter = new JsoupHighscoreAdapter(httpClient, parser);
        when(httpClient.fetchHighscoresPage("Antica", StatCategory.EXPERIENCE, 0, 1))
                .thenThrow(new IOException("network"));
        when(httpClient.fetchHighscoresPage("Antica", StatCategory.EXPERIENCE, 0, 2))
                .thenThrow(new InterruptedException("stop"));

        assertThatThrownBy(() -> adapter.fetchHighscores("Antica", StatCategory.EXPERIENCE, 0, 1))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to fetch highscores")
                .hasCauseInstanceOf(IOException.class);

        try {
            assertThatThrownBy(() -> adapter.fetchHighscores("Antica", StatCategory.EXPERIENCE, 0, 2))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Interrupted while fetching highscores")
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void guildAdapterDelegatesListAndDetailParsing() throws Exception {
        TibiaGuildHttpClient httpClient = mock(TibiaGuildHttpClient.class);
        GuildListPageParser listParser = mock(GuildListPageParser.class);
        GuildDetailPageParser detailParser = mock(GuildDetailPageParser.class);
        JsoupGuildAdapter adapter = new JsoupGuildAdapter(httpClient, listParser, detailParser);
        Document listDoc = Jsoup.parse("<html></html>");
        Document detailDoc = Jsoup.parse("<html></html>");
        List<GuildListItem> guilds = List.of(new GuildListItem("Raw Raw", "Antica", true, "Active guild"));
        GuildDetail detail = new GuildDetail(
                "Raw Raw",
                "Antica",
                "Neutral guild",
                null,
                null,
                LocalDate.parse("2024-05-01"),
                1,
                1,
                "hash",
                List.of(),
                List.of()
        );
        when(httpClient.fetchGuildListDocument("Antica")).thenReturn(listDoc);
        when(httpClient.fetchGuildDetailDocument("Raw Raw")).thenReturn(detailDoc);
        when(listParser.parse(listDoc, "Antica")).thenReturn(guilds);
        when(detailParser.parse(detailDoc, "Raw Raw")).thenReturn(detail);

        assertThat(adapter.fetchGuildList("Antica")).isSameAs(guilds);
        assertThat(adapter.fetchGuildDetail("Raw Raw")).isSameAs(detail);
    }

    @Test
    void guildAdapterWrapsIoFailuresWithContext() throws Exception {
        TibiaGuildHttpClient httpClient = mock(TibiaGuildHttpClient.class);
        JsoupGuildAdapter adapter = new JsoupGuildAdapter(
                httpClient,
                mock(GuildListPageParser.class),
                mock(GuildDetailPageParser.class)
        );
        when(httpClient.fetchGuildListDocument("Antica")).thenThrow(new IOException("list"));
        when(httpClient.fetchGuildDetailDocument("Raw Raw")).thenThrow(new IOException("detail"));

        assertThatThrownBy(() -> adapter.fetchGuildList("Antica"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to fetch guild list for world 'Antica'")
                .hasCauseInstanceOf(IOException.class);
        assertThatThrownBy(() -> adapter.fetchGuildDetail("Raw Raw"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to fetch guild detail for 'Raw Raw'")
                .hasCauseInstanceOf(IOException.class);
    }

    private static CharacterDetails characterDetails(String currentName, List<String> formerNames) {
        return new CharacterDetails(
                currentName,
                formerNames,
                CharacterEntity.Sex.male,
                "Elite Knight",
                300,
                10,
                "Thais",
                OffsetDateTime.parse("2026-06-05T12:00:00Z"),
                "Premium Account",
                Instant.parse("2020-01-01T00:00:00Z"),
                "Antica"
        );
    }
}
