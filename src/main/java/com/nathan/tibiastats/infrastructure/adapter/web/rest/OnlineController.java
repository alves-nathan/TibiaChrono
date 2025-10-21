package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.AnalyticsService;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import com.nathan.tibiastats.domain.model.Scrape;
import org.springframework.web.bind.annotation.*;
import java.time.Instant; import java.util.*; import java.util.stream.*;

@RestController
@RequestMapping("/api/online")
public class OnlineController {
    private final AnalyticsService analytics;
    private final WorldRepositoryPort worlds;

    public OnlineController(AnalyticsService a, WorldRepositoryPort w) {
        this.analytics = a;
        this.worlds = w;
    }

    @GetMapping("/total")
    public Map<String, Object> total() {
        Map<String, Object> m = new HashMap<>();
        m.put("total", analytics.getCurrentOnlineTotal());
        return m;
    }

    @GetMapping("/worlds")
    public List<Map<String, Object>> worldsNow() {
        return worlds.findAll().stream()
                .map(w -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("world", w.getName());
                    int online = worlds.findLatestByWorld(w)
                            .map(Scrape::getPlayersOnline)
                            .orElse(0);
                    m.put("playersOnline", online);
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/worlds/{name}")
    public Map<String, Object> worldNow(@PathVariable String name) {
        var w = worlds.findByName(name).orElseThrow();
        var latest = worlds.findLatestByWorld(w);
        Map<String, Object> m = new HashMap<>();
        m.put("world", name);
        m.put("playersOnline", latest.map(Scrape::getPlayersOnline).orElse(0));
        return m;
    }

    @GetMapping("/worlds/{name}/history")
    public List<Map<String, Object>> history(
            @PathVariable String name,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {
        Instant start = (from == null)
                ? Instant.now().minusSeconds(86400)
                : Instant.ofEpochMilli(from);
        Instant end = (to == null) ? Instant.now() : Instant.ofEpochMilli(to);

        return analytics.getWorldOnlineHistory(name, start, end).stream()
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("timestamp", p.timestamp().toString());
                    m.put("playersOnline", p.playersOnline());
                    return m;
                })
                .collect(Collectors.toList());
    }
}
