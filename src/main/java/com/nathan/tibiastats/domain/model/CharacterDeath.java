package com.nathan.tibiastats.domain.model;


import jakarta.persistence.*;
import java.time.Instant;


@Entity
@Table(name="characterdeaths")
public class CharacterDeath {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional=false)
    @JoinColumn(name="character_id")
    private CharacterEntity character;
    @Column(name="death_date")
    private Instant deathDate;
    @Column(name="killed_by")
    private String killedBy; // keeping original column name

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public CharacterEntity getCharacter() {
        return character;
    }

    public void setCharacter(CharacterEntity character) {
        this.character = character;
    }

    public Instant getDeathDate() {
        return deathDate;
    }

    public void setDeathDate(Instant deathDate) {
        this.deathDate = deathDate;
    }

    public String getKilledBy() {
        return killedBy;
    }

    public void setKilledBy(String killedBy) {
        this.killedBy = killedBy;
    }
}