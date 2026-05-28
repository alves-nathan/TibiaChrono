#!/usr/bin/env bash
set -euo pipefail

if [ ! -f "pom.xml" ] || [ ! -d "src/main/java/com/nathan/tibiastats" ]; then
  echo "Execute este script na raiz do projeto TibiaChrono, onde fica o pom.xml." >&2
  exit 1
fi

BACKUP_DIR=".tibiachrono-rename-rules-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"

backup_file() {
  local file="$1"
  if [ -f "$file" ]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$file" "$BACKUP_DIR/$file"
  fi
}

backup_file "src/main/java/com/nathan/tibiastats/domain/model/CharacterName.java"
backup_file "src/main/java/com/nathan/tibiastats/domain/port/CharacterRepositoryPort.java"
backup_file "src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java"
backup_file "src/main/java/com/nathan/tibiastats/application/service/CharacterNamingService.java"
backup_file "src/main/java/com/nathan/tibiastats/application/service/CharacterDetailsService.java"
backup_file "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java"
backup_file "src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/graphql/StatsGraphQLController.java"
backup_file "src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java"

mkdir -p src/main/java/com/nathan/tibiastats/domain/model
cat > src/main/java/com/nathan/tibiastats/domain/model/CharacterName.java <<'JAVA'
package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Entity
@Table(name = "character_names")
public class CharacterName {
    /**
     * Dynamic cutoff used when resolving former names. Former names are only a safe
     * identity alias for 6 months; after that CipSoft may release/reuse the name.
     */
    public static Instant inactiveHorizon() {
        return ZonedDateTime.now(ZoneOffset.UTC).minusMonths(6).toInstant();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "character_id")
    private CharacterEntity character;

    @Column(nullable = false)
    private String name;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "inactive_date")
    private Instant inactiveDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getInactiveDate() {
        return inactiveDate;
    }

    public void setInactiveDate(Instant inactiveDate) {
        this.inactiveDate = inactiveDate;
    }

    public CharacterEntity getCharacter() {
        return character;
    }

    public void setCharacter(CharacterEntity character) {
        this.character = character;
    }

    public static CharacterName createActive(String name, CharacterEntity character) {
        CharacterName characterName = new CharacterName();
        characterName.setName(name);
        characterName.setCharacter(character);
        characterName.setActive(true);
        characterName.setInactiveDate(null);
        return characterName;
    }

    public static CharacterName createInactive(String name, CharacterEntity character, Instant inactiveDate) {
        CharacterName characterName = new CharacterName();
        characterName.setName(name);
        characterName.setCharacter(character);
        characterName.setActive(false);
        characterName.setInactiveDate(inactiveDate);
        return characterName;
    }

    public void activate() {
        this.setActive(true);
        this.setInactiveDate(null);
    }

    public void deactivate(Instant when) {
        this.setActive(false);
        this.setInactiveDate(when);
    }
}
JAVA

mkdir -p src/main/java/com/nathan/tibiastats/domain/port
cat > src/main/java/com/nathan/tibiastats/domain/port/CharacterRepositoryPort.java <<'JAVA'
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

    List<CharacterName> findActiveNamesMissingDetails(int limit);

    Optional<CharacterEntity> findByAnyName(String name, Instant cutoff);

    CharacterName saveName(CharacterName name);

    Optional<CharacterName> findName(String name);

    List<CharacterName> findNames(String name);

    Optional<CharacterName> findNameForCharacter(Long characterId, String name);

    List<CharacterName> findNamesForCharacter(Long characterId);

    Optional<Vocation> findVocationByNameOrPromotionName(String name);

    CharacterStatRecord saveStat(CharacterStatRecord record);

    List<CharacterStatRecord> findStatsBy(CharacterEntity character, StatCategory category);

    void mergeCharacterReferences(Long fromCharacterId, Long toCharacterId);

    void deleteCharacter(Long characterId);
}
JAVA

