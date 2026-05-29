package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class TibiaHighscoreHttpClient {
    private static final String HS_URL = "https://www.tibia.com/community/?subtopic=highscores&world=%s&beprotection=-1&profession=%d&category=%d&currentpage=%d";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; TibiaChrono/1.0; +https://localhost)";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    /**
     * Reuse one HttpClient so Java can reuse connections instead of creating a fresh connection for every page.
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_2)
            .build();

    public HighscorePage fetchHighscoresPage(String world, StatCategory category, int vocationId, int page) throws IOException, InterruptedException {
        String url = buildHighscoreUrl(world, category, vocationId, page);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " from Tibia highscores: " + url);
        }
        return new HighscorePage(response.body(), url);
    }

    private String buildHighscoreUrl(String world, StatCategory category, int vocationId, int page) {
        int catId = mapCategory(category);
        String encodedWorld = URLEncoder.encode(world, StandardCharsets.UTF_8).replace("+", "%20");
        return String.format(HS_URL, encodedWorld, vocationId, catId, page);
    }

    private int mapCategory(StatCategory category) {
        return switch (category) {
            case ACHIEVEMENTS -> 1;
            case AXE_FIGHTING -> 2;
            case BOSS_POINTS -> 15;
            case BOUNTY_POINTS_EARNED -> 16;
            case CHARM_POINTS -> 3;
            case CLUB_FIGHTING -> 4;
            case DISTANCE_FIGHTING -> 5;
            case DROME_SCORE -> 14;
            case EXPERIENCE -> 6;
            case FISHING -> 7;
            case FIST_FIGHTING -> 8;
            case GOSHNARS_TAINT -> 9;
            case LOYALTY_POINTS -> 10;
            case MAGIC_LEVEL -> 11;
            case SHIELDING -> 12;
            case SWORD_FIGHTING -> 13;
            case WEEKLY_TASKS_COMPLETED -> 17;
        };
    }

    public record HighscorePage(String html, String sourceUrl) {
    }
}
