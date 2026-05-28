#!/usr/bin/env bash
set -euo pipefail
ROOT="$(pwd)"
BACKUP_DIR="$ROOT/.tibiachrono-character-details-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"
backup_file() { local file="$1"; if [ -f "$ROOT/$file" ]; then mkdir -p "$BACKUP_DIR/$(dirname "$file")"; cp "$ROOT/$file" "$BACKUP_DIR/$file"; fi; }
write_file() { local file="$1"; mkdir -p "$ROOT/$(dirname "$file")"; cat > "$ROOT/$file"; }

echo "Creating backup in $BACKUP_DIR"
backup_file "docker-compose.dev.yml"
backup_file "src/main/java/com/nathan/tibiastats/config/AppProperties.java"
backup_file "src/main/java/com/nathan/tibiastats/domain/model/Vocation.java"
backup_file "src/main/java/com/nathan/tibiastats/domain/port/CharacterDetailPort.java"
backup_file "src/main/java/com/nathan/tibiastats/domain/port/CharacterRepositoryPort.java"
backup_file "src/main/java/com/nathan/tibiastats/application/scheduler/CharacterDetailsScrapeScheduler.java"
backup_file "src/main/java/com/nathan/tibiastats/application/service/CharacterDetailsService.java"
backup_file "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupCharacterAdapter.java"
backup_file "src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java"
backup_file "src/main/resources/application-dev.yml"
backup_file "src/main/resources/application.yml"
backup_file "src/main/resources/db/migration/V4__ensure_vocations.sql"

write_file "docker-compose.dev.yml" <<'__TIBIACHRONO_FILE__'
services:
  db:
    image: postgres:15-alpine
    environment:
      POSTGRES_USER: tibia
      POSTGRES_PASSWORD: secret
      POSTGRES_DB: tibiastats
    ports:
      - "5432:5432"
    volumes:
      - db_data:/var/lib/postgresql/data

  app:
    build:
      context: .
      dockerfile: Dockerfile.dev
    depends_on:
      - db
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/tibiastats
      SPRING_DATASOURCE_USERNAME: tibia
      SPRING_DATASOURCE_PASSWORD: secret
      SPRING_JPA_HIBERNATE_DDL_AUTO: none
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_SECRET_KEY: "please-change-me-to-a-very-long-random-secret"
      TIBIASTATS_SCRAPE_WORLDS_RATE_MS: "60000"
      TIBIASTATS_SCRAPE_CHARACTER_DETAILS_ENABLED: "true"
      TIBIASTATS_SCRAPE_CHARACTER_DETAILS_RATE_MS: "60000"
      TIBIASTATS_SCRAPE_CHARACTER_DETAILS_INITIAL_DELAY_MS: "15000"
      TIBIASTATS_SCRAPE_CHARACTER_DETAILS_BATCH_SIZE: "50"
      TIBIASTATS_SCRAPE_HIGHSCORES_CRON: "0 0 7 * * *"
      SPRING_FLYWAY_URL: jdbc:postgresql://db:5432/tibiastats
      SPRING_FLYWAY_USER: tibia
      SPRING_FLYWAY_PASSWORD: secret
      SPRING_FLYWAY_LOCATIONS: classpath:db/migration
      SPRING_FLYWAY_BASELINE_ON_MIGRATE: "true"
      DOCKER_HOST: unix:///var/run/docker.sock
    ports:
      - "8080:8080"
      - "9003:9003"
    volumes:
      - .:/workspace
      - ~/.m2:/root/.m2
    command:
      - mvn
      - -DskipTests
      - spring-boot:run
      - -Dspring-boot.run.profiles=dev
      - -Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:9003


volumes:
  db_data: {}
__TIBIACHRONO_FILE__

write_file "src/main/java/com/nathan/tibiastats/config/AppProperties.java" <<'__TIBIACHRONO_FILE__'
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
__TIBIACHRONO_FILE__

write_file "src/main/java/com/nathan/tibiastats/domain/model/Vocation.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;

@Entity @Table(name="vocations")
public class Vocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    @Column(name = "promotion_name")
    private String promotionName;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPromotionName() {
        return promotionName;
    }

    public void setPromotionName(String promotionName) {
        this.promotionName = promotionName;
    }
}
__TIBIACHRONO_FILE__

