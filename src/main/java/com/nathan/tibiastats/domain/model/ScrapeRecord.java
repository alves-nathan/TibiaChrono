package com.nathan.tibiastats.domain.model;


import jakarta.persistence.*;
import java.time.Instant;


@Entity @Table(name="scrapes")
public class ScrapeRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional=false)
    @JoinColumn(name="world_id")
    private World world;
    @Column(name = "scrape_time")
    private Instant scrapetime;
    @Column(name = "players_online")
    private Integer playersOnline;

    @Lob
    @Column(name = "player_list", columnDefinition = "TEXT")
    private String playerlist; // JSON string

    public ScrapeRecord(){}

    public ScrapeRecord(Integer id, World world, Instant scrapetime, Integer playersOnline, String playerlist) {
        this.id = id;
        this.world = world;
        this.scrapetime = scrapetime;
        this.playersOnline = playersOnline;
        this.playerlist = playerlist;
    }

    public ScrapeRecord(World world, Instant scrapetime, Integer playersOnline, String playerlist) {
        this.world = world;
        this.scrapetime = scrapetime;
        this.playersOnline = playersOnline;
        this.playerlist = playerlist;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
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

    public String getPlayerlist() {
        return playerlist;
    }

    public void setPlayerlist(String playerlist) {
        this.playerlist = playerlist;
    }
}