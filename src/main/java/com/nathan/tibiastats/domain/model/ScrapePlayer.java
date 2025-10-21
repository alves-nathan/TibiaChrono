package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;

// domain/model/ScrapePlayer.java
@Entity
@Table(name = "scrape_players")
public class ScrapePlayer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "scrape_id")
    private Scrape scrape;

    @ManyToOne(optional = false)
    @JoinColumn(name = "character_id")
    private Character character;

    public ScrapePlayer(Scrape scrape, Character character) {
        this.scrape = scrape;
        this.character = character;
    }

    public ScrapePlayer(){}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Scrape getScrape() {
        return scrape;
    }

    public void setScrape(Scrape scrape) {
        this.scrape = scrape;
    }

    public Character getCharacter() {
        return character;
    }

    public void setCharacter(Character character) {
        this.character = character;
    }
}
