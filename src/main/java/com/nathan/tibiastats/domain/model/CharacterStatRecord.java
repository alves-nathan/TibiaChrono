package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name="characterStatRecords")
public class CharacterStatRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    @JoinColumn(name="character_id")
    private Character character;

    @Enumerated(EnumType.STRING)
    private StatCategory category;

    @Column(name = "date")
    private LocalDate date;

    @Column(name="value")
    private Long value;

    @Column(name = "rank")
    private Integer rank;

    @ManyToOne(optional=false)
    @JoinColumn(name="world_id")
    private World world;

    @Column(name = "timestamp")
    private Instant timestamp;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Character getCharacter() {
        return character;
    }

    public void setCharacter(Character character) {
        this.character = character;
    }

    public StatCategory getCategory() {
        return category;
    }

    public void setCategory(StatCategory category) {
        this.category = category;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Long getValue() {
        return value;
    }

    public void setValue(Long value) {
        this.value = value;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}