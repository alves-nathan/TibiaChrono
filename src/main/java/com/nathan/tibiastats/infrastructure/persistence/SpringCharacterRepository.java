package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface CharacterJpa extends JpaRepository<CharacterEntity, Long> {
    @Query(value = """
        select c.*
          from characters c
          join character_names n on n.character_id = c.id
         where lower(n.name) = lower(:name)
           and (
                n.active is true
                or (n.active is false and n.inactive_date > :cutoff)
           )
         order by n.active desc, n.inactive_date desc nulls first, n.id desc
         limit 1
        """, nativeQuery = true)
    Optional<CharacterEntity> findByAnyName(@Param("name") String name, @Param("cutoff") Instant cutoff);
}

interface CharacterNameJpa extends JpaRepository<CharacterName, Long> {
    @Query(value = """
        select *
          from character_names
         where lower(name) = lower(:name)
           and active is true
         order by id desc
         limit 1
        """, nativeQuery = true)
    Optional<CharacterName> findByNameAndActiveTrue(@Param("name") String name);

    @Query("select cn from CharacterName cn where cn.character.id = :charId and cn.active = true")
    Optional<CharacterName> findCharacterActiveName(@Param("charId") Long charId);

    @Query(value = """
        select *
          from character_names
         where lower(name) = lower(:name)
         order by active desc, inactive_date desc nulls first, id desc
        """, nativeQuery = true)
    List<CharacterName> findAllByNormalizedName(@Param("name") String name);

    @Query(value = """
        select *
          from character_names
         where lower(name) = lower(:name)
         order by active desc, inactive_date desc nulls first, id desc
         limit 1
        """, nativeQuery = true)
    Optional<CharacterName> findName(@Param("name") String name);

    @Query(value = """
        select *
          from character_names
         where character_id = :characterId
           and lower(name) = lower(:name)
         order by active desc, inactive_date desc nulls first, id desc
         limit 1
        """, nativeQuery = true)
    Optional<CharacterName> findNameForCharacter(@Param("characterId") Long characterId, @Param("name") String name);

    @Query(value = """
        select *
          from character_names
         where character_id = :characterId
         order by active desc, inactive_date desc nulls first, id desc
        """, nativeQuery = true)
    List<CharacterName> findNamesForCharacter(@Param("characterId") Long characterId);

    @Query("""
        select cn
        from CharacterName cn
        join fetch cn.character c
        where cn.active = true
        order by
          case when c.detailsLastScrapedAt is null then 0 else 1 end asc,
          c.detailsLastScrapedAt asc,
          c.id asc
        """)
    List<CharacterName> findActiveNamesForDetailsRefresh(Pageable pageable);
}

interface VocationJpa extends JpaRepository<Vocation, Integer> {
    @Query("""
        select v
        from Vocation v
        where lower(v.name) = lower(:name)
           or lower(v.promotionName) = lower(:name)
        """)
    Optional<Vocation> findByNameOrPromotionName(@Param("name") String name);
}

interface CharacterStatJpa extends JpaRepository<CharacterStatRecord, Long> {
    @Query("select r from CharacterStatRecord r where r.character = :character and r.category = :category order by r.date asc")
    List<CharacterStatRecord> findByCat(@Param("character") CharacterEntity character, @Param("category") StatCategory category);
}

interface CharacterReferenceMaintenanceJpa extends JpaRepository<CharacterEntity, Long> {
    @Modifying
    @Transactional
    @Query(value = "update scrape_players set character_id = :toId where character_id = :fromId", nativeQuery = true)
    void reassignScrapePlayers(@Param("fromId") Long fromId, @Param("toId") Long toId);

    @Modifying
    @Transactional
    @Query(value = "update character_worlds set character_id = :toId where character_id = :fromId", nativeQuery = true)
    void reassignCharacterWorlds(@Param("fromId") Long fromId, @Param("toId") Long toId);

    @Modifying
    @Transactional
    @Query(value = "update character_deaths set character_id = :toId where character_id = :fromId", nativeQuery = true)
    void reassignCharacterDeaths(@Param("fromId") Long fromId, @Param("toId") Long toId);

    @Modifying
    @Transactional
    @Query(value = "update guild_characters set character_id = :toId where character_id = :fromId", nativeQuery = true)
    void reassignGuildCharacters(@Param("fromId") Long fromId, @Param("toId") Long toId);

    @Modifying
    @Transactional
    @Query(value = "update character_statrecords set character_id = :toId where character_id = :fromId", nativeQuery = true)
    void reassignCharacterStatRecords(@Param("fromId") Long fromId, @Param("toId") Long toId);

    @Modifying
    @Transactional
    @Query(value = """
        update character_names source
           set character_id = :toId
         where source.character_id = :fromId
           and not exists (
               select 1
                 from character_names target
                where target.character_id = :toId
                  and lower(target.name) = lower(source.name)
           )
        """, nativeQuery = true)
    void reassignNonDuplicateCharacterNames(@Param("fromId") Long fromId, @Param("toId") Long toId);

