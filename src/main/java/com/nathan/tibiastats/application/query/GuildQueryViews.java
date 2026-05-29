package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.Guild;
import com.nathan.tibiastats.domain.model.GuildMembership;
import com.nathan.tibiastats.domain.model.GuildMembershipEvent;

import java.time.Instant;
import java.time.LocalDate;

public final class GuildQueryViews {
    private GuildQueryViews() {
    }

    public record GuildView(Long id,
                            String name,
                            String world,
                            String description,
                            String homepage,
                            String logoUrl,
                            LocalDate foundedAt,
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
