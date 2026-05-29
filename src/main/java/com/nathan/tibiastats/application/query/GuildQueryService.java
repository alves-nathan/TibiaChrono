package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.GuildMembershipEventType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class GuildQueryService {
    private final GuildCatalogReadModelService catalog;
    private final GuildMembershipReadModelService memberships;
    private final GuildMembershipEventReadModelService events;

    public GuildQueryService(GuildCatalogReadModelService catalog,
                             GuildMembershipReadModelService memberships,
                             GuildMembershipEventReadModelService events) {
        this.catalog = catalog;
        this.memberships = memberships;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<GuildQueryViews.GuildView> findGuilds(String worldName, Boolean active) {
        return catalog.findGuilds(worldName, active).stream().map(GuildQueryViews.GuildView::from).toList();
    }

    @Transactional(readOnly = true)
    public GuildQueryViews.GuildView findGuild(String name) {
        return GuildQueryViews.GuildView.from(catalog.findGuild(name));
    }

    @Transactional(readOnly = true)
    public List<GuildQueryViews.GuildMemberView> findMembers(String guildName, Boolean active) {
        Long guildId = catalog.findGuildId(guildName);
        return memberships.findMembers(guildId, active).stream().map(GuildQueryViews.GuildMemberView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<GuildQueryViews.GuildMembershipEventView> findEvents(String guildName,
                                                                     String characterName,
                                                                     GuildMembershipEventType type,
                                                                     Instant from,
                                                                     Instant to,
                                                                     int limit) {
        Long guildId = guildName == null || guildName.isBlank()
                ? null
                : catalog.findGuildId(guildName);

        Long characterId = characterName == null || characterName.isBlank()
                ? null
                : memberships.findCharacterId(characterName);

        return events.findEvents(guildId, characterId, type, from, to, limit).stream()
                .map(GuildQueryViews.GuildMembershipEventView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GuildQueryViews.GuildMemberView> findCharacterGuildHistory(String characterName) {
        return memberships.findCharacterGuildHistory(characterName).stream()
                .map(GuildQueryViews.GuildMemberView::from)
                .toList();
    }
}
