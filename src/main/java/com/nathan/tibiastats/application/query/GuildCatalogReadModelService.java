package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.Guild;
import com.nathan.tibiastats.infrastructure.persistence.SpringGuildRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@ReadModelComponent
public class GuildCatalogReadModelService {
    private final SpringGuildRepository guilds;

    public GuildCatalogReadModelService(SpringGuildRepository guilds) {
        this.guilds = guilds;
    }

    @Transactional(readOnly = true)
    public List<Guild> findGuilds(String worldName, Boolean active) {
        return guilds.findGuilds(worldName, active);
    }

    @Transactional(readOnly = true)
    public Guild findGuild(String name) {
        return guilds.findGuild(name)
                .orElseThrow(() -> new IllegalArgumentException("Guild not found: " + name));
    }

    @Transactional(readOnly = true)
    public Long findGuildId(String name) {
        return findGuild(name).getId();
    }
}
