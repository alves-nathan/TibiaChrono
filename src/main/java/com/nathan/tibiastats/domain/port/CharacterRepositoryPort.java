package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.domain.model.CharacterEntity;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

public interface CharacterRepositoryPort {
    Optional<CharacterEntity> findById(Long id);
    CharacterEntity save(CharacterEntity c);
    Optional<CharacterName> findActiveName(String name);
    Optional<CharacterName> findCharacterActiveName(Long id);
    Optional<CharacterEntity> findByAnyName(String name, Instant cutoff);
    CharacterName saveName(CharacterName name);
    Optional<CharacterName> findName(String name);
    CharacterStatRecord saveStat(CharacterStatRecord r);
    List<CharacterStatRecord> findStatsBy(CharacterEntity c, StatCategory category);
}