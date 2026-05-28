package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "guilds")
public class Guild {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "normalized_name", nullable = false, unique = true)
    private String normalizedName;

    @ManyToOne(optional = false)
    @JoinColumn(name = "world_id")
    private World world;

    @Column(columnDefinition = "text")
    private String description;

    private String homepage;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "founded_at")
    private LocalDate foundedAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "disband_condition")
    private String disbandCondition;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_scraped_at")
    private Instant lastScrapedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }

    public World getWorld() { return world; }
    public void setWorld(World world) { this.world = world; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getHomepage() { return homepage; }
    public void setHomepage(String homepage) { this.homepage = homepage; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public LocalDate getFoundedAt() { return foundedAt; }
    public void setFoundedAt(LocalDate foundedAt) { this.foundedAt = foundedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getDisbandCondition() { return disbandCondition; }
    public void setDisbandCondition(String disbandCondition) { this.disbandCondition = disbandCondition; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public Instant getLastScrapedAt() { return lastScrapedAt; }
    public void setLastScrapedAt(Instant lastScrapedAt) { this.lastScrapedAt = lastScrapedAt; }
}
