package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.*;
import java.util.Optional;
import java.util.List;

public interface CharacterRepositoryPort {
    Optional<CharacterName> findActiveName(String name);
    CharacterName saveName(CharacterName name);

    CharacterEntity saveCharacter(CharacterEntity c);
    Optional<CharacterEntity> findByActiveName(String name);

    CharacterStatRecord saveStat(CharacterStatRecord r);
    List<CharacterStatRecord> findStatsBy(CharacterEntity c, StatCategory category);
}