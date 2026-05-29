package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.port.HighscorePort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class JsoupHighscoreAdapter implements HighscorePort {
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

    @Override
    public List<HighscoreRow> fetchHighscores(String world, StatCategory category, int vocationId, int page) {
        int catId = mapCategory(category);
        String encodedWorld = URLEncoder.encode(world, StandardCharsets.UTF_8).replace("+", "%20");
        String url = String.format(HS_URL, encodedWorld, vocationId, catId, page);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " from Tibia highscores: " + url);
            }

            Document doc = Jsoup.parse(response.body(), url);
            return parseHighscoreDocument(doc);
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch highscores: world=" + world
                    + ", category=" + category
                    + ", vocationId=" + vocationId
                    + ", page=" + page, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching highscores: world=" + world
                    + ", category=" + category
                    + ", vocationId=" + vocationId
                    + ", page=" + page, e);
        }
    }

    List<HighscoreRow> parseHighscoreDocument(Document doc) {
        List<HighscoreRow> out = new ArrayList<>();
        Elements rows = doc.select("table.TableContent tr");
        for (Element tr : rows) {
            Elements tds = tr.select("td");
            if (tds.size() < 3) {
                continue;
            }

            int rank = parseIntSafe(tds.get(0).text());
            String name = tds.get(1).text().trim();
            long value = parseLongSafe(tds.get(tds.size() - 1).text());

            if (rank > 0 && !name.isBlank()) {
                out.add(new HighscoreRow(rank, name, value));
            }
        }
        return out;
    }


    List<HighscoreRow> parseHighscoresHtml(String html, String sourceUrl) {
        Document doc = Jsoup.parse(html, sourceUrl);
        List<HighscoreRow> out = new ArrayList<>();
        Elements rows = doc.select("table.TableContent tr");
        for (Element tr : rows) {
            Elements tds = tr.select("td");
            if (tds.size() < 3) {
                continue;
            }

            int rank = parseIntSafe(tds.get(0).text());
            String name = tds.get(1).text().trim();
            long value = parseLongSafe(tds.get(tds.size() - 1).text());

            if (rank > 0 && !name.isBlank()) {
                out.add(new HighscoreRow(rank, name, value));
            }
        }
        return out;
    }

    private int mapCategory(StatCategory c) {
        return switch (c) {
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

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseLongSafe(String s) {
        try {
            String cleaned = s.replaceAll("[^0-9]", "");
            return cleaned.isBlank() ? 0L : Long.parseLong(cleaned);
        } catch (Exception e) {
            return 0L;
        }
    }
}
