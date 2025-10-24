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
    private CharacterEntity character;

    @JoinColumn(name = "created_at")
    private Instant createdAt;

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

    public CharacterEntity getCharacter() {
        return character;
    }

    public void setCharacter(CharacterEntity character) {
        this.character = character;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
