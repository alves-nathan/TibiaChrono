package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "guild_snapshots")
public class GuildSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "guild_id")
    private Guild guild;

    @Column(name = "scraped_at", nullable = false)
    private Instant scrapedAt;

    @Column(name = "member_count")
    private Integer memberCount;

    @Column(name = "online_count")
    private Integer onlineCount;

    @Column(name = "raw_hash")
    private String rawHash;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Guild getGuild() { return guild; }
    public void setGuild(Guild guild) { this.guild = guild; }

    public Instant getScrapedAt() { return scrapedAt; }
    public void setScrapedAt(Instant scrapedAt) { this.scrapedAt = scrapedAt; }

    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }

    public Integer getOnlineCount() { return onlineCount; }
    public void setOnlineCount(Integer onlineCount) { this.onlineCount = onlineCount; }

    public String getRawHash() { return rawHash; }
    public void setRawHash(String rawHash) { this.rawHash = rawHash; }
}
