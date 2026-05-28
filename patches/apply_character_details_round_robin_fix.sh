#!/usr/bin/env bash
set -euo pipefail

if [ ! -f "pom.xml" ] || [ ! -d "src/main/java/com/nathan/tibiastats" ]; then
  echo "Execute este script na raiz do projeto TibiaChrono, onde fica o pom.xml." >&2
  exit 1
fi

BACKUP_DIR=".tibiachrono-character-details-round-robin-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"

backup_if_exists() {
  local file="$1"
  if [ -e "$file" ]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp -a "$file" "$BACKUP_DIR/$file"
  fi
}

write_file() {
  local file="$1"
  mkdir -p "$(dirname "$file")"
  cat > "$file"
}

echo "Criando backup em: $BACKUP_DIR"

backup_if_exists "docker-compose.dev.yml"
backup_if_exists "src/main/java/com/nathan/tibiastats/config/AppProperties.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/domain/model/CharacterEntity.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/domain/model/CharacterName.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/domain/model/CharacterNameNormalizer.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/domain/model/Vocation.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/domain/port/CharacterDetailPort.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/domain/port/CharacterRepositoryPort.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/application/scheduler/CharacterDetailsScrapeScheduler.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/application/service/CharacterDetailsService.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java"
backup_if_exists "src/main/resources/db/migration/V20__character_details_round_robin.sql"

write_file "src/main/java/com/nathan/tibiastats/domain/model/CharacterNameNormalizer.java" <<'JAVA'
package com.nathan.tibiastats.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Normalizes raw character names scraped from Tibia.com.
 * Tibia may append UI/status tags such as "(traded)" next to a character name.
 * Those tags are metadata and must not become part of character_names.name.
 */
public final class CharacterNameNormalizer {
    private static final Pattern TRADED_SUFFIX = Pattern.compile("\\s*\\((?i:traded)\\)\\s*$");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    private CharacterNameNormalizer() {}

    public static String normalize(String rawName) {
        if (rawName == null) {
            return null;
        }

        String normalized = rawName.replace('\u00A0', ' ').trim();

        String previous;
        do {
            previous = normalized;
            normalized = TRADED_SUFFIX.matcher(normalized).replaceAll("").trim();
        } while (!Objects.equals(previous, normalized));

        return MULTIPLE_SPACES.matcher(normalized).replaceAll(" ").trim();
    }

    public static boolean isBlank(String rawName) {
        String normalized = normalize(rawName);
        return normalized == null || normalized.isBlank();
    }

    public static boolean sameName(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);

        if (normalizedLeft == null || normalizedRight == null) {
            return normalizedLeft == normalizedRight;
        }