    @Modifying
    @Transactional
    @Query(value = "delete from character_names where character_id = :fromId", nativeQuery = true)
    void deleteRemainingCharacterNames(@Param("fromId") Long fromId);

    @Modifying
    @Transactional
    @Query(value = "delete from characters where id = :characterId", nativeQuery = true)
    void deleteCharacter(@Param("characterId") Long characterId);
}

@Repository
public class SpringCharacterRepository implements CharacterRepositoryPort {
    private final CharacterNameJpa names;
    private final CharacterJpa chars;
    private final CharacterStatJpa stats;
    private final VocationJpa vocations;
    private final CharacterReferenceMaintenanceJpa maintenance;

    public SpringCharacterRepository(CharacterNameJpa names,
                                     CharacterJpa chars,
                                     CharacterStatJpa stats,
                                     VocationJpa vocations,
                                     CharacterReferenceMaintenanceJpa maintenance) {
        this.names = names;
        this.chars = chars;
        this.stats = stats;
        this.vocations = vocations;
        this.maintenance = maintenance;
    }

    @Override
    public CharacterName saveName(CharacterName name) {
        return names.save(name);
    }

    @Override
    public Optional<CharacterName> findName(String name) {
        return names.findName(CharacterNameNormalizer.normalize(name));
    }

    @Override
    public List<CharacterName> findNames(String name) {
        return names.findAllByNormalizedName(CharacterNameNormalizer.normalize(name));
    }

    @Override
    public Optional<CharacterName> findNameForCharacter(Long characterId, String name) {
        return names.findNameForCharacter(characterId, CharacterNameNormalizer.normalize(name));
    }

    @Override
    public List<CharacterName> findNamesForCharacter(Long characterId) {
        return names.findNamesForCharacter(characterId);
    }

    @Override
    public Optional<CharacterName> findCharacterActiveName(Long id) {
        return names.findCharacterActiveName(id);
    }

    @Override
    public Optional<CharacterEntity> findByAnyName(String name, Instant cutoff) {
        return chars.findByAnyName(CharacterNameNormalizer.normalize(name), cutoff);
    }

    @Override
    public List<CharacterName> findActiveNamesMissingDetails(int limit) {
        return findActiveNamesForDetailsRefresh(limit);
    }

    @Override
    public List<CharacterName> findActiveNamesForDetailsRefresh(int limit) {
        return names.findActiveNamesForDetailsRefresh(PageRequest.of(0, Math.max(1, limit)));
    }

    @Override
    public Optional<CharacterEntity> findById(Long id) {
        return chars.findById(id);
    }

    @Override
    public CharacterEntity save(CharacterEntity character) {
        return chars.save(character);
    }

    @Override
    public CharacterStatRecord saveStat(CharacterStatRecord record) {
        return stats.save(record);
    }

    @Override
    public List<CharacterStatRecord> findStatsBy(CharacterEntity character, StatCategory category) {
        return stats.findByCat(character, category);
    }

    @Override
    public Optional<CharacterName> findActiveName(String name) {
        return names.findByNameAndActiveTrue(CharacterNameNormalizer.normalize(name));
    }

    @Override
    public Optional<Vocation> findVocationByNameOrPromotionName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return vocations.findByNameOrPromotionName(name.trim());
    }

    @Override
    @Transactional
    public void markDetailsScrapeAttempt(Long characterId, Instant attemptedAt, String status, String error) {
        if (characterId == null) {
            return;
        }
        chars.findById(characterId).ifPresent(character -> {
            character.setDetailsLastScrapedAt(attemptedAt);
            character.setDetailsLastScrapeStatus(status);
            character.setDetailsLastScrapeError(truncate(error, 2000));
            chars.save(character);
        });
    }

    @Override
    @Transactional
    public void mergeCharacterReferences(Long fromCharacterId, Long toCharacterId) {
        if (fromCharacterId == null || toCharacterId == null || fromCharacterId.equals(toCharacterId)) {
            return;
        }

        maintenance.reassignScrapePlayers(fromCharacterId, toCharacterId);
        maintenance.reassignCharacterWorlds(fromCharacterId, toCharacterId);
        maintenance.reassignCharacterDeaths(fromCharacterId, toCharacterId);
        maintenance.reassignGuildCharacters(fromCharacterId, toCharacterId);
        maintenance.reassignCharacterStatRecords(fromCharacterId, toCharacterId);
        maintenance.reassignNonDuplicateCharacterNames(fromCharacterId, toCharacterId);
        maintenance.deleteRemainingCharacterNames(fromCharacterId);
    }

    @Override
    @Transactional
    public void deleteCharacter(Long characterId) {
        maintenance.deleteCharacter(characterId);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
