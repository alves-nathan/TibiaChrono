package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.GuildMembershipEvent;
import com.nathan.tibiastats.domain.model.GuildMembershipEventType;
import com.nathan.tibiastats.infrastructure.persistence.SpringGuildRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@ReadModelComponent
public class GuildMembershipEventReadModelService {
    private final SpringGuildRepository guilds;

    public GuildMembershipEventReadModelService(SpringGuildRepository guilds) {
        this.guilds = guilds;
    }

    @Transactional(readOnly = true)
    public List<GuildMembershipEvent> findEvents(Long guildId,
                                                 Long characterId,
                                                 GuildMembershipEventType type,
                                                 Instant from,
                                                 Instant to,
                                                 int limit) {
        return guilds.findEvents(guildId, characterId, type, from, to, limit);
    }
}
