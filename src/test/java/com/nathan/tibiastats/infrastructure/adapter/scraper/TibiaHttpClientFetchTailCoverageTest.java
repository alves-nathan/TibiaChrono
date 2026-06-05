package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.ArgumentMatchers;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TibiaHttpClientFetchTailCoverageTest {
    @Test
    void worldHttpClientFetchesOverviewWorldAndCharacterDocumentsWithEncodedUrls() throws Exception {
        Document document = Jsoup.parse("<html><body>world</body></html>");
        Connection connection = mockConnection(document);

        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect(anyString())).thenReturn(connection);

            TibiaWorldHttpClient client = new TibiaWorldHttpClient();

            assertThat(client.fetchWorldsOverviewDocument()).isSameAs(document);
            assertThat(client.fetchWorldPageDocument("Wintera Test")).isSameAs(document);
            assertThat(client.fetchCharacterDocument("Old Name")).isSameAs(document);

            ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
            jsoup.verify(() -> Jsoup.connect(urls.capture()), times(3));
            assertThat(urls.getAllValues()).containsExactly(
                    TibiaWorldHttpClient.WORLDS_URL,
                    TibiaWorldHttpClient.WORLDS_URL + "&world=Wintera+Test",
                    "https://www.tibia.com/community/?name=Old+Name"
            );

            verify(connection, times(3)).userAgent(anyString());
            verify(connection, times(3)).timeout(15_000);
            verify(connection, times(3)).get();
        }
    }

    @Test
    void guildHttpClientFetchesListAndDetailDocumentsWithEncodedUrls() throws Exception {
        Document document = Jsoup.parse("<html><body>guild</body></html>");
        Connection connection = mockConnection(document);

        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect(anyString())).thenReturn(connection);

            TibiaGuildHttpClient client = new TibiaGuildHttpClient();

            assertThat(client.fetchGuildListDocument("Antica Space")).isSameAs(document);
            assertThat(client.fetchGuildDetailDocument("Raw Raw")).isSameAs(document);

            ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
            jsoup.verify(() -> Jsoup.connect(urls.capture()), times(2));
            assertThat(urls.getAllValues()).containsExactly(
                    TibiaGuildHttpClient.BASE_URL + "&world=Antica+Space",
                    TibiaGuildHttpClient.BASE_URL + "&page=view&GuildName=Raw+Raw"
            );

            verify(connection, times(2)).userAgent(anyString());
            verify(connection, times(2)).timeout(30_000);
            verify(connection, times(2)).get();
        }
    }

    @Test
    void characterHttpClientFetchesDetailsDocumentWithEncodedUrl() throws Exception {
        Document document = Jsoup.parse("<html><body>character</body></html>");
        Connection connection = mockConnection(document);

        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect(anyString())).thenReturn(connection);

            TibiaCharacterHttpClient client = new TibiaCharacterHttpClient();

            assertThat(client.fetchCharacterDetailsDocument("Knight Name")).isSameAs(document);

            ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
            jsoup.verify(() -> Jsoup.connect(urls.capture()));
            assertThat(urls.getValue())
                    .isEqualTo("https://www.tibia.com/community/?subtopic=characters&name=Knight+Name");

            verify(connection).userAgent(anyString());
            verify(connection).timeout(15_000);
            verify(connection).get();
        }
    }

    @Test
    void highscoreHttpClientFetchesPageThroughInjectedHttpClient() throws Exception {
        TibiaHighscoreHttpClient client = new TibiaHighscoreHttpClient();
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("<html>ok</html>");
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
        replaceHttpClient(client, httpClient);

        TibiaHighscoreHttpClient.HighscorePage page =
                client.fetchHighscoresPage("Wintera Space", StatCategory.EXPERIENCE, 4, 7);

        assertThat(page.html()).isEqualTo("<html>ok</html>");
        assertThat(page.sourceUrl())
                .contains("world=Wintera%20Space")
                .contains("profession=4")
                .contains("category=6")
                .contains("currentpage=7");

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertThat(request.getValue().uri().toString()).isEqualTo(page.sourceUrl());
        assertThat(request.getValue().headers().firstValue("User-Agent"))
                .hasValueSatisfying(userAgent -> assertThat(userAgent).contains("TibiaChrono/1.0"));
        assertThat(request.getValue().headers().firstValue("Accept"))
                .hasValueSatisfying(accept -> assertThat(accept).contains("text/html"));
        assertThat(request.getValue().timeout()).isPresent();
    }

    @Test
    void highscoreHttpClientThrowsIOExceptionForNonSuccessfulStatus() throws Exception {
        TibiaHighscoreHttpClient client = new TibiaHighscoreHttpClient();
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
        replaceHttpClient(client, httpClient);

        assertThatThrownBy(() -> client.fetchHighscoresPage("Antica", StatCategory.EXPERIENCE, 0, 1))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 503 from Tibia highscores")
                .hasMessageContaining("world=Antica")
                .hasMessageContaining("currentpage=1");
    }

    private static Connection mockConnection(Document document) throws IOException {
        Connection connection = mock(Connection.class);
        when(connection.userAgent(anyString())).thenReturn(connection);
        when(connection.timeout(anyInt())).thenReturn(connection);
        when(connection.get()).thenReturn(document);
        return connection;
    }

    private static void replaceHttpClient(TibiaHighscoreHttpClient client, HttpClient httpClient) throws Exception {
        Field field = TibiaHighscoreHttpClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(client, httpClient);
    }
}
