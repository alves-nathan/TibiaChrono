package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.Instant; import java.util.*; import java.util.List;

interface WorldJpa extends JpaRepository<World, Integer> { Optional<World> findByName(String name); }
interface ScrapeJpa extends JpaRepository<ScrapeRecord, Integer> {
    @Query("select s from ScrapeRecord s where s.world = :world and s.scrapetime between :from and :to order by s.scrapetime asc")
    List<ScrapeRecord> findRange(World world, Instant from, Instant to);
    @Query("select s from ScrapeRecord s where s.world = :world order by s.scrapetime desc limit 1")
    Optional<ScrapeRecord> findLatest(World world);
}

@Repository
public class SpringWorldRepository implements WorldRepositoryPort {
    private final WorldJpa worlds; private final ScrapeJpa scrapes;
    public SpringWorldRepository(WorldJpa w, ScrapeJpa s){this.worlds=w; this.scrapes=s;}
    public Optional<World> findByName(String name){return worlds.findByName(name);}
    public World save(World w){return worlds.save(w);}
    public List<World> findAll(){return worlds.findAll();}
    public ScrapeRecord saveScrape(ScrapeRecord r){return scrapes.save(r);}
    public List<ScrapeRecord> findScrapesByWorldAndRange(World w, Instant from, Instant to){return scrapes.findRange(w, from, to);}
    public Optional<ScrapeRecord> findLatestByWorld(World w){return scrapes.findLatest(w);}
}