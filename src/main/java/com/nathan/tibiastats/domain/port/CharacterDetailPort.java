package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.CharacterEntity;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CharacterDetailPort {
    record NameDetails(String currentName, List<String> formerNames) {}

    record CharacterDetails(
            String currentName,
            List<String> formerNames,
            CharacterEntity.Sex sex,
            String vocation,
            Integer level,
            Integer achievementPoints,
            String residence,
            OffsetDateTime lastLogin,
            String accountStatus,
            Instant creationDate,
            String world
    ) {}

    NameDetails fetchNameDetails(String worldName, String characterName);

    Optional<CharacterDetails> fetchCharacterDetails(String characterName);
}
