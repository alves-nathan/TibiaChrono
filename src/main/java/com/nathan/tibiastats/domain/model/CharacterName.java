package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Entity
@Table(name = "character_names")
public class CharacterName {
    /**
     * Dynamic cutoff used when resolving former names. Former names are only a safe
     * identity alias for 6 months; after that CipSoft may release/reuse the name.
     */
    public static Instant inactiveHorizon() {
        return ZonedDateTime.now(ZoneOffset.UTC).minusMonths(6).toInstant();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "character_id")
    private CharacterEntity character;

    @Column(nullable = false)
    private String name;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "inactive_date")
    private Instant inactiveDate;

    @PrePersist
    @PreUpdate
    private void normalizeNameBeforePersistence() {
        this.name = CharacterNameNormalizer.normalize(this.name);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = CharacterNameNormalizer.normalize(name); }

    public Boolean getActive() { return active; }
    public boolean isActive() { return Boolean.TRUE.equals(active); }
    public void setActive(Boolean active) { this.active = active; }

    public Instant getInactiveDate() { return inactiveDate; }
    public void setInactiveDate(Instant inactiveDate) { this.inactiveDate = inactiveDate; }

    public CharacterEntity getCharacter() { return character; }
    public void setCharacter(CharacterEntity character) { this.character = character; }

    public static CharacterName createActive(String name, CharacterEntity character) {
        CharacterName characterName = new CharacterName();
        characterName.setName(name);
        characterName.setCharacter(character);
        characterName.setActive(true);
        characterName.setInactiveDate(null);
        return characterName;
    }

    public static CharacterName createInactive(String name, CharacterEntity character, Instant inactiveDate) {
        CharacterName characterName = new CharacterName();
        characterName.setName(name);
        characterName.setCharacter(character);
        characterName.setActive(false);
        characterName.setInactiveDate(inactiveDate);
        return characterName;
    }

    public void activate() {
        this.setActive(true);
        this.setInactiveDate(null);
    }

    public void deactivate(Instant when) {
        this.setActive(false);
        this.setInactiveDate(when);
    }
}
