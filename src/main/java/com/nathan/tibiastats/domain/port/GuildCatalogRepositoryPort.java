package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.Guild;

import java.util.List;
import java.util.Optional;

public interface GuildCatalogRepositoryPort {
    Optional<Guild> findGuild(String name);

    Guild saveGuild(Guild guild);

    List<Guild> findGuilds(String worldName, Boolean active);

    List<Guild> findActiveForDetailsRefresh(int limit);
}
