package com.nathan.tibiastats.infrastructure.adapter.web.graphql;

import com.nathan.tibiastats.application.service.AnalyticsService;
import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class StatsGraphQLController {

    private final AnalyticsService analytics;
    private final WorldRepositoryPort worlds;
    private final CharacterRepositoryPort chars;

    public StatsGraphQLController(AnalyticsService a,
                                  WorldRepositoryPort w,
                                  CharacterRepositoryPort c) {
        this.analytics = a;
        this.worlds = w;
        this.chars = c;
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
    public Map<String, Object> character(@Argument String name) {
        var c = chars.findByAnyName(name, CharacterName.INACTIVE_HORIZON).orElseThrow();
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("name", name);
        m.put("level", c.getLevel());
        m.put("vocation", c.getVocation());
        return m;
    }

    @QueryMapping
    public List<Map<String, Object>> characterStatHistory(
            @Argument String name,
            @Argument StatCategory category) {
        var c = chars.findByAnyName(name, CharacterName.INACTIVE_HORIZON).orElseThrow();
        return chars.findStatsBy(c, category).stream()
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("date", r.getDate().toString());
                    m.put("value", r.getValue());
                    m.put("rank", r.getRank());
                    m.put("world", r.getWorld().getName());
                    return m;
                })
                .collect(Collectors.toList());
    }
}
