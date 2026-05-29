package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import com.nathan.tibiastats.infrastructure.persistence.SpringGuildRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class GuildQueryService {
    private final SpringGuildRepository guilds;
    private final CharacterRepositoryPort characters;

    public GuildQueryService(SpringGuildRepository guilds, CharacterRepositoryPort characters) {
        this.guilds = guilds;
        this.characters = characters;
    }

    @Transactional(readOnly = true)
    public List<GuildView> findGuilds(String worldName, Boolean active) {
        return guilds.findGuilds(worldName, active).stream().map(GuildView::from).toList();
    }

    @Transactional(readOnly = true)
    public GuildView findGuild(String name) {
        return guilds.findGuild(name).map(GuildView::from)
                .orElseThrow(() -> new IllegalArgumentException("Guild not found: " + name));
    }

    @Transactional(readOnly = true)
    public List<GuildMemberView> findMembers(String guildName, Boolean active) {
        Guild guild = guilds.findGuild(guildName)
                .orElseThrow(() -> new IllegalArgumentException("Guild not found: " + guildName));
        return guilds.findMemberships(guild, active).stream().map(GuildMemberView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<GuildMembershipEventView> findEvents(String guildName,
                                                     String characterName,
                                                     GuildMembershipEventType type,
                                                     Instant from,
                                                     Instant to,
                                                     int limit) {
        Long guildId = guildName == null || guildName.isBlank()
                ? null
                : guilds.findGuild(guildName).map(Guild::getId)
                    .orElseThrow(() -> new IllegalArgumentException("Guild not found: " + guildName));

        Long characterId = null;
        if (characterName != null && !characterName.isBlank()) {
            characterId = characters.findByAnyName(characterName, Instant.EPOCH)
                    .orElseThrow(() -> new IllegalArgumentException("Character not found: " + characterName))
                    .getId();
        }

        return guilds.findEvents(guildId, characterId, type, from, to, limit).stream()
                .map(GuildMembershipEventView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GuildMemberView> findCharacterGuildHistory(String characterName) {
        CharacterEntity character = characters.findByAnyName(characterName, Instant.EPOCH)
                .orElseThrow(() -> new IllegalArgumentException("Character not found: " + characterName));
        return guilds.findMembershipHistory(character.getId()).stream().map(GuildMemberView::from).toList();
    }

    public record GuildView(Long id,
                            String name,
                            String world,
                            String description,
                            String homepage,
                            String logoUrl,
                            java.time.LocalDate foundedAt,
                            boolean active,
                            Instant lastSeenAt,
                            Instant lastScrapedAt) {
        static GuildView from(Guild guild) {
            return new GuildView(
                    guild.getId(),
                    guild.getName(),
                    guild.getWorld() == null ? null : guild.getWorld().getName(),
                    guild.getDescription(),
                    guild.getHomepage(),
                    guild.getLogoUrl(),
                    guild.getFoundedAt(),
                    guild.isActive(),
                    guild.getLastSeenAt(),
                    guild.getLastScrapedAt()
            );
        }
    }

    public record GuildMemberView(Long membershipId,
                                  Long guildId,
                                  String guildName,
                                  Long characterId,
                                  String characterName,
                                  String rankName,
                                  String title,
                                  String vocation,
                                  Integer level,
                                  Instant joinedAt,
                                  LocalDate joinedOn,
                                  Instant firstSeenAt,
                                  Instant lastSeenAt,
                                  Instant leftAt,
                                  boolean active) {
        static GuildMemberView from(GuildMembership membership) {
            return new GuildMemberView(
                    membership.getId(),
                    membership.getGuild() == null ? null : membership.getGuild().getId(),
                    membership.getGuild() == null ? null : membership.getGuild().getName(),
                    membership.getCharacter() == null ? null : membership.getCharacter().getId(),
                    membership.getCharacterNameSnapshot(),
                    membership.getRankName(),
                    membership.getTitle(),
                    membership.getVocation(),
                    membership.getLevel(),
                    membership.getJoinedAt(),
                    membership.getJoinedOn(),
                    membership.getFirstSeenAt(),
                    membership.getLastSeenAt(),
                    membership.getLeftAt(),
                    membership.isActive()
            );
        }
    }

    public record GuildMembershipEventView(Long id,
                                           String eventType,
                                           Long characterId,
                                           String characterName,
                                           Long fromGuildId,
                                           String fromGuildName,
                                           Long toGuildId,
                                           String toGuildName,
                                           Instant observedAt,
                                           String description) {
        static GuildMembershipEventView from(GuildMembershipEvent event) {
            return new GuildMembershipEventView(
                    event.getId(),
                    event.getEventType().name(),
                    event.getCharacter() == null ? null : event.getCharacter().getId(),
                    event.getCharacterNameSnapshot(),
                    event.getFromGuild() == null ? null : event.getFromGuild().getId(),
                    event.getFromGuild() == null ? null : event.getFromGuild().getName(),
                    event.getToGuild() == null ? null : event.getToGuild().getId(),
                    event.getToGuild() == null ? null : event.getToGuild().getName(),
                    event.getObservedAt(),
                    event.getDescription()
            );
        }
    }
}