        return normalizedLeft.equalsIgnoreCase(normalizedRight);
    }

    public static List<String> normalizeMany(List<String> rawNames) {
        if (rawNames == null || rawNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> normalizedNames = new ArrayList<>();
        for (String rawName : rawNames) {
            String normalized = normalize(rawName);
            if (normalized != null && !normalized.isBlank()) {
                normalizedNames.add(normalized);
            }
        }
        return normalizedNames;
    }

    public static List<String> normalizeCsvToList(String rawNames) {
        if (rawNames == null || rawNames.isBlank()) {
            return Collections.emptyList();
        }

        List<String> normalizedNames = new ArrayList<>();
        for (String rawName : rawNames.split(",")) {
            String normalized = normalize(rawName);
            if (normalized != null && !normalized.isBlank()) {
                normalizedNames.add(normalized);
            }
        }
        return normalizedNames;
    }

    public static String normalizeCsv(String rawNames) {
        return String.join(",", normalizeCsvToList(rawNames));
    }

    public static String normalizedKey(String rawName) {
        String normalized = normalize(rawName);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
}
JAVA

write_file "src/main/java/com/nathan/tibiastats/domain/model/CharacterName.java" <<'JAVA'
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

    @PrePersist
    @PreUpdate
    private void normalizeNameBeforePersistence() {
        this.name = CharacterNameNormalizer.normalize(this.name);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = CharacterNameNormalizer.normalize(name); }

    public Boolean getActive() { return active; }
    public boolean isActive() { return Boolean.TRUE.equals(active); }
    public void setActive(Boolean active) { this.active = active; }

    public Instant getInactiveDate() { return inactiveDate; }
    public void setInactiveDate(Instant inactiveDate) { this.inactiveDate = inactiveDate; }

    public CharacterEntity getCharacter() { return character; }
    public void setCharacter(CharacterEntity character) { this.character = character; }

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

write_file "src/main/java/com/nathan/tibiastats/domain/model/CharacterEntity.java" <<'JAVA'
package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name="characters")
public class CharacterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public enum Sex { male, female }

    @Enumerated(EnumType.STRING)
    private Sex sex;

    @ManyToOne
    @JoinColumn(name = "vocation_id")
    private Vocation vocation;

    @Column(name = "level")
    private Integer level;

    @Column(name = "achievement_points")
    private Integer achievementPoints;

    @Column(name = "residence")
    private String residence;

    @Column(name = "last_login")
    private OffsetDateTime lastLogin;

    @Column(name = "acc_status")
    private String accStatus;

    @Column(name = "creation_date")
    private Instant creationDate;

    @Column(name = "details_last_scraped_at")
    private Instant detailsLastScrapedAt;

    @Column(name = "details_last_scrape_status")
    private String detailsLastScrapeStatus;

    @Column(name = "details_last_scrape_error", columnDefinition = "text")
    private String detailsLastScrapeError;

    @OneToMany(mappedBy = "character", cascade = CascadeType.PERSIST, orphanRemoval = false)
    private Set<CharacterName> names = new HashSet<>();

    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL)
    private Set<CharacterWorld> worlds = new HashSet<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public Integer getAchievementPoints() { return achievementPoints; }
    public void setAchievementPoints(Integer achievementPoints) { this.achievementPoints = achievementPoints; }

    public String getResidence() { return residence; }
    public void setResidence(String residence) { this.residence = residence; }

    public OffsetDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(OffsetDateTime lastLogin) { this.lastLogin = lastLogin; }

    public String getAccStatus() { return accStatus; }
    public void setAccStatus(String accStatus) { this.accStatus = accStatus; }

    public Instant getCreationDate() { return creationDate; }
    public void setCreationDate(Instant creationDate) { this.creationDate = creationDate; }

    public Instant getDetailsLastScrapedAt() { return detailsLastScrapedAt; }
    public void setDetailsLastScrapedAt(Instant detailsLastScrapedAt) { this.detailsLastScrapedAt = detailsLastScrapedAt; }

    public String getDetailsLastScrapeStatus() { return detailsLastScrapeStatus; }
    public void setDetailsLastScrapeStatus(String detailsLastScrapeStatus) { this.detailsLastScrapeStatus = detailsLastScrapeStatus; }

    public String getDetailsLastScrapeError() { return detailsLastScrapeError; }
    public void setDetailsLastScrapeError(String detailsLastScrapeError) { this.detailsLastScrapeError = detailsLastScrapeError; }

    public Sex getSex() { return sex; }
    public void setSex(Sex sex) { this.sex = sex; }

    public Vocation getVocation() { return vocation; }
    public void setVocation(Vocation vocation) { this.vocation = vocation; }

    public Set<CharacterName> getNames() { return names; }
    public void setNames(Set<CharacterName> names) { this.names = names; }

    public Set<CharacterWorld> getWorlds() { return worlds; }
    public void setWorlds(Set<CharacterWorld> worlds) { this.worlds = worlds; }

    public void addName(CharacterName name){
        name.setCharacter(this);
        names.add(name);
    }
}
JAVA

write_file "src/main/java/com/nathan/tibiastats/domain/model/Vocation.java" <<'JAVA'
package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name="vocations")
public class Vocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;

    @Column(name = "promotion_name")
    private String promotionName;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPromotionName() { return promotionName; }
    public void setPromotionName(String promotionName) { this.promotionName = promotionName; }
}
JAVA

write_file "src/main/java/com/nathan/tibiastats/config/AppProperties.java" <<'JAVA'
package com.nathan.tibiastats.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "tibiastats.scrape")
public class AppProperties {
    private Worlds worlds = new Worlds();
    private Highscores highscores = new Highscores();
    private CharacterDetails characterDetails = new CharacterDetails();

