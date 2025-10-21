package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "guild_characters")
public class GuildCharacter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "guild_id")
    private Guild guild;

    @ManyToOne(optional = false)
    @JoinColumn(name = "character_id")
    private Character character;

    private Instant timestamp;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Guild getGuild() {
        return guild;
    }

    public void setGuild(Guild guild) {
        this.guild = guild;
    }

    public Character getCharacter() {
        return character;
    }

    public void setCharacter(Character character) {
        this.character = character;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
