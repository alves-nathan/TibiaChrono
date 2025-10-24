package com.nathan.tibiastats.domain.model;


import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


@Entity @Table(name="scrapes")
public class Scrape {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional=false)
    @JoinColumn(name="world_id")
    private World world;
    @Column(name = "scrape_time", nullable = false)
    private Instant scrapetime;
    @Column(name = "players_online", nullable = false)
    private Integer playersOnline;

    @OneToMany(mappedBy = "scrape", cascade = CascadeType.ALL)
    private List<ScrapePlayer> players = new ArrayList<>();

    public Scrape(){}

    public Scrape(Long id, World world, Instant scrapetime, Integer playersOnline, String playerlist) {
        this.id = id;
        this.world = world;
        this.scrapetime = scrapetime;
        this.playersOnline = playersOnline;
    }

    public Scrape(World world, Instant scrapetime, Integer playersOnline, String playerlist) {
        this.world = world;
        this.scrapetime = scrapetime;
        this.playersOnline = playersOnline;
    }

    public void addPlayer(ScrapePlayer sp) {
        sp.setScrape(this);
        this.players.add(sp);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public Instant getScrapetime() {
        return scrapetime;
    }

    public void setScrapetime(Instant scrapetime) {
        this.scrapetime = scrapetime;
    }

    public Integer getPlayersOnline() {
        return playersOnline;
    }

    public void setPlayersOnline(Integer playersOnline) {
        this.playersOnline = playersOnline;
    }

    public List<ScrapePlayer> getPlayers() {
        return players;
    }

    public void setPlayers(List<ScrapePlayer> players) {
        this.players = players;
    }
}