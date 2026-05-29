package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.GuildMembershipEvent;
import com.nathan.tibiastats.domain.model.GuildMembershipEventType;

import java.time.Instant;
import java.util.List;

public interface GuildMembershipEventRepositoryPort {
    GuildMembershipEvent saveEvent(GuildMembershipEvent event);

    List<GuildMembershipEvent> findEvents(Long guildId,
                                          Long characterId,
                                          GuildMembershipEventType type,
                                          Instant from,
                                          Instant to,
                                          int limit);
}
