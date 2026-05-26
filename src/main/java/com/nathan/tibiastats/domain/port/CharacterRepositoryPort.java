package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.domain.model.CharacterEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CharacterRepositoryPort {
    Optional<CharacterEntity> findById(Long id);

    CharacterEntity save(CharacterEntity character);

    Optional<CharacterName> findActiveName(String name);

    Optional<CharacterName> findCharacterActiveName(Long id);

    /** Legacy name kept for older callers. Now uses round-robin ordering instead of only missing fields. */
    List<CharacterName> findActiveNamesMissingDetails(int limit);

    /** Returns the next active character names ordered by least-recently attempted detail scrape. */
    List<CharacterName> findActiveNamesForDetailsRefresh(int limit);

    Optional<CharacterEntity> findByAnyName(String name, Instant cutoff);

    CharacterName saveName(CharacterName name);

    Optional<CharacterName> findName(String name);

    List<CharacterName> findNames(String name);

    Optional<CharacterName> findNameForCharacter(Long characterId, String name);

    List<CharacterName> findNamesForCharacter(Long characterId);

    Optional<Vocation> findVocationByNameOrPromotionName(String name);

    CharacterStatRecord saveStat(CharacterStatRecord record);

    List<CharacterStatRecord> findStatsBy(CharacterEntity character, StatCategory category);

    void markDetailsScrapeAttempt(Long characterId, Instant attemptedAt, String status, String error);

    void mergeCharacterReferences(Long fromCharacterId, Long toCharacterId);

    void deleteCharacter(Long characterId);
}
