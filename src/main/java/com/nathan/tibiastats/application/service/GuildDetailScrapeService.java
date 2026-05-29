package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import com.nathan.tibiastats.domain.port.GuildScrapePort;
import com.nathan.tibiastats.infrastructure.persistence.SpringGuildRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GuildDetailScrapeService {
    private static final Logger log = LoggerFactory.getLogger(GuildDetailScrapeService.class);
    private static final String LOG_PREFIX = "[GUILD_SCRAPER]";

    private final GuildScrapePort scraper;
    private final SpringGuildRepository guilds;
    private final GuildCatalogService catalog;
    private final CharacterNamingService characterNamingService;
    private final CharacterRepositoryPort characters;
    private final Clock clock;

    @Autowired
    public GuildDetailScrapeService(GuildScrapePort scraper,
                                    SpringGuildRepository guilds,
                                    GuildCatalogService catalog,
                                    CharacterNamingService characterNamingService,
                                    CharacterRepositoryPort characters) {
        this(scraper, guilds, catalog, characterNamingService, characters, Clock.systemUTC());
    }

    GuildDetailScrapeService(GuildScrapePort scraper,
                             SpringGuildRepository guilds,
                             GuildCatalogService catalog,
                             CharacterNamingService characterNamingService,
                             CharacterRepositoryPort characters,
                             Clock clock) {
        this.scraper = scraper;
        this.guilds = guilds;
        this.catalog = catalog;
        this.characterNamingService = characterNamingService;
        this.characters = characters;
        this.clock = clock;
    }

    @Transactional
    public GuildScrapeService.GuildDetailResult updateGuildDetail(String guildName) {
        Instant observedAt = clock.instant();
        GuildScrapePort.GuildDetail detail = scraper.fetchGuildDetail(guildName);
        if (isBlank(detail.name())) {
            throw new IllegalStateException("Guild detail did not contain a guild name for '" + guildName + "'");
        }

        GuildCatalogService.GuildUpdate result = catalog.upsertGuild(
                detail.name(),
                detail.worldName(),
                detail.description(),
                detail.homepage(),
                detail.logoUrl(),
                detail.foundedAt(),
                true,
                observedAt
        );
        Guild guild = result.guild();
        guild.setLastScrapedAt(observedAt);
        guilds.saveGuild(guild);

        saveSnapshot(guild, detail, observedAt);

        Map<Long, GuildMembership> activeBefore = guilds.findActiveMemberships(guild).stream()
                .filter(m -> m.getCharacter() != null && m.getCharacter().getId() != null)
                .collect(Collectors.toMap(m -> m.getCharacter().getId(), m -> m, (a, b) -> a, LinkedHashMap::new));

        Set<Long> seenCharacterIds = new LinkedHashSet<>();
        int opened = 0;
        int updated = 0;
        int closed = 0;
        int transfers = 0;

        for (GuildScrapePort.Member member : nullSafe(detail.members())) {
            if (isBlank(member.name())) continue;

            CharacterEntity character = characterNamingService.resolveObservedName(member.name());
            updateCharacterSnapshot(character, member);
            Long characterId = character.getId();
            seenCharacterIds.add(characterId);

            GuildMembership activeFromThisGuild = activeBefore.get(characterId);
            if (activeFromThisGuild != null) {
                refreshMembership(activeFromThisGuild, member, observedAt);
                guilds.saveMembership(activeFromThisGuild);
                updated++;
                continue;
            }

            Optional<GuildMembership> currentActive = guilds.findActiveMembershipForCharacter(characterId);
            if (currentActive.isEmpty()) {
                GuildMembership membership = openMembership(guild, character, member, observedAt);
                guilds.saveMembership(membership);
                saveEvent(character, member.name(), GuildMembershipEventType.JOINED, null, guild, observedAt,
                        "Observed character joining guild " + guild.getName());
                opened++;
                continue;
            }

            GuildMembership active = currentActive.get();
            if (active.getGuild() != null && active.getGuild().getId().equals(guild.getId())) {
                refreshMembership(active, member, observedAt);
                guilds.saveMembership(active);
                updated++;
            } else {
                Guild previousGuild = active.getGuild();
                closeMembership(active, observedAt);
                guilds.saveAndFlushMembership(active);

                GuildMembership membership = openMembership(guild, character, member, observedAt);
                guilds.saveMembership(membership);
                saveEvent(character, member.name(), GuildMembershipEventType.TRANSFERRED, previousGuild, guild, observedAt,
                        "Observed character transfer from " + safeGuildName(previousGuild) + " to " + guild.getName());
                transfers++;
                opened++;
                closed++;
            }
        }

        for (GuildMembership membership : activeBefore.values()) {
            if (membership.getCharacter() == null || membership.getCharacter().getId() == null) continue;
            if (seenCharacterIds.contains(membership.getCharacter().getId())) continue;

            closeMembership(membership, observedAt);
            guilds.saveMembership(membership);
            saveEvent(membership.getCharacter(), membership.getCharacterNameSnapshot(), GuildMembershipEventType.LEFT, guild, null, observedAt,
                    "Observed character leaving guild " + guild.getName());
            closed++;
        }

        updateInvites(guild, detail, observedAt);

        log.info("{} Guild detail updated: guild={}, membersSeen={}, opened={}, updated={}, closed={}, transfers={}",
                LOG_PREFIX, guild.getName(), nullSafe(detail.members()).size(), opened, updated, closed, transfers);

        return new GuildScrapeService.GuildDetailResult(guild.getName(), nullSafe(detail.members()).size(), opened, updated, closed, transfers);
    }

    private void saveSnapshot(Guild guild, GuildScrapePort.GuildDetail detail, Instant observedAt) {
        GuildSnapshot snapshot = new GuildSnapshot();
        snapshot.setGuild(guild);
        snapshot.setScrapedAt(observedAt);
        snapshot.setMemberCount(detail.memberCount());
        snapshot.setOnlineCount(detail.onlineCount());
        snapshot.setRawHash(detail.rawHash());
        guilds.saveSnapshot(snapshot);
    }

    private GuildMembership openMembership(Guild guild, CharacterEntity character, GuildScrapePort.Member member, Instant observedAt) {
        GuildMembership membership = new GuildMembership();
        membership.setGuild(guild);
        membership.setCharacter(character);
        membership.setCharacterNameSnapshot(normalizeDisplayName(member.name()));
        membership.setRankName(blankToNull(member.rankName()));
        membership.setTitle(blankToNull(member.title()));
        membership.setVocation(blankToNull(member.vocation()));
        membership.setLevel(member.level());
        membership.setJoinedOn(member.joinedOn());
        membership.setJoinedAt(toMembershipJoinedAt(member, observedAt));
        membership.setFirstSeenAt(observedAt);
        membership.setLastSeenAt(observedAt);
        membership.setActive(true);
        return membership;
    }

    private void refreshMembership(GuildMembership membership, GuildScrapePort.Member member, Instant observedAt) {
        membership.setCharacterNameSnapshot(normalizeDisplayName(member.name()));
        membership.setRankName(blankToNull(member.rankName()));
        membership.setTitle(blankToNull(member.title()));
        membership.setVocation(blankToNull(member.vocation()));
        membership.setLevel(member.level());
        if (member.joinedOn() != null) {
            membership.setJoinedOn(member.joinedOn());
            membership.setJoinedAt(toMembershipJoinedAt(member, observedAt));
        }
        membership.setLastSeenAt(observedAt);
        membership.setActive(true);
        membership.setLeftAt(null);
    }

    private void closeMembership(GuildMembership membership, Instant observedAt) {
        membership.setActive(false);
        membership.setLeftAt(observedAt);
        membership.setLastSeenAt(observedAt);
    }

    private void saveEvent(CharacterEntity character,
                           String nameSnapshot,
                           GuildMembershipEventType type,
                           Guild fromGuild,
                           Guild toGuild,
                           Instant observedAt,
                           String description) {
        GuildMembershipEvent event = new GuildMembershipEvent();
        event.setCharacter(character);
        event.setCharacterNameSnapshot(normalizeDisplayName(nameSnapshot));
        event.setEventType(type);
        event.setFromGuild(fromGuild);
        event.setToGuild(toGuild);
        event.setObservedAt(observedAt);
        event.setDescription(description);
        guilds.saveEvent(event);
    }

    private void updateInvites(Guild guild, GuildScrapePort.GuildDetail detail, Instant observedAt) {
        Set<String> seen = new HashSet<>();
        for (GuildScrapePort.Invite invite : nullSafe(detail.invites())) {
            if (isBlank(invite.characterName())) continue;
            String normalized = invite.characterName().trim().toLowerCase(Locale.ROOT);
            seen.add(normalized);
            GuildInvite entity = guilds.findActiveInvite(guild.getId(), invite.characterName()).orElseGet(GuildInvite::new);
            if (entity.getId() == null) {
                entity.setGuild(guild);
                entity.setCharacterName(normalizeDisplayName(invite.characterName()));
                entity.setInvitedAt(invite.invitedAt());
                entity.setFirstSeenAt(observedAt);
            }
            entity.setLastSeenAt(observedAt);
            entity.setActive(true);
            guilds.saveInvite(entity);
        }

        for (GuildInvite active : guilds.findActiveInvites(guild.getId())) {
            if (!seen.contains(active.getCharacterName().trim().toLowerCase(Locale.ROOT))) {
                active.setActive(false);
                active.setLastSeenAt(observedAt);
                guilds.saveInvite(active);
            }
        }
    }

    private void updateCharacterSnapshot(CharacterEntity character, GuildScrapePort.Member member) {
        if (member.level() != null) character.setLevel(member.level());
        if (!isBlank(member.vocation())) {
            characters.findVocationByNameOrPromotionName(member.vocation()).ifPresent(character::setVocation);
        }
        characters.save(character);
    }

    private static String safeGuildName(Guild guild) {
        return guild == null || isBlank(guild.getName()) ? "Unknown" : guild.getName();
    }

    private static Instant toMembershipJoinedAt(GuildScrapePort.Member member, Instant observedAt) {
        if (member.joinedOn() == null) return observedAt;
        return member.joinedOn().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String normalizeDisplayName(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
