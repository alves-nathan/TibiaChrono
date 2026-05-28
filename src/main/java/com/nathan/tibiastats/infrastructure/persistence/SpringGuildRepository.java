package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface GuildJpa extends JpaRepository<Guild, Long> {
    Optional<Guild> findByNameIgnoreCase(String name);

    Optional<Guild> findByNormalizedName(String normalizedName);

    @Query("""
        select g
          from Guild g
          join fetch g.world w
         where (:worldName is null or lower(w.name) = lower(:worldName))
           and (:active is null or g.active = :active)
         order by w.name asc, g.name asc
        """)
    List<Guild> findGuilds(@Param("worldName") String worldName, @Param("active") Boolean active);

    @Query("""
        select g
          from Guild g
          join fetch g.world w
         where g.active = true
         order by
           case when g.lastScrapedAt is null then 0 else 1 end asc,
           g.lastScrapedAt asc,
           g.name asc
        """)
    List<Guild> findActiveForDetailsRefresh(org.springframework.data.domain.Pageable pageable);
}

interface GuildSnapshotJpa extends JpaRepository<GuildSnapshot, Long> {}

interface GuildMembershipJpa extends JpaRepository<GuildMembership, Long> {
    @Query("""
        select gm
          from GuildMembership gm
          join fetch gm.character c
         where gm.guild.id = :guildId
           and gm.active = true
         order by gm.rankName asc nulls last, gm.characterNameSnapshot asc
        """)
    List<GuildMembership> findActiveByGuildId(@Param("guildId") Long guildId);

    @Query(value = """
        select gm.*
          from guild_memberships gm
         where gm.character_id = :characterId
           and gm.active is true
         limit 1
        """, nativeQuery = true)
    Optional<GuildMembership> findActiveByCharacterId(@Param("characterId") Long characterId);

    @Query("""
        select gm
          from GuildMembership gm
          join fetch gm.guild g
          join fetch gm.character c
         where c.id = :characterId
         order by gm.joinedAt desc, gm.id desc
        """)
    List<GuildMembership> findHistoryByCharacterId(@Param("characterId") Long characterId);

    @Query("""
        select gm
          from GuildMembership gm
          join fetch gm.guild g
          join fetch gm.character c
         where gm.guild.id = :guildId
           and (:active is null or gm.active = :active)
         order by gm.active desc, gm.rankName asc nulls last, gm.characterNameSnapshot asc
        """)
    List<GuildMembership> findByGuildId(@Param("guildId") Long guildId, @Param("active") Boolean active);
}

interface GuildMembershipEventJpa extends JpaRepository<GuildMembershipEvent, Long> {
    @Query("""
        select e
          from GuildMembershipEvent e
          left join fetch e.fromGuild fg
          left join fetch e.toGuild tg
          join fetch e.character c
         where (:guildId is null or fg.id = :guildId or tg.id = :guildId)
           and (:characterId is null or c.id = :characterId)
           and (:type is null or e.eventType = :type)
           and (:from is null or e.observedAt >= :from)
           and (:to is null or e.observedAt <= :to)
         order by e.observedAt desc, e.id desc
        """)
    List<GuildMembershipEvent> findEvents(@Param("guildId") Long guildId,
                                           @Param("characterId") Long characterId,
                                           @Param("type") GuildMembershipEventType type,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to,
                                           org.springframework.data.domain.Pageable pageable);
}

interface GuildInviteJpa extends JpaRepository<GuildInvite, Long> {
    @Query("""
        select i
          from GuildInvite i
         where i.guild.id = :guildId
           and i.active = true
        """)
    List<GuildInvite> findActiveByGuildId(@Param("guildId") Long guildId);

    @Query("""
        select i
          from GuildInvite i
         where i.guild.id = :guildId
           and lower(i.characterName) = lower(:characterName)
           and i.active = true
        """)
    Optional<GuildInvite> findActive(@Param("guildId") Long guildId, @Param("characterName") String characterName);
}

@Repository
public class SpringGuildRepository {
    private final GuildJpa guilds;
    private final GuildSnapshotJpa snapshots;
    private final GuildMembershipJpa memberships;
    private final GuildMembershipEventJpa events;
    private final GuildInviteJpa invites;

    public SpringGuildRepository(GuildJpa guilds,
                                 GuildSnapshotJpa snapshots,
                                 GuildMembershipJpa memberships,
                                 GuildMembershipEventJpa events,
                                 GuildInviteJpa invites) {
        this.guilds = guilds;
        this.snapshots = snapshots;
        this.memberships = memberships;
        this.events = events;
        this.invites = invites;
    }

    public Optional<Guild> findGuild(String name) {
        return guilds.findByNormalizedName(normalizeGuildName(name))
                .or(() -> guilds.findByNameIgnoreCase(name));
    }

    public Guild saveGuild(Guild guild) { return guilds.save(guild); }

    public List<Guild> findGuilds(String worldName, Boolean active) { return guilds.findGuilds(blankToNull(worldName), active); }

    public List<Guild> findActiveForDetailsRefresh(int limit) {
        return guilds.findActiveForDetailsRefresh(PageRequest.of(0, Math.max(1, limit)));
    }

    public GuildSnapshot saveSnapshot(GuildSnapshot snapshot) { return snapshots.save(snapshot); }

    public GuildMembership saveMembership(GuildMembership membership) { return memberships.save(membership); }

    public GuildMembership saveAndFlushMembership(GuildMembership membership) { return memberships.saveAndFlush(membership); }

    public void flushMemberships() { memberships.flush(); }

    public List<GuildMembership> findActiveMemberships(Guild guild) { return memberships.findActiveByGuildId(guild.getId()); }

    public Optional<GuildMembership> findActiveMembershipForCharacter(Long characterId) {
        return memberships.findActiveByCharacterId(characterId);
    }

    public List<GuildMembership> findMemberships(Guild guild, Boolean active) {
        return memberships.findByGuildId(guild.getId(), active);
    }

    public List<GuildMembership> findMembershipHistory(Long characterId) {
        return memberships.findHistoryByCharacterId(characterId);
    }

    public GuildMembershipEvent saveEvent(GuildMembershipEvent event) { return events.save(event); }

    public List<GuildMembershipEvent> findEvents(Long guildId,
                                                 Long characterId,
                                                 GuildMembershipEventType type,
                                                 Instant from,
                                                 Instant to,
                                                 int limit) {
        return events.findEvents(guildId, characterId, type, from, to, PageRequest.of(0, Math.max(1, limit)));
    }

    public GuildInvite saveInvite(GuildInvite invite) { return invites.save(invite); }

    public Optional<GuildInvite> findActiveInvite(Long guildId, String characterName) {
        return invites.findActive(guildId, characterName);
    }

    public List<GuildInvite> findActiveInvites(Long guildId) { return invites.findActiveByGuildId(guildId); }

    public static String normalizeGuildName(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
