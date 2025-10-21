package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.domain.model.Character;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

public interface CharacterRepositoryPort {
    Optional<Character> findById(Long id);
    Character save(Character c);
    Optional<CharacterName> findActiveName(String name);
    Optional<Character> findByAnyName(String name);
    CharacterName saveName(CharacterName name);
    Optional<Character> findByName(String name);
    List<CharacterName> findCharacterNamesNewerThan(Long characterId, Instant cutoff);
    CharacterStatRecord saveStat(CharacterStatRecord r);
    List<CharacterStatRecord> findStatsBy(Character c, StatCategory category);

    void deactivateName(CharacterName name);
}