mkdir -p src/main/java/com/nathan/tibiastats/infrastructure/persistence
cat > src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java <<'JAVA'
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
          and (
               c.sex is null
            or c.vocation is null
            or c.level is null
            or c.residence is null
            or c.accStatus is null
            or c.creationDate is null
          )
        order by c.id asc
        """)
    List<CharacterName> findActiveNamesMissingDetails(Pageable pageable);
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
        return names.findName(name);
    }

    @Override
    public List<CharacterName> findNames(String name) {
        return names.findAllByNormalizedName(name);
    }

    @Override
    public Optional<CharacterName> findNameForCharacter(Long characterId, String name) {
        return names.findNameForCharacter(characterId, name);
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
        return chars.findByAnyName(name, cutoff);
    }

    @Override
    public List<CharacterName> findActiveNamesMissingDetails(int limit) {
        return names.findActiveNamesMissingDetails(PageRequest.of(0, Math.max(1, limit)));
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
        return names.findByNameAndActiveTrue(name);
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
}
JAVA

mkdir -p src/main/java/com/nathan/tibiastats/application/service
cat > src/main/java/com/nathan/tibiastats/application/service/CharacterNamingService.java <<'JAVA'
package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
public class CharacterNamingService {
    private static final Logger log = LoggerFactory.getLogger(CharacterNamingService.class);
    private static final int FORMER_NAME_SAFE_RESOLUTION_MONTHS = 6;

    private final CharacterRepositoryPort repo;

    public CharacterNamingService(CharacterRepositoryPort repo) {
        this.repo = repo;
    }

    /**
     * Resolve a name observed in scrapes. If formerNames came from an official profile,
     * the current name and former names are reconciled as the same identity.
     */
    @Transactional
    public CharacterEntity ensureCharacterForName(String name, String formerNames) {
        List<String> parsedFormerNames = parseFormerNames(formerNames, name);

        if (parsedFormerNames.isEmpty()) {
            return resolveObservedName(name);
        }

        return reconcileOfficialNames(name, parsedFormerNames);
    }

    /**
     * Resolve a plain mention. Inactive former names only resolve automatically inside
     * the 6-month safe window; after that CipSoft may release/reuse the name.
     */
    @Transactional
    public CharacterEntity resolveObservedName(String observedName) {
        String normalizedName = normalizeName(observedName);

        return repo.findByAnyName(normalizedName, formerNameCutoff())
                .orElseGet(() -> createCharacterWithActiveName(normalizedName));
    }

    @Transactional
    public CharacterEntity reconcileOfficialNames(String currentName, List<String> formerNames) {
        return reconcileOfficialNames(null, currentName, formerNames);
    }

    /**
     * Official Tibia.com profile rule:
     * Name + Former Names refer to the same CharacterEntity and must not create duplicates.
     */
    @Transactional
    public CharacterEntity reconcileOfficialNames(CharacterEntity knownCharacter, String currentName, List<String> formerNames) {
        String normalizedCurrentName = normalizeName(currentName);
        List<String> normalizedFormerNames = normalizeFormerNames(formerNames, normalizedCurrentName);

        CharacterEntity canonical = findCanonicalCharacter(knownCharacter, normalizedCurrentName, normalizedFormerNames)
                .orElseGet(() -> createCharacterWithActiveName(normalizedCurrentName));

        canonical = mergeDuplicateCandidatesIntoCanonical(canonical, knownCharacter, normalizedCurrentName, normalizedFormerNames);

        Instant now = Instant.now();
        deactivateActiveNamesExcept(canonical, normalizedCurrentName, now);
        ensureActiveName(canonical, normalizedCurrentName);

        for (String formerName : normalizedFormerNames) {
            ensureInactiveName(canonical, formerName, now);
        }

        return repo.save(canonical);
    }

    /** Existing entry point kept for older callers. */
    @Transactional
    public void handleRenamed(CharacterEntity character, String newActiveName, CharacterName oldName) {
        List<String> formerNames = oldName == null ? List.of() : List.of(oldName.getName());
        reconcileOfficialNames(character, newActiveName, formerNames);
    }

    private Optional<CharacterEntity> findCanonicalCharacter(CharacterEntity knownCharacter,
                                                            String currentName,
                                                            List<String> formerNames) {
        List<CharacterEntity> candidates = findIdentityCandidates(knownCharacter, currentName, formerNames);

        return candidates.stream()
                .filter(c -> c.getId() != null)
                .min((a, b) -> Long.compare(a.getId(), b.getId()));
    }

    private CharacterEntity mergeDuplicateCandidatesIntoCanonical(CharacterEntity canonical,
                                                                  CharacterEntity knownCharacter,
                                                                  String currentName,
                                                                  List<String> formerNames) {
        List<CharacterEntity> candidates = findIdentityCandidates(knownCharacter, currentName, formerNames);
        CharacterEntity managedCanonical = reload(canonical);

        for (CharacterEntity candidate : candidates) {
            if (candidate.getId() == null || candidate.getId().equals(managedCanonical.getId())) {
                continue;
            }

            CharacterEntity duplicate = reload(candidate);
            copyMissingDetails(managedCanonical, duplicate);
            repo.save(managedCanonical);

            log.warn(
                    "[CHARACTER_NAMING] Merging duplicated character fromCharacterId={} into toCharacterId={} based on official names currentName='{}', formerNames={}",
                    duplicate.getId(), managedCanonical.getId(), currentName, formerNames
            );

            repo.mergeCharacterReferences(duplicate.getId(), managedCanonical.getId());
            repo.deleteCharacter(duplicate.getId());
            managedCanonical = reload(managedCanonical);
        }

        return managedCanonical;
    }

    private List<CharacterEntity> findIdentityCandidates(CharacterEntity knownCharacter,
                                                         String currentName,
                                                         List<String> formerNames) {
        LinkedHashSet<Long> seenIds = new LinkedHashSet<>();
        List<CharacterEntity> candidates = new ArrayList<>();

        addCandidate(candidates, seenIds, knownCharacter);
        repo.findByAnyName(currentName, formerNameCutoff()).ifPresent(c -> addCandidate(candidates, seenIds, c));

        for (String formerName : formerNames) {
            repo.findByAnyName(formerName, formerNameCutoff()).ifPresent(c -> addCandidate(candidates, seenIds, c));
        }

        return candidates;
    }

    private void addCandidate(List<CharacterEntity> candidates, LinkedHashSet<Long> seenIds, CharacterEntity candidate) {
        if (candidate == null || candidate.getId() == null || !seenIds.add(candidate.getId())) {
            return;
        }
        candidates.add(candidate);
    }

    private CharacterEntity createCharacterWithActiveName(String name) {
        CharacterEntity character = repo.save(new CharacterEntity());
        CharacterName characterName = CharacterName.createActive(name, character);
        repo.saveName(characterName);
        character.addName(characterName);
        return repo.save(character);
    }

    private CharacterEntity reload(CharacterEntity character) {
        if (character == null || character.getId() == null) {
            return character;
        }
        return repo.findById(character.getId()).orElse(character);
    }

    private void deactivateActiveNamesExcept(CharacterEntity character, String activeName, Instant when) {
        for (CharacterName name : repo.findNamesForCharacter(character.getId())) {
            if (name.isActive() && !sameName(name.getName(), activeName)) {
                name.deactivate(when);
                repo.saveName(name);
            }
        }
    }

    private void ensureActiveName(CharacterEntity character, String activeName) {
        Optional<CharacterName> existingForCharacter = repo.findNameForCharacter(character.getId(), activeName);

        if (existingForCharacter.isPresent()) {
            CharacterName existing = existingForCharacter.get();
            if (!existing.isActive()) {
                existing.activate();
                repo.saveName(existing);
            }
            return;
        }

        CharacterName newActiveName = CharacterName.createActive(activeName, character);
        repo.saveName(newActiveName);
        character.addName(newActiveName);
    }

    private void ensureInactiveName(CharacterEntity character, String formerName, Instant when) {
        if (isBlank(formerName)) {
            return;
        }

        Optional<CharacterName> existingForCharacter = repo.findNameForCharacter(character.getId(), formerName);

        if (existingForCharacter.isPresent()) {
            CharacterName existing = existingForCharacter.get();
            if (existing.isActive()) {
                existing.deactivate(when);
                repo.saveName(existing);
            }
            return;
        }

        CharacterName inactiveName = CharacterName.createInactive(formerName, character, when);
        repo.saveName(inactiveName);
        character.addName(inactiveName);
    }

    private void copyMissingDetails(CharacterEntity target, CharacterEntity source) {
        if (target.getSex() == null) target.setSex(source.getSex());
        if (target.getVocation() == null) target.setVocation(source.getVocation());
        if (target.getLevel() == null) target.setLevel(source.getLevel());
        if (target.getAchievementPoints() == null) target.setAchievementPoints(source.getAchievementPoints());
        if (target.getResidence() == null) target.setResidence(source.getResidence());
        if (target.getLastLogin() == null) target.setLastLogin(source.getLastLogin());
        if (target.getAccStatus() == null) target.setAccStatus(source.getAccStatus());
        if (target.getCreationDate() == null) target.setCreationDate(source.getCreationDate());
    }

    private Instant formerNameCutoff() {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .minusMonths(FORMER_NAME_SAFE_RESOLUTION_MONTHS)
                .toInstant();
    }

    private List<String> parseFormerNames(String formerNames, String currentName) {
        if (isBlank(formerNames)) {
            return List.of();
        }

        String normalizedCurrentName = normalizeName(currentName);
        List<String> parsed = new ArrayList<>();

        for (String part : formerNames.split(",")) {
            String normalized = normalizeName(part);
            if (!isBlank(normalized) && !sameName(normalized, normalizedCurrentName)) {
                parsed.add(normalized);
            }
        }

        return normalizeFormerNames(parsed, normalizedCurrentName);
    }

    private List<String> normalizeFormerNames(List<String> formerNames, String currentName) {
        if (formerNames == null || formerNames.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String formerName : formerNames) {
            String value = normalizeName(formerName);
            if (!isBlank(value) && !sameName(value, currentName)) {
                normalized.add(value);
            }
        }

        return List.copyOf(normalized);
    }

    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceAll("\\s+", " ");
    }

    private boolean sameName(String a, String b) {
        return Objects.equals(normalizeName(a).toLowerCase(Locale.ROOT), normalizeName(b).toLowerCase(Locale.ROOT));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
JAVA

cat > src/main/java/com/nathan/tibiastats/application/service/CharacterDetailsService.java <<'JAVA'
package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.AppProperties;
import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.model.Vocation;
import com.nathan.tibiastats.domain.port.CharacterDetailPort;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;

@Service
public class CharacterDetailsService {
    private static final Logger log = LoggerFactory.getLogger(CharacterDetailsService.class);
    private static final String LOG_PREFIX = "[CHARACTER_DETAILS_SCRAPER]";

    private final CharacterRepositoryPort characterRepo;
    private final CharacterDetailPort characterDetailPort;
    private final CharacterNamingService characterNamingService;
    private final AppProperties appProperties;
    private final TransactionTemplate transactionTemplate;

    public CharacterDetailsService(CharacterRepositoryPort characterRepo,
                                   CharacterDetailPort characterDetailPort,
                                   CharacterNamingService characterNamingService,
                                   AppProperties appProperties,
                                   TransactionTemplate transactionTemplate) {
        this.characterRepo = characterRepo;
        this.characterDetailPort = characterDetailPort;
        this.characterNamingService = characterNamingService;
        this.appProperties = appProperties;
        this.transactionTemplate = transactionTemplate;
    }

    public void updateMissingDetailsBatch() {
        int batchSize = Math.max(1, appProperties.getCharacterDetails().getBatchSize());
        List<CharacterName> pendingNames = characterRepo.findActiveNamesMissingDetails(batchSize);

        if (pendingNames.isEmpty()) {
            log.info("{} No characters pending detail scrape", LOG_PREFIX);
            return;
        }

        log.info("{} Starting character detail scrape batch: pendingNames={}, batchSize={}", LOG_PREFIX, pendingNames.size(), batchSize);

        int updated = 0;
        int skipped = 0;
        int failed = 0;

        for (CharacterName characterName : pendingNames) {
            Long characterId = characterName.getCharacter().getId();
            String activeName = characterName.getName();

            try {
                var details = characterDetailPort.fetchCharacterDetails(activeName);
                if (details.isEmpty()) {
                    skipped++;
                    log.warn("{} Character details not found for {}. Skipping for now.", LOG_PREFIX, activeName);
                    continue;
                }

                var characterDetails = details.get();
                if (!hasAnyUsefulDetail(characterDetails)) {
                    skipped++;
                    log.warn(
                            "{} Character details page was fetched for {}, but parser found no useful fields. currentName={}, world={}",
                            LOG_PREFIX,
                            activeName,
                            characterDetails.currentName(),
                            characterDetails.world()
                    );
                    continue;
                }

                boolean changed = saveCharacterDetails(characterId, activeName, characterDetails);
                if (changed) {
                    updated++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.error("{} Failed to scrape character details for {}. Continuing with next character.", LOG_PREFIX, activeName, e);
            }
        }

        log.info("{} Finished character detail scrape batch: updated={}, skipped={}, failed={}", LOG_PREFIX, updated, skipped, failed);
    }

    private boolean saveCharacterDetails(Long characterId, String requestedName, CharacterDetailPort.CharacterDetails details) {
        Boolean changed = transactionTemplate.execute(status -> {
            CharacterEntity originalCharacter = characterRepo.findById(characterId).orElse(null);
            if (originalCharacter == null) {
                return false;
            }

            String officialCurrentName = firstNonBlank(details.currentName(), requestedName);
            CharacterEntity character = characterNamingService.reconcileOfficialNames(
                    originalCharacter,
                    officialCurrentName,
                    details.formerNames()
            );

            boolean characterChanged = !Objects.equals(originalCharacter.getId(), character.getId());

            if (details.sex() != null && !Objects.equals(character.getSex(), details.sex())) {
                character.setSex(details.sex());
                characterChanged = true;
            }

            if (details.level() != null && !Objects.equals(character.getLevel(), details.level())) {
                character.setLevel(details.level());
                characterChanged = true;
            }

            if (details.vocation() != null && !details.vocation().isBlank()) {
                var vocation = characterRepo.findVocationByNameOrPromotionName(details.vocation().trim());
                if (vocation.isPresent() && !sameVocation(character.getVocation(), vocation.get())) {
                    character.setVocation(vocation.get());
                    characterChanged = true;
                }
            }

            if (details.achievementPoints() != null
                    && !Objects.equals(character.getAchievementPoints(), details.achievementPoints())) {
                character.setAchievementPoints(details.achievementPoints());
                characterChanged = true;
            }

            if (isNonBlank(details.residence()) && !Objects.equals(character.getResidence(), details.residence().trim())) {
                character.setResidence(details.residence().trim());
                characterChanged = true;
            }

            if (details.lastLogin() != null && !Objects.equals(character.getLastLogin(), details.lastLogin())) {
                character.setLastLogin(details.lastLogin());
                characterChanged = true;
            }

            if (isNonBlank(details.accountStatus()) && !Objects.equals(character.getAccStatus(), details.accountStatus().trim())) {
                character.setAccStatus(details.accountStatus().trim());
                characterChanged = true;
            }

            if (details.creationDate() != null && !Objects.equals(character.getCreationDate(), details.creationDate())) {
                character.setCreationDate(details.creationDate());
                characterChanged = true;
            }

            if (characterChanged) {
                characterRepo.save(character);
                log.info(
                        "{} Saved details for characterId={} officialName='{}' formerNames={}: sex={}, vocation={}, level={}, residence={}, status={}, created={}",
                        LOG_PREFIX,
                        character.getId(),
                        officialCurrentName,
                        details.formerNames(),
                        character.getSex(),
                        character.getVocation() != null ? character.getVocation().getName() : null,
                        character.getLevel(),
                        character.getResidence(),
                        character.getAccStatus(),
                        character.getCreationDate()
                );
            } else {
                log.debug("{} No changed fields for characterId={}", LOG_PREFIX, character.getId());
            }

            return characterChanged;
        });

        return Boolean.TRUE.equals(changed);
    }

    private boolean hasAnyUsefulDetail(CharacterDetailPort.CharacterDetails details) {
        return isNonBlank(details.currentName())
                || (details.formerNames() != null && !details.formerNames().isEmpty())
                || details.sex() != null
                || details.vocation() != null
                || details.level() != null
                || details.achievementPoints() != null
                || isNonBlank(details.residence())
                || details.lastLogin() != null
                || isNonBlank(details.accountStatus())
                || details.creationDate() != null
                || isNonBlank(details.world());
    }

    private boolean sameVocation(Vocation current, Vocation scraped) {
        return current != null && scraped != null && Objects.equals(current.getId(), scraped.getId());
    }

    private String firstNonBlank(String first, String fallback) {
        return isNonBlank(first) ? first.trim() : fallback;
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.trim().isBlank();
    }
}
JAVA

mkdir -p src/main/resources/db/migration
cat > src/main/resources/db/migration/V5__character_name_identity_rules.sql <<'SQL'
-- Character name identity rules
--
-- A former name is a safe identity alias for 6 months after rename, but after that
-- CipSoft may release the name for another character. Therefore character_names.name
-- cannot stay globally unique forever. Only one active owner per name is enforced.

ALTER TABLE character_names
    DROP CONSTRAINT IF EXISTS character_names_name_key;

UPDATE character_names
   SET active = false,
       inactive_date = COALESCE(inactive_date, now())
 WHERE active IS NULL;

-- Remove duplicate rows for the same character/name pair before creating the unique index.
DELETE FROM character_names newer
USING character_names older
WHERE newer.id > older.id
  AND newer.character_id = older.character_id
  AND lower(newer.name) = lower(older.name);

-- Safety guard: if duplicated active names exist, keep only the newest active owner and
-- turn the others into inactive historical names so the partial unique index can be created.
WITH ranked_active_names AS (
    SELECT id,
           row_number() OVER (PARTITION BY lower(name) ORDER BY id DESC) AS rn
      FROM character_names
     WHERE active IS TRUE
)
UPDATE character_names cn
   SET active = false,
       inactive_date = COALESCE(inactive_date, now())
  FROM ranked_active_names ranked
 WHERE cn.id = ranked.id
   AND ranked.rn > 1;

CREATE INDEX IF NOT EXISTS idx_character_names_name_lower
    ON character_names (lower(name));

CREATE INDEX IF NOT EXISTS idx_character_names_resolution
    ON character_names (lower(name), active, inactive_date);

CREATE UNIQUE INDEX IF NOT EXISTS ux_character_names_active_name_lower
    ON character_names (lower(name))
    WHERE active IS TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS ux_character_names_character_name_lower
    ON character_names (character_id, lower(name));
SQL


# Replace old static cutoff usage with a dynamic cutoff method.
# This keeps existing controllers/services compiling without freezing the 6-month window at app startup.
find src/main/java -type f -name '*.java' -print0 | xargs -0 sed -i 's/CharacterName\.INACTIVE_HORIZON/CharacterName.inactiveHorizon()/g'

# Keep the files as regular source files; previous scripts may have made them executable.
chmod 0644 \
  src/main/java/com/nathan/tibiastats/domain/model/CharacterName.java \
  src/main/java/com/nathan/tibiastats/domain/port/CharacterRepositoryPort.java \
  src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java \
  src/main/java/com/nathan/tibiastats/application/service/CharacterNamingService.java \
  src/main/java/com/nathan/tibiastats/application/service/CharacterDetailsService.java \
  src/main/resources/db/migration/V5__character_name_identity_rules.sql

echo "Correção aplicada. Backup salvo em: $BACKUP_DIR"
echo "Próximos passos sugeridos:"
echo "  ./mvnw test -DskipTests ou mvn test -DskipTests"
echo "  make down-dev && make up-dev"