write_file "src/main/java/com/nathan/tibiastats/domain/port/CharacterDetailPort.java" <<'__TIBIACHRONO_FILE__'
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
__TIBIACHRONO_FILE__

write_file "src/main/java/com/nathan/tibiastats/domain/port/CharacterRepositoryPort.java" <<'__TIBIACHRONO_FILE__'
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
    List<CharacterName> findActiveNamesMissingDetails(int limit);
    Optional<CharacterEntity> findByAnyName(String name, Instant cutoff);
    CharacterName saveName(CharacterName name);
    Optional<CharacterName> findName(String name);
    Optional<Vocation> findVocationByNameOrPromotionName(String name);
    CharacterStatRecord saveStat(CharacterStatRecord r);
    List<CharacterStatRecord> findStatsBy(CharacterEntity c, StatCategory category);
}
__TIBIACHRONO_FILE__

write_file "src/main/java/com/nathan/tibiastats/application/scheduler/CharacterDetailsScrapeScheduler.java" <<'__TIBIACHRONO_FILE__'
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
            log.info("{} Scheduler tick ignored because character-details scraping is disabled", LOG_PREFIX);
            return;
        }

        log.info(
                "{} Scheduler tick started. rateMs={}, batchSize={}",
                LOG_PREFIX,
                appProperties.getCharacterDetails().getRateMs(),
                appProperties.getCharacterDetails().getBatchSize()
        );

        characterDetailsService.updateMissingDetailsBatch();
    }
}
__TIBIACHRONO_FILE__

write_file "src/main/java/com/nathan/tibiastats/application/service/CharacterDetailsService.java" <<'__TIBIACHRONO_FILE__'
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
    private final AppProperties appProperties;
    private final TransactionTemplate transactionTemplate;

    public CharacterDetailsService(CharacterRepositoryPort characterRepo,
                                   CharacterDetailPort characterDetailPort,
                                   AppProperties appProperties,
                                   TransactionTemplate transactionTemplate) {
        this.characterRepo = characterRepo;
        this.characterDetailPort = characterDetailPort;
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

                boolean changed = saveCharacterDetails(characterId, characterDetails);
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

    private boolean saveCharacterDetails(Long characterId, CharacterDetailPort.CharacterDetails details) {
        Boolean changed = transactionTemplate.execute(status -> {
            CharacterEntity character = characterRepo.findById(characterId).orElse(null);
            if (character == null) {
                return false;
            }

            boolean characterChanged = false;

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
                        "{} Saved details for characterId={}: sex={}, vocation={}, level={}, residence={}, status={}, created={}",
                        LOG_PREFIX,
                        characterId,
                        character.getSex(),
                        character.getVocation() != null ? character.getVocation().getName() : null,
                        character.getLevel(),
                        character.getResidence(),
                        character.getAccStatus(),
                        character.getCreationDate()
                );
            } else {
                log.debug("{} No changed fields for characterId={}", LOG_PREFIX, characterId);
            }

            return characterChanged;
        });

        return Boolean.TRUE.equals(changed);
    }

    private boolean hasAnyUsefulDetail(CharacterDetailPort.CharacterDetails details) {
        return details.sex() != null
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

    private boolean isNonBlank(String value) {
        return value != null && !value.trim().isBlank();
    }
}
__TIBIACHRONO_FILE__

write_file "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupCharacterAdapter.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.port.CharacterDetailPort;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class JsoupCharacterAdapter implements CharacterDetailPort {
    private static final Logger log = LoggerFactory.getLogger(JsoupCharacterAdapter.class);
    private static final String LOG_PREFIX = "[CHARACTER_DETAILS_SCRAPER]";

    private static final String CHARACTER_PAGE_URL_TEMPLATE =
            "https://www.tibia.com/community/?subtopic=characters&name=%s";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; TibiaChronoBot/1.0; +https://localhost)";
    private static final int TIMEOUT_MS = 15000;

    private static final DateTimeFormatter SHORT_MONTH_DATE_TIME =
            DateTimeFormatter.ofPattern("MMM d yyyy, HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter LONG_MONTH_DATE_TIME =
            DateTimeFormatter.ofPattern("MMMM d yyyy, HH:mm:ss", Locale.ENGLISH);

    @Override
    public NameDetails fetchNameDetails(String worldName, String characterName) {
        return fetchCharacterDetails(characterName)
                .map(details -> new NameDetails(details.currentName(), details.formerNames()))
                .orElseGet(() -> new NameDetails(characterName, List.of()));
    }

    @Override
    public Optional<CharacterDetails> fetchCharacterDetails(String characterName) {
        String url = CHARACTER_PAGE_URL_TEMPLATE.formatted(URLEncoder.encode(characterName, StandardCharsets.UTF_8));

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            if (isCharacterNotFound(doc)) {
                log.warn("{} Character not found on Tibia.com: {}", LOG_PREFIX, characterName);
                return Optional.empty();
            }

            Map<String, String> fields = collectCharacterFields(doc);
            if (fields.isEmpty()) {
                log.warn("{} No character profile fields parsed for {}. title='{}'", LOG_PREFIX, characterName, doc.title());
            } else {
                log.debug("{} Parsed fields for {}: {}", LOG_PREFIX, characterName, fields.keySet());
            }

            String currentName = firstNonBlank(fields.get("name"), characterName);
            List<String> formerNames = splitFormerNames(fields.get("former names"));
            CharacterEntity.Sex sex = parseSex(fields.get("sex"));
            String vocation = fields.get("vocation");
            Integer level = parseIntegerOrNull(fields.get("level"));
            Integer achievementPoints = parseIntegerOrNull(fields.get("achievement points"));
            String residence = fields.get("residence");
            OffsetDateTime lastLogin = parseTibiaDateTime(fields.get("last login")).orElse(null);
            String accountStatus = fields.get("account status");
            Instant creationDate = parseTibiaDateTime(fields.get("created"))
                    .map(OffsetDateTime::toInstant)
                    .orElse(null);
            String world = fields.get("world");

            return Optional.of(new CharacterDetails(
                    currentName,
                    formerNames,
                    sex,
                    vocation,
                    level,
                    achievementPoints,
                    residence,
                    lastLogin,
                    accountStatus,
                    creationDate,
                    world
            ));
        } catch (HttpStatusException e) {
            throw new RuntimeException(
                    "Failed to fetch character details for " + characterName + ": HTTP " + e.getStatusCode(),
                    e
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch character details for " + characterName, e);
        }
    }

    private Map<String, String> collectCharacterFields(Document doc) {
        Map<String, String> fields = new LinkedHashMap<>();

        // Tibia profile rows usually use a LabelV cell followed by the value cell.
        Elements labelCells = doc.select("td.LabelV");
        for (Element labelCell : labelCells) {
            Element valueCell = labelCell.nextElementSibling();
            addField(fields, labelCell, valueCell);
        }

        // Fallback for small layout changes or cached/static HTML variants.
        if (fields.isEmpty()) {
            Elements rows = doc.select("table.TableContent tr");
            for (Element row : rows) {
                Elements cols = row.select("> td");
                if (cols.size() >= 2) {
                    addField(fields, cols.get(0), cols.get(1));
                }
            }
        }

        return fields;
    }

    private void addField(Map<String, String> fields, Element labelCell, Element valueCell) {
        if (labelCell == null || valueCell == null) {
            return;
        }

        String label = normalizeLabel(labelCell.text());
        String value = normalizeValue(valueCell.text());

        if (!label.isBlank() && !value.isBlank()) {
            fields.putIfAbsent(label, value);
        }
    }

    private boolean isCharacterNotFound(Document doc) {
        String text = doc.text().toLowerCase(Locale.ROOT);
        return text.contains("could not find character")
                || text.contains("character does not exist")
                || text.contains("no character with this name");
    }

    private String normalizeLabel(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(':', ' ')
                .replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    private List<String> splitFormerNames(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String name = part.trim();
            if (!name.isBlank()) {
                result.add(name);
            }
        }
        return result;
    }

    private CharacterEntity.Sex parseSex(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("male")) {
            return CharacterEntity.Sex.male;
        }
        if (normalized.startsWith("female")) {
            return CharacterEntity.Sex.female;
        }
        return null;
    }

    private Optional<OffsetDateTime> parseTibiaDateTime(String value) {
        String normalized = normalizeValue(value);
        if (normalized.isBlank() || normalized.equalsIgnoreCase("never logged in.")) {
            return Optional.empty();
        }

        String[] parts = normalized.split("\\s+");
        if (parts.length < 2) {
            return Optional.empty();
        }

        String zoneAbbreviation = parts[parts.length - 1];
        String dateTimePart = normalized.substring(0, normalized.length() - zoneAbbreviation.length()).trim();
        ZoneOffset offset = zoneOffsetFor(zoneAbbreviation);

        for (DateTimeFormatter formatter : List.of(SHORT_MONTH_DATE_TIME, LONG_MONTH_DATE_TIME)) {
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(dateTimePart, formatter);
                return Optional.of(OffsetDateTime.of(localDateTime, offset));
            } catch (DateTimeParseException ignored) {
                // Try the next accepted Tibia date format.
            }
        }

        log.debug("{} Could not parse Tibia date/time: '{}'", LOG_PREFIX, value);
        return Optional.empty();
    }

    private ZoneOffset zoneOffsetFor(String abbreviation) {
        if (abbreviation == null) {
            return ZoneOffset.UTC;
        }

        return switch (abbreviation.trim().toUpperCase(Locale.ROOT)) {
            case "CEST" -> ZoneOffset.ofHours(2);
            case "CET" -> ZoneOffset.ofHours(1);
            case "BRT" -> ZoneOffset.ofHours(-3);
            case "UTC", "GMT" -> ZoneOffset.UTC;
            default -> ZoneOffset.UTC;
        };
    }

    private Integer parseIntegerOrNull(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
__TIBIACHRONO_FILE__

write_file "src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*; import java.util.List;

interface CharacterJpa extends JpaRepository<CharacterEntity, Long> {
    @Query("""
        SELECT n.character
        FROM CharacterName n
        WHERE n.name = :name
          AND (
               n.active = true
               OR (n.active = false AND n.inactiveDate > :cutoff)
          )
        """)
    Optional<CharacterEntity> findByAnyName(String name, Instant cutoff);
}
interface CharacterNameJpa extends JpaRepository<CharacterName, Long> {
    Optional<CharacterName> findByNameAndActiveTrue(String name);

    @Query("select cn from CharacterName cn where cn.character.id = :charId and cn.active=true")
    Optional<CharacterName> findCharacterActiveName(Long charId);

    @Query("select cn from CharacterName cn where cn.name = :name")
    Optional<CharacterName> findName(String name);

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
    Optional<Vocation> findByNameOrPromotionName(String name);
}

interface CharacterStatJpa extends JpaRepository<CharacterStatRecord, Long> {
    @Query("select r from CharacterStatRecord r where r.character = :c and r.category = :cat order by r.date asc")
    List<CharacterStatRecord> findByCat(CharacterEntity c, StatCategory cat);
}

@Repository
public class SpringCharacterRepository implements CharacterRepositoryPort {
    private final CharacterNameJpa names;
    private final CharacterJpa chars;
    private final CharacterStatJpa stats;
    private final VocationJpa vocations;

    public SpringCharacterRepository(CharacterNameJpa names, CharacterJpa chars, CharacterStatJpa stats, VocationJpa vocations) {
        this.names = names;
        this.chars = chars;
        this.stats = stats;
        this.vocations = vocations;
    }

    public CharacterName saveName(CharacterName name){
        return names.save(name);
    }

    @Override
    public Optional<CharacterName> findName(String name) {
        return names.findName(name);
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
    public CharacterEntity save(CharacterEntity c){
        return chars.save(c);
    }
    @Override
    public CharacterStatRecord saveStat(CharacterStatRecord r){
        return stats.save(r);
    }
    @Override
    public List<CharacterStatRecord> findStatsBy(CharacterEntity c, StatCategory category){
        return stats.findByCat(c, category);
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
}
__TIBIACHRONO_FILE__

write_file "src/main/resources/application-dev.yml" <<'__TIBIACHRONO_FILE__'
spring:
  config:
    activate:
      on-profile: dev

  datasource:
    url: jdbc:postgresql://db:5432/tibiastats
    username: tibia
    password: secret
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    defer-datasource-initialization: false   # ensure migrations happen before JPA validates
    open-in-view: false
    show-sql: false
    properties:
      hibernate:
        format_sql: true

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  task:
    scheduling:
      pool:
        size: 4

  graphql:
    path: /graphql

  devtools:
    restart:
      enabled: true
    livereload:
      enabled: true

  security:
    oauth2:
      resourceserver:
        jwt:
          secret-key: "please-change-me-to-a-very-long-random-secret"

logging:
  level:
    root: INFO
    org.hibernate.SQL: WARN
    org.springframework.web: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: WARN
    org.hibernate.orm.jdbc.bind: WARN
    org.flywaydb.core: DEBUG
    org.hibernate.tool.hbm2ddl: DEBUG
    com.nathan.tibiastats: DEBUG
    org.springframework.scheduling: INFO

server:
  port: 8080

tibiastats:
  scrape:
    worlds:
      rate-ms: 60000
    character-details:
      enabled: true
      rate-ms: 60000           # 1 min between small batches during dev
      initial-delay-ms: 15000  # wait 15s after startup before first batch
      batch-size: 50            # adjustable through TIBIASTATS_SCRAPE_CHARACTER_DETAILS_BATCH_SIZE
    highscores:
      cron: "0 0 7 * * *"       # daily at 7 AM
  jwt:
    access-ttl-ms: 900000       # 15 minutes
    refresh-ttl-ms: 1209600000  # 14 days
__TIBIACHRONO_FILE__

write_file "src/main/resources/application.yml" <<'__TIBIACHRONO_FILE__'
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/tibiastats
    username: tibia
    password: secret
  security:
    oauth2:
      resourceserver:
        jwt:
          secret-key: "please-change-me-to-a-very-long-random-secret"
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
  task:
    scheduling:
      pool:
        size: 4

  graphql:
    path: /graphql.

server:
  port: 8080

# Scraping schedules (overrideable)
tibiastats:
  scrape:
    worlds:
      rate-ms: 60000
    character-details:
      enabled: true
      rate-ms: 60000           # 1 min between small batches during dev
      initial-delay-ms: 15000  # wait 15s after startup before first batch
      batch-size: 50            # adjustable through TIBIASTATS_SCRAPE_CHARACTER_DETAILS_BATCH_SIZE
    highscores:
      cron: "0 0 7 * * *" # daily at 7 AM
  jwt:
    access-ttl-ms: 900000
    refresh-ttl-ms: 1209600000
__TIBIACHRONO_FILE__

write_file "src/main/resources/db/migration/V4__ensure_vocations.sql" <<'__TIBIACHRONO_FILE__'
INSERT INTO vocations(name, promotion_name)
SELECT 'none', 'none'
WHERE NOT EXISTS (
    SELECT 1 FROM vocations WHERE lower(name) = 'none'
);

INSERT INTO vocations(name, promotion_name)
SELECT 'druid', 'elder druid'
WHERE NOT EXISTS (
    SELECT 1 FROM vocations WHERE lower(name) = 'druid' OR lower(promotion_name) = 'elder druid'
);

INSERT INTO vocations(name, promotion_name)
SELECT 'sorcerer', 'master sorcerer'
WHERE NOT EXISTS (
    SELECT 1 FROM vocations WHERE lower(name) = 'sorcerer' OR lower(promotion_name) = 'master sorcerer'
);

INSERT INTO vocations(name, promotion_name)
SELECT 'knight', 'elite knight'
WHERE NOT EXISTS (
    SELECT 1 FROM vocations WHERE lower(name) = 'knight' OR lower(promotion_name) = 'elite knight'
);

INSERT INTO vocations(name, promotion_name)
SELECT 'paladin', 'royal paladin'
WHERE NOT EXISTS (
    SELECT 1 FROM vocations WHERE lower(name) = 'paladin' OR lower(promotion_name) = 'royal paladin'
);

INSERT INTO vocations(name, promotion_name)
SELECT 'monk', 'exalted monk'
WHERE NOT EXISTS (
    SELECT 1 FROM vocations WHERE lower(name) = 'monk' OR lower(promotion_name) = 'exalted monk'
);
__TIBIACHRONO_FILE__

echo "Character details scheduler diagnostic fix applied."
echo "Run: docker compose -f docker-compose.dev.yml restart app"
