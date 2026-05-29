package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.GuildMembership;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import com.nathan.tibiastats.domain.port.GuildMembershipRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@ReadModelComponent
public class GuildMembershipReadModelService {
    private final GuildMembershipRepositoryPort guilds;
    private final CharacterRepositoryPort characters;

    public GuildMembershipReadModelService(GuildMembershipRepositoryPort guilds, CharacterRepositoryPort characters) {
        this.guilds = guilds;
        this.characters = characters;
    }

    @Transactional(readOnly = true)
    public List<GuildMembership> findMembers(Long guildId, Boolean active) {
        return guilds.findMemberships(guildId, active);
    }

    @Transactional(readOnly = true)
    public List<GuildMembership> findCharacterGuildHistory(String characterName) {
        CharacterEntity character = resolveCharacter(characterName);
        return guilds.findMembershipHistory(character.getId());
    }

    @Transactional(readOnly = true)
    public Long findCharacterId(String characterName) {
        return resolveCharacter(characterName).getId();
    }

    private CharacterEntity resolveCharacter(String characterName) {
        return characters.findByAnyName(characterName, Instant.EPOCH)
                .orElseThrow(() -> new IllegalArgumentException("Character not found: " + characterName));
    }
}
