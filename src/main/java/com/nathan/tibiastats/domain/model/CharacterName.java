package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Entity @Table(name="character_names")
public class CharacterName {
    public static final Instant INACTIVE_HORIZON = ZonedDateTime.now(ZoneOffset.UTC).minusMonths(6).toInstant();
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "character_id")
    private CharacterEntity character;
    @Column(nullable=false, unique=true)
    private String name;
    @Column(name = "active")
    private Boolean active;
    @Column(name = "inactive_date")
    private Instant inactiveDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getInactiveDate() {
        return inactiveDate;
    }

    public void setInactiveDate(Instant inactiveDate) {
        this.inactiveDate = inactiveDate;
    }

    public CharacterEntity getCharacter() {
        return character;
    }

    public void setCharacter(CharacterEntity character) {
        this.character = character;
    }

    public static CharacterName createActive(String name, CharacterEntity c) {
        CharacterName cn = new CharacterName();
        cn.setName(name);
        cn.setCharacter(c);
        cn.setActive(true);
        cn.setInactiveDate(null);
        return cn;
    }

    public void deactivate(Instant when) {
        this.setActive(false);
        this.setInactiveDate(when);
    }
}