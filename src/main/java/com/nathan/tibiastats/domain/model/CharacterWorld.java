package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "character_worlds")
public class CharacterWorld {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "character_id")
    private Character character;

    @ManyToOne(optional = false)
    @JoinColumn(name = "world_id")
    private World world;

    private Boolean active;

    @Column(nullable = false)
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

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public static CharacterWorld createActive(World world) {
        CharacterWorld cn = new CharacterWorld();
        cn.setWorld(world);
        cn.setActive(true);
        cn.setTimestamp(null);
        return cn;
    }

    public void deactivate(Instant when) {
        this.active = false;
        this.timestamp = when;
    }
}