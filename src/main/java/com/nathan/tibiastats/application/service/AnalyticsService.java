package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.port.AnalyticsQueryPort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import com.nathan.tibiastats.domain.model.Scrape;
import org.springframework.stereotype.Service;
import java.time.Instant; import java.util.*; import java.util.stream.*;

@Service
public class AnalyticsService implements AnalyticsQueryPort {
    private final WorldRepositoryPort worlds;
    public AnalyticsService(WorldRepositoryPort worlds){this.worlds=worlds;}

    @Override
    public int getCurrentOnlineTotal(){
        return worlds.findAll().stream()
                .map(worlds::findLatestByWorld)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .mapToInt(Scrape::getPlayersOnline)
                .sum();
    }

    @Override
    public List<RecordPoint> getWorldOnlineHistory(String worldName, Instant from, Instant to){
        var w = worlds.findByName(worldName).orElseThrow();
        return worlds.findScrapesByWorldAndRange(w, from, to).stream()
                .map(s -> new RecordPoint(s.getScrapeTime(), s.getPlayersOnline()))
                .collect(Collectors.toList());
    }
}