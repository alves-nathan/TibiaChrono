package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.Guild;
import com.nathan.tibiastats.domain.model.GuildMembership;

import java.util.List;
import java.util.Optional;

public interface GuildMembershipRepositoryPort {
    GuildMembership saveMembership(GuildMembership membership);

    GuildMembership saveAndFlushMembership(GuildMembership membership);

    void flushMemberships();

    List<GuildMembership> findActiveMemberships(Guild guild);

    Optional<GuildMembership> findActiveMembershipForCharacter(Long characterId);

    List<GuildMembership> findMemberships(Guild guild, Boolean active);

    List<GuildMembership> findMemberships(Long guildId, Boolean active);

    List<GuildMembership> findMembershipHistory(Long characterId);
}
