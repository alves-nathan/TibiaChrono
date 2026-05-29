package com.nathan.tibiastats.infrastructure.adapter.web.graphql;

import com.nathan.tibiastats.application.service.AnalyticsService;
import com.nathan.tibiastats.application.query.ApiQueryService;
import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class StatsGraphQLController {

    private final AnalyticsService analytics;
    private final WorldRepositoryPort worlds;
    private final ApiQueryService queries;

    public StatsGraphQLController(AnalyticsService analytics,
                                  WorldRepositoryPort worlds,
                                  ApiQueryService queries) {
        this.analytics = analytics;
        this.worlds = worlds;
        this.queries = queries;
    }

    @QueryMapping
    public Integer onlineTotal() {
        return analytics.getCurrentOnlineTotal();
    }

    @QueryMapping
    public List<Map<String, Object>> worldsOnline() {
        return worlds.findAll().stream()
                .map(w -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", w.getName());
                    int online = worlds.findLatestByWorld(w)
                            .map(Scrape::getPlayersOnline)
                            .orElse(0);
                    m.put("playersOnline", online);
                    return m;
                })
                .collect(Collectors.toList());
    }

    @QueryMapping
    public Map<String, Object> worldOnlineNow(@Argument String name) {
        var w = worlds.findByName(name).orElseThrow();
        var latest = worlds.findLatestByWorld(w);
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("playersOnline", latest.map(Scrape::getPlayersOnline).orElse(0));
        return m;
    }

    @QueryMapping
    public List<Map<String, Object>> worldOnlineHistory(
            @Argument String name,
            @Argument String from,
            @Argument String to) {
        Instant start = (from == null)
                ? Instant.now().minusSeconds(86400)
                : Instant.parse(from);
        Instant end = (to == null)
                ? Instant.now()
                : Instant.parse(to);

        return analytics.getWorldOnlineHistory(name, start, end).stream()
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("timestamp", p.timestamp().toString());
                    m.put("playersOnline", p.playersOnline());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @QueryMapping
    public List<ApiQueryService.WorldView> worlds() {
        return queries.findWorlds();
    }

    @QueryMapping
    public ApiQueryService.WorldView world(@Argument String name) {
        return queries.findWorld(name).orElse(null);
    }

    @QueryMapping
    public ApiQueryService.CharacterView character(@Argument String name) {
        return queries.findCharacter(name).orElse(null);
    }

    @QueryMapping
    public List<ApiQueryService.CharacterNameView> characterNames(@Argument String name) {
        return queries.findCharacterNames(name);
    }

    @QueryMapping
    public List<ApiQueryService.HighscoreView> characterHighscores(
            @Argument String name,
            @Argument StatCategory category,
            @Argument String world,
            @Argument Integer vocationFilterId,
            @Argument String from,
            @Argument String to,
            @Argument Integer limit) {
        return queries.findCharacterHighscores(
                name,
                category,
                world,
                vocationFilterId,
                from == null ? null : LocalDate.parse(from),
                to == null ? null : LocalDate.parse(to),
                limit == null ? 100 : limit
        );
    }

    @QueryMapping
    public List<ApiQueryService.HighscoreView> highscores(
            @Argument String world,
            @Argument StatCategory category,
            @Argument Integer vocationFilterId,
            @Argument String date,
            @Argument Integer limit) {
        return queries.findHighscores(
                world,
                category,
                vocationFilterId,
                date == null ? null : LocalDate.parse(date),
                limit == null ? 100 : limit
        );
    }

    @QueryMapping
    public List<ApiQueryService.ScrapeJobView> scrapeJobs(
            @Argument String jobName,
            @Argument String status,
            @Argument Integer limit) {
        return queries.findScrapeJobs(jobName, status, limit == null ? 50 : limit);
    }

    /** Legacy query kept for compatibility with the previous schema. */
    @QueryMapping
    public List<ApiQueryService.HighscoreView> characterStatHistory(
            @Argument String name,
            @Argument StatCategory category) {
        return queries.findCharacterHighscores(name, category, null, null, null, null, 100);
    }
}
