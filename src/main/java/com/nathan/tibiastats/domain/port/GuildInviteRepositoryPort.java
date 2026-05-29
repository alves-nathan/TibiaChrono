package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.GuildInvite;

import java.util.List;
import java.util.Optional;

public interface GuildInviteRepositoryPort {
    GuildInvite saveInvite(GuildInvite invite);

    Optional<GuildInvite> findActiveInvite(Long guildId, String characterName);

    List<GuildInvite> findActiveInvites(Long guildId);
}
