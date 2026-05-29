package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.ScrapePlayer;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.model.Scrape;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WorldRepositoryPort {
    Optional<World> findByName(String name);
    World save(World w);
    List<World> findAll();
    Scrape saveScrape(Scrape r);
    List<Scrape> findScrapesByWorldAndRange(World w, Instant from, Instant to);
    Optional<Scrape> findLatestByWorld(World w);
    ScrapePlayer saveScrapePlayer(ScrapePlayer sp);
}