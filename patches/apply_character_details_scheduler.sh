#!/usr/bin/env bash
set -euo pipefail

if [[ ! -f "pom.xml" || ! -d "src/main/java/com/nathan/tibiastats" ]]; then
  echo "Run this script from the TibiaChrono project root."
  exit 1
fi

backup_dir=".tibiachrono-character-details-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$backup_dir"
backup_file() {
  local file="$1"
  if [[ -f "$file" ]]; then
    mkdir -p "$backup_dir/$(dirname "$file")"
    cp "$file" "$backup_dir/$file"
  fi
}

files=(
  "src/main/java/com/nathan/tibiastats/domain/port/CharacterDetailPort.java"
  "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupCharacterAdapter.java"
  "src/main/java/com/nathan/tibiastats/domain/port/CharacterRepositoryPort.java"
  "src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java"
  "src/main/java/com/nathan/tibiastats/application/service/CharacterDetailsService.java"
  "src/main/java/com/nathan/tibiastats/application/scheduler/CharacterDetailsScrapeScheduler.java"
  "src/main/java/com/nathan/tibiastats/config/AppProperties.java"
  "src/main/resources/application-dev.yml"
  "src/main/resources/application.yml"
)

for file in "${files[@]}"; do
  backup_file "$file"
done

mkdir -p \
  src/main/java/com/nathan/tibiastats/domain/port \
  src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper \
  src/main/java/com/nathan/tibiastats/infrastructure/persistence \
  src/main/java/com/nathan/tibiastats/application/service \
  src/main/java/com/nathan/tibiastats/application/scheduler \
  src/main/java/com/nathan/tibiastats/config \
  src/main/resources

cat > src/main/java/com/nathan/tibiastats/domain/port/CharacterDetailPort.java <<'EOF'
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
EOF

cat > src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupCharacterAdapter.java <<'EOF'
package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.port.CharacterDetailPort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class JsoupCharacterAdapter implements CharacterDetailPort {
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
        try {
            String url = CHARACTER_PAGE_URL_TEMPLATE.formatted(URLEncoder.encode(characterName, StandardCharsets.UTF_8));
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            if (isCharacterNotFound(doc)) {
                return Optional.empty();
            }

            String currentName = null;
            List<String> formerNames = new ArrayList<>();
            CharacterEntity.Sex sex = null;
            String vocation = null;
            Integer level = null;
            Integer achievementPoints = null;
            String residence = null;
            OffsetDateTime lastLogin = null;
            String accountStatus = null;
            Instant creationDate = null;
            String world = null;

            Elements rows = doc.select("table.TableContent tr");
            for (Element row : rows) {
                Elements cols = row.select("> td");
                if (cols.size() < 2) {
                    continue;
                }

                String label = normalizeLabel(cols.get(0).text());
                String value = normalizeValue(cols.get(1).text());
                if (label.isBlank() || value.isBlank()) {
                    continue;
                }

                switch (label) {
                    case "name" -> currentName = value;
                    case "former names" -> formerNames = splitFormerNames(value);
                    case "sex" -> sex = parseSex(value);
                    case "vocation" -> vocation = value;
                    case "level" -> level = parseIntegerOrNull(value);
                    case "achievement points" -> achievementPoints = parseIntegerOrNull(value);
                    case "world" -> world = value;
                    case "residence" -> residence = value;
                    case "last login" -> lastLogin = parseTibiaDateTime(value).orElse(null);
                    case "account status" -> accountStatus = value;
                    case "created" -> creationDate = parseTibiaDateTime(value)
                            .map(OffsetDateTime::toInstant)
                            .orElse(null);
                    default -> {
                        // Ignore table rows that are not part of the character profile summary.
                    }
                }
            }

            if (currentName == null || currentName.isBlank()) {
                currentName = characterName;
            }

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
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch character details for " + characterName, e);
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
EOF

python3 - <<'PY'
from pathlib import Path

p = Path('src/main/java/com/nathan/tibiastats/domain/port/CharacterRepositoryPort.java')
s = p.read_text()
if 'List<CharacterName> findActiveNamesMissingDetails(int limit);' not in s:
    s = s.replace('Optional<CharacterName> findCharacterActiveName(Long id);\n', 'Optional<CharacterName> findCharacterActiveName(Long id);\n    List<CharacterName> findActiveNamesMissingDetails(int limit);\n')
p.write_text(s)

p = Path('src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java')
s = p.read_text()
if 'import org.springframework.data.domain.PageRequest;' not in s:
    s = s.replace('import org.springframework.data.jpa.repository.Query;\n', 'import org.springframework.data.jpa.repository.Query;\nimport org.springframework.data.domain.PageRequest;\nimport org.springframework.data.domain.Pageable;\n')
if 'List<CharacterName> findActiveNamesMissingDetails(Pageable pageable);' not in s:
    s = s.replace('    @Query("select cn from CharacterName cn where cn.name = :name")\n    Optional<CharacterName> findName(String name);\n', '''    @Query("select cn from CharacterName cn where cn.name = :name")\n    Optional<CharacterName> findName(String name);\n\n    @Query("""\n        select cn\n        from CharacterName cn\n        join fetch cn.character c\n        where cn.active = true\n          and (\n               c.sex is null\n            or c.vocation is null\n            or c.level is null\n            or c.residence is null\n            or c.accStatus is null\n            or c.creationDate is null\n          )\n        order by c.id asc\n        """)\n    List<CharacterName> findActiveNamesMissingDetails(Pageable pageable);\n''')
if 'public List<CharacterName> findActiveNamesMissingDetails(int limit)' not in s:
    s = s.replace('    @Override\n    public Optional<CharacterEntity> findByAnyName(String name, Instant cutoff) {\n        return chars.findByAnyName(name, cutoff);\n    }\n', '''    @Override\n    public Optional<CharacterEntity> findByAnyName(String name, Instant cutoff) {\n        return chars.findByAnyName(name, cutoff);\n    }\n\n    @Override\n    public List<CharacterName> findActiveNamesMissingDetails(int limit) {\n        return names.findActiveNamesMissingDetails(PageRequest.of(0, Math.max(1, limit)));\n    }\n''')
p.write_text(s)
PY

cat > src/main/java/com/nathan/tibiastats/application/service/CharacterDetailsService.java <<'EOF'
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
            log.debug("No characters pending detail scrape");
            return;
        }

        log.info("Starting character detail scrape batch: size={}", pendingNames.size());

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
                    log.warn("Character details not found for {}. Skipping for now.", activeName);
                    continue;
                }

                boolean changed = saveCharacterDetails(characterId, details.get());
                if (changed) {
                    updated++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to scrape character details for {}. Continuing with next character.", activeName, e);
            }
        }

        log.info("Finished character detail scrape batch: updated={}, skipped={}, failed={}", updated, skipped, failed);
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
            }

            return characterChanged;
        });

        return Boolean.TRUE.equals(changed);
    }

    private boolean sameVocation(Vocation current, Vocation scraped) {
        return current != null && scraped != null && Objects.equals(current.getId(), scraped.getId());
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.trim().isBlank();
    }
}
EOF

cat > src/main/java/com/nathan/tibiastats/application/scheduler/CharacterDetailsScrapeScheduler.java <<'EOF'
package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.CharacterDetailsService;
import com.nathan.tibiastats.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CharacterDetailsScrapeScheduler {
    private final CharacterDetailsService characterDetailsService;
    private final AppProperties appProperties;

    public CharacterDetailsScrapeScheduler(CharacterDetailsService characterDetailsService,
                                           AppProperties appProperties) {
        this.characterDetailsService = characterDetailsService;
        this.appProperties = appProperties;
    }

    @Scheduled(fixedDelayString = "${tibiastats.scrape.character-details.rate-ms:300000}")
    public void run() {
        if (!appProperties.getCharacterDetails().isEnabled()) {
            return;
        }

        characterDetailsService.updateMissingDetailsBatch();
    }
}
EOF

cat > src/main/java/com/nathan/tibiastats/config/AppProperties.java <<'EOF'
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
        private int batchSize = 25;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public long getRateMs() { return rateMs; }
        public void setRateMs(long rateMs) { this.rateMs = rateMs; }

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
EOF

python3 - <<'PY'
from pathlib import Path
p = Path('src/main/resources/application-dev.yml')
s = p.read_text()
if 'character-details:' not in s:
    s = s.replace('''    worlds:\n      rate-ms: 60000            # 1 min scrape interval\n    highscores:\n      cron: "0 0 7 * * *"       # daily at 3 AM\n''','''    worlds:\n      rate-ms: 60000            # 1 min scrape interval\n    character-details:\n      enabled: true\n      rate-ms: 300000           # 5 min between small batches\n      batch-size: 25            # adjustable through TIBIASTATS_SCRAPE_CHARACTER_DETAILS_BATCH_SIZE\n    highscores:\n      cron: "0 0 7 * * *"       # daily at 7 AM\n''')
    if s == p.read_text():
        s = s.rstrip() + '''\n\ntibiastats:\n  scrape:\n    character-details:\n      enabled: true\n      rate-ms: 300000\n      batch-size: 25\n'''
p.write_text(s)

p = Path('src/main/resources/application.yml')
s = p.read_text()
if 'character-details:' not in s:
    marker = '# Scraping schedules (overrideable)'
    if marker in s:
        head = s.split(marker)[0].rstrip()
        s = head + '''\n\n# Scraping schedules (overrideable)\ntibiastats:\n  scrape:\n    worlds:\n      rate-ms: 60000 # every 1 minute by default\n    character-details:\n      enabled: true\n      rate-ms: 300000 # every 5 minutes by default\n      batch-size: 25\n    highscores:\n      cron: "0 0 7 * * *" # daily at 7 AM\n  jwt:\n    access-ttl-ms: 900000\n    refresh-ttl-ms: 1209600000\n'''
p.write_text(s)
PY

echo "Done. Backup written to $backup_dir"
