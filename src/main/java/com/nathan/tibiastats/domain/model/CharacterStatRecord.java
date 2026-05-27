package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "character_statrecords")
public class CharacterStatRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "character_id")
    private CharacterEntity character;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private StatCategory category;

    @Column(name = "vocation_filter_id", nullable = false)
    private Integer vocationFilterId = 0;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "value")
    private Long value;

    @Column(name = "rank")
    private Integer rank;

    @ManyToOne(optional = false)
    @JoinColumn(name = "world_id")
    private World world;

    @Column(name = "scraped_at", nullable = false)
    private Instant scrapedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CharacterEntity getCharacter() { return character; }
    public void setCharacter(CharacterEntity character) { this.character = character; }

    public StatCategory getCategory() { return category; }
    public void setCategory(StatCategory category) { this.category = category; }

    public Integer getVocationFilterId() { return vocationFilterId; }
    public void setVocationFilterId(Integer vocationFilterId) { this.vocationFilterId = vocationFilterId == null ? 0 : vocationFilterId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Long getValue() { return value; }
    public void setValue(Long value) { this.value = value; }

    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }

    public World getWorld() { return world; }
    public void setWorld(World world) { this.world = world; }

    public Instant getScrapedAt() { return scrapedAt; }
    public void setScrapedAt(Instant scrapedAt) { this.scrapedAt = scrapedAt; }
}