    public static class Worlds {
        private long rateMs = 60000L;
        public long getRateMs(){return rateMs;}
        public void setRateMs(long v){this.rateMs=v;}
    }

    public static class Highscores {
        private String cron = "0 0 7 * * *";
        public String getCron(){return cron;}
        public void setCron(String c){this.cron=c;}
    }

    public static class CharacterDetails {
        private boolean enabled = true;
        private long rateMs = 300000L;
        private long initialDelayMs = 15000L;
        private int batchSize = 25;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public long getRateMs() { return rateMs; }
        public void setRateMs(long rateMs) { this.rateMs = rateMs; }

        public long getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    public Worlds getWorlds() {return worlds;}
    public void setWorlds(Worlds worlds) { this.worlds = worlds; }

    public Highscores getHighscores() {return highscores;}
    public void setHighscores(Highscores highscores) { this.highscores = highscores; }

    public CharacterDetails getCharacterDetails() { return characterDetails; }
    public void setCharacterDetails(CharacterDetails characterDetails) { this.characterDetails = characterDetails; }
}
JAVA

write_file "src/main/java/com/nathan/tibiastats/domain/port/CharacterDetailPort.java" <<'JAVA'
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
JAVA

write_file "src/main/java/com/nathan/tibiastats/domain/port/CharacterRepositoryPort.java" <<'JAVA'
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
JAVA

write_file "src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java" <<'JAVA'
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
JAVA

write_file "src/main/java/com/nathan/tibiastats/application/scheduler/CharacterDetailsScrapeScheduler.java" <<'JAVA'
package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.CharacterDetailsService;
import com.nathan.tibiastats.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CharacterDetailsScrapeScheduler {
    private static final Logger log = LoggerFactory.getLogger(CharacterDetailsScrapeScheduler.class);
    private static final String LOG_PREFIX = "[CHARACTER_DETAILS_SCRAPER]";

    private final CharacterDetailsService characterDetailsService;
    private final AppProperties appProperties;

    public CharacterDetailsScrapeScheduler(CharacterDetailsService characterDetailsService,
                                           AppProperties appProperties) {
        this.characterDetailsService = characterDetailsService;
        this.appProperties = appProperties;
    }

    @Scheduled(
            fixedDelayString = "${tibiastats.scrape.character-details.rate-ms:300000}",
            initialDelayString = "${tibiastats.scrape.character-details.initial-delay-ms:15000}"
    )
    public void run() {
        if (!appProperties.getCharacterDetails().isEnabled()) {
            log.debug("{} Scheduler disabled", LOG_PREFIX);
            return;
        }

        int batchSize = Math.max(1, appProperties.getCharacterDetails().getBatchSize());
        log.info("{} Scheduler tick started. batchSize={}", LOG_PREFIX, batchSize);
        characterDetailsService.updateMissingDetailsBatch();
        log.info("{} Scheduler tick finished", LOG_PREFIX);
    }
}
JAVA

write_file "src/main/java/com/nathan/tibiastats/application/service/CharacterDetailsService.java" <<'JAVA'
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

import java.lang.reflect.Method;
import java.time.Instant;
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

