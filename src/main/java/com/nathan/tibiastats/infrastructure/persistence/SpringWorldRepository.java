package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.Instant; import java.util.*; import java.util.List;

interface WorldJpa extends JpaRepository<World, Integer> {
    Optional<World> findByName(String name);
}
interface ScrapeJpa extends JpaRepository<Scrape, Integer> {
    @Query("select s from Scrape s where s.world = :world and s.scrapeTime between :from and :to order by s.scrapeTime asc")
    List<Scrape> findRange(World world, Instant from, Instant to);

    @Query("select s from Scrape s where s.world = :world order by s.scrapeTime desc")
    List<Scrape> findLatest(World world, Pageable pageable);
}

interface ScrapePlayerJpa extends JpaRepository<ScrapePlayer, Long> {}

@Repository
public class SpringWorldRepository implements WorldRepositoryPort {
    private final WorldJpa worlds;
    private final ScrapeJpa scrapes;
    private final ScrapePlayerJpa scrapePlayers;

    public SpringWorldRepository(WorldJpa worlds, ScrapeJpa scrapes, ScrapePlayerJpa scrapePlayers) {
        this.worlds = worlds;
        this.scrapes = scrapes;
        this.scrapePlayers = scrapePlayers;
    }

    @Override
    public Optional<World> findByName(String name){
        return worlds.findByName(name);
    }

    @Override
    public World save(World w){
        return worlds.save(w);
    }

    @Override
    public List<World> findAll(){
        return worlds.findAll();
    }

    @Override
    public Scrape saveScrape(Scrape r){
        return scrapes.save(r);
    }

    @Override
    public List<Scrape> findScrapesByWorldAndRange(World w, Instant from, Instant to){
        return scrapes.findRange(w, from, to);
    }

    @Override
    public Optional<Scrape> findLatestByWorld(World w){
        var list = scrapes.findLatest(w, PageRequest.of(0,1));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public ScrapePlayer saveScrapePlayer(ScrapePlayer sp) {
        return scrapePlayers.save(sp);
    }
}