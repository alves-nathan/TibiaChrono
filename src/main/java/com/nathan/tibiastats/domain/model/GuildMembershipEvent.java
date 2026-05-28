package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "guild_membership_events")
public class GuildMembershipEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id")
    private CharacterEntity character;

    @Column(name = "character_name_snapshot", nullable = false)
    private String characterNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private GuildMembershipEventType eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_guild_id")
    private Guild fromGuild;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_guild_id")
    private Guild toGuild;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(columnDefinition = "text")
    private String description;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CharacterEntity getCharacter() { return character; }
    public void setCharacter(CharacterEntity character) { this.character = character; }

    public String getCharacterNameSnapshot() { return characterNameSnapshot; }
    public void setCharacterNameSnapshot(String characterNameSnapshot) { this.characterNameSnapshot = characterNameSnapshot; }

    public GuildMembershipEventType getEventType() { return eventType; }
    public void setEventType(GuildMembershipEventType eventType) { this.eventType = eventType; }

    public Guild getFromGuild() { return fromGuild; }
    public void setFromGuild(Guild fromGuild) { this.fromGuild = fromGuild; }

    public Guild getToGuild() { return toGuild; }
    public void setToGuild(Guild toGuild) { this.toGuild = toGuild; }

    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