    /**
     * Kept with the old name for compatibility with the existing scheduler.
     * The implementation no longer selects only rows with missing fields. It selects the
     * least recently attempted characters, so the first character is only retried after
     * all other active characters have also been attempted.
     */
    public void updateMissingDetailsBatch() {
        int batchSize = Math.max(1, appProperties.getCharacterDetails().getBatchSize());
        List<CharacterName> namesToRefresh = characterRepo.findActiveNamesForDetailsRefresh(batchSize);

        if (namesToRefresh.isEmpty()) {
            log.info("{} No active characters available for detail scrape", LOG_PREFIX);
            return;
        }

        log.info("{} Starting character detail scrape batch: selectedNames={}, batchSize={}", LOG_PREFIX, namesToRefresh.size(), batchSize);

        int updated = 0;
        int unchanged = 0;
        int notFound = 0;
        int empty = 0;
        int failed = 0;

        for (CharacterName characterName : namesToRefresh) {
            Long characterId = characterName.getCharacter().getId();
            String activeName = characterName.getName();
            Instant attemptedAt = Instant.now();

            try {
                var details = characterDetailPort.fetchCharacterDetails(activeName);
                if (details.isEmpty()) {
                    notFound++;
                    markAttempt(characterId, attemptedAt, "NOT_FOUND", null);
                    log.warn("{} Character details not found for id={} name='{}'; attempt marked so rotation can continue", LOG_PREFIX, characterId, activeName);
                    continue;
                }

                var characterDetails = details.get();
                if (!hasAnyUsefulDetail(characterDetails)) {
                    empty++;
                    markAttempt(characterId, attemptedAt, "EMPTY", "Profile fetched, but parser found no useful fields");
                    log.warn("{} Parser found no useful fields for id={} name='{}'; attempt marked so rotation can continue", LOG_PREFIX, characterId, activeName);
                    continue;
                }

                SaveResult result = saveCharacterDetails(characterId, activeName, characterDetails, attemptedAt);
                if (result.changed()) {
                    updated++;
                } else {
                    unchanged++;
                }
            } catch (Exception e) {
                failed++;
                markAttempt(characterId, attemptedAt, "FAILED", e.getMessage());
                log.error("{} Failed to scrape character details for id={} name='{}'; attempt marked so rotation can continue", LOG_PREFIX, characterId, activeName, e);
            }
        }

        log.info(
                "{} Finished character detail scrape batch: updated={}, unchanged={}, notFound={}, empty={}, failed={}",
                LOG_PREFIX, updated, unchanged, notFound, empty, failed
        );
    }

    private SaveResult saveCharacterDetails(Long characterId,
                                            String requestedName,
                                            CharacterDetailPort.CharacterDetails details,
                                            Instant attemptedAt) {
        SaveResult result = transactionTemplate.execute(status -> {
            CharacterEntity originalCharacter = characterRepo.findById(characterId).orElse(null);
            if (originalCharacter == null) {
                return new SaveResult(false, "MISSING_LOCAL_CHARACTER");
            }

            String officialCurrentName = firstNonBlank(details.currentName(), requestedName);
            CharacterEntity character = reconcileOfficialNamesIfAvailable(originalCharacter, officialCurrentName, details.formerNames());

            boolean characterChanged = character.getId() != null
                    && originalCharacter.getId() != null
                    && !Objects.equals(originalCharacter.getId(), character.getId());

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

            character.setDetailsLastScrapedAt(attemptedAt);
            character.setDetailsLastScrapeStatus(characterChanged ? "UPDATED" : "UNCHANGED");
            character.setDetailsLastScrapeError(null);
            characterRepo.save(character);

            log.info(
                    "{} Details attempt saved for characterId={} officialName='{}' status={} sex={} vocation={} level={} residence={} accStatus={} created={}",
                    LOG_PREFIX,
                    character.getId(),
                    officialCurrentName,
                    character.getDetailsLastScrapeStatus(),
                    character.getSex(),
                    character.getVocation() != null ? character.getVocation().getName() : null,
                    character.getLevel(),
                    character.getResidence(),
                    character.getAccStatus(),
                    character.getCreationDate()
            );

            return new SaveResult(characterChanged, character.getDetailsLastScrapeStatus());
        });

        return result == null ? new SaveResult(false, "UNKNOWN") : result;
    }

    @SuppressWarnings("unchecked")
    private CharacterEntity reconcileOfficialNamesIfAvailable(CharacterEntity originalCharacter,
                                                               String officialCurrentName,
                                                               List<String> formerNames) {
        try {
            Method method = characterNamingService.getClass().getMethod(
                    "reconcileOfficialNames",
                    CharacterEntity.class,
                    String.class,
                    List.class
            );
            Object result = method.invoke(characterNamingService, originalCharacter, officialCurrentName, formerNames);
            if (result instanceof CharacterEntity reconciledCharacter) {
                return reconciledCharacter;
            }
        } catch (NoSuchMethodException ignored) {
            // Older codebase: keep saving details on the current character.
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reconcile official character names", e);
        }
        return originalCharacter;
    }

    private void markAttempt(Long characterId, Instant attemptedAt, String status, String error) {
        transactionTemplate.executeWithoutResult(tx ->
                characterRepo.markDetailsScrapeAttempt(characterId, attemptedAt, status, error)
        );
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

    private record SaveResult(boolean changed, String status) {}
}
JAVA

mkdir -p "src/main/resources/db/migration"
write_file "src/main/resources/db/migration/V20__character_details_round_robin.sql" <<'SQL'
-- Rotation/fairness metadata for the character details scraper.
-- The scheduler must not keep selecting the first N characters forever when a field
-- such as creation_date stays NULL because Tibia.com does not expose it or the parser
-- cannot read it yet.

ALTER TABLE characters
    ADD COLUMN IF NOT EXISTS details_last_scraped_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS details_last_scrape_status TEXT,
    ADD COLUMN IF NOT EXISTS details_last_scrape_error TEXT;

CREATE INDEX IF NOT EXISTS idx_characters_details_last_scraped_at
    ON characters (details_last_scraped_at, id);

CREATE INDEX IF NOT EXISTS idx_character_names_active_character
    ON character_names (active, character_id);
SQL

python3 - <<'PY'
from pathlib import Path

compose = Path('docker-compose.dev.yml')
if compose.exists():
    text = compose.read_text(encoding='utf-8')
    env_lines = [
        '      SPRING_TASK_SCHEDULING_POOL_SIZE: "4"',
        '      TIBIASTATS_SCRAPE_CHARACTER_DETAILS_ENABLED: "true"',
        '      TIBIASTATS_SCRAPE_CHARACTER_DETAILS_RATE_MS: "60000"',
        '      TIBIASTATS_SCRAPE_CHARACTER_DETAILS_INITIAL_DELAY_MS: "15000"',
        '      TIBIASTATS_SCRAPE_CHARACTER_DETAILS_BATCH_SIZE: "25"',
    ]
    missing = [line for line in env_lines if line.split(':', 1)[0].strip() not in text]
    if missing:
        lines = text.splitlines()
        insert_at = None
        for i, line in enumerate(lines):
            if 'TIBIASTATS_SCRAPE_WORLDS_RATE_MS' in line:
                insert_at = i + 1
                break
        if insert_at is None:
            for i, line in enumerate(lines):
                if line.strip() == 'environment:':
                    insert_at = i + 1
                    break
        if insert_at is not None:
            lines[insert_at:insert_at] = missing
            compose.write_text('\n'.join(lines) + '\n', encoding='utf-8')

# Keep the dynamic cutoff call if old sources still reference the old static constant.
for path in Path('src/main/java').rglob('*.java'):
    text = path.read_text(encoding='utf-8')
    new_text = text.replace('CharacterName.INACTIVE_HORIZON', 'CharacterName.inactiveHorizon()')
    if new_text != text:
        path.write_text(new_text, encoding='utf-8')
PY

chmod 0644 \
  src/main/java/com/nathan/tibiastats/config/AppProperties.java \
  src/main/java/com/nathan/tibiastats/domain/model/CharacterEntity.java \
  src/main/java/com/nathan/tibiastats/domain/model/CharacterName.java \
  src/main/java/com/nathan/tibiastats/domain/model/CharacterNameNormalizer.java \
  src/main/java/com/nathan/tibiastats/domain/model/Vocation.java \
  src/main/java/com/nathan/tibiastats/domain/port/CharacterDetailPort.java \
  src/main/java/com/nathan/tibiastats/domain/port/CharacterRepositoryPort.java \
  src/main/java/com/nathan/tibiastats/application/scheduler/CharacterDetailsScrapeScheduler.java \
  src/main/java/com/nathan/tibiastats/application/service/CharacterDetailsService.java \
  src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java \
  src/main/resources/db/migration/V20__character_details_round_robin.sql

echo "Correção aplicada. Backup salvo em: $BACKUP_DIR"
echo "Próximos passos:"
echo "  make down-dev && make up-dev"
echo "  docker compose -f docker-compose.dev.yml logs -f app | grep --line-buffered CHARACTER_DETAILS_SCRAPER"
