#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

if [ ! -f "pom.xml" ] || [ ! -d "src/main/java" ]; then
  echo "ERRO: rode este script na raiz do projeto TibiaChrono." >&2
  exit 1
fi

BACKUP_DIR=".tibiachrono-highscore-vocation-world-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"

backup_if_exists() {
  local file="$1"
  if [ -f "$file" ]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp -a "$file" "$BACKUP_DIR/$file"
  fi
}

backup_if_exists "src/main/java/com/nathan/tibiastats/domain/port/HighscorePersistencePort.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/domain/model/CharacterStatRecord.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringHighscorePersistenceRepository.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/domain/model/CharacterNameNormalizer.java"
backup_if_exists "docker-compose.dev.yml"
backup_if_exists "src/main/resources/application-dev.yml"

mkdir -p "src/main/java/com/nathan/tibiastats/domain/model"
if [ ! -f "src/main/java/com/nathan/tibiastats/domain/model/CharacterNameNormalizer.java" ]; then
cat > "src/main/java/com/nathan/tibiastats/domain/model/CharacterNameNormalizer.java" <<'JAVA'
package com.nathan.tibiastats.domain.model;

public final class CharacterNameNormalizer {
    private CharacterNameNormalizer() {}

    public static String normalize(String rawName) {
        if (rawName == null) {
            return null;
        }

        return rawName
                .replaceAll("(?i)\\s*\\(traded\\)\\s*$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
JAVA
fi

mkdir -p "src/main/java/com/nathan/tibiastats/domain/port"
cat > "src/main/java/com/nathan/tibiastats/domain/port/HighscorePersistencePort.java" <<'JAVA'
package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.StatCategory;

import java.time.Instant;
import java.time.LocalDate;

public interface HighscorePersistencePort {
    void upsertDailyStat(Long characterId,
                         Integer worldId,
                         StatCategory category,
                         int vocationFilterId,
                         LocalDate date,
                         long value,
                         int rank,
                         Instant scrapedAt);
}
JAVA

cat > "src/main/java/com/nathan/tibiastats/domain/model/CharacterStatRecord.java" <<'JAVA'
package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "character_statrecords")
public class CharacterStatRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "character_id")
    private CharacterEntity character;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private StatCategory category;

    @Column(name = "vocation_filter_id", nullable = false)
    private Integer vocationFilterId = 0;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "value")
    private Long value;

    @Column(name = "rank")
    private Integer rank;

    @ManyToOne(optional = false)
    @JoinColumn(name = "world_id")
    private World world;

    @Column(name = "scraped_at", nullable = false)
    private Instant scrapedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CharacterEntity getCharacter() { return character; }
    public void setCharacter(CharacterEntity character) { this.character = character; }

    public StatCategory getCategory() { return category; }
    public void setCategory(StatCategory category) { this.category = category; }

    public Integer getVocationFilterId() { return vocationFilterId; }
    public void setVocationFilterId(Integer vocationFilterId) { this.vocationFilterId = vocationFilterId == null ? 0 : vocationFilterId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Long getValue() { return value; }
    public void setValue(Long value) { this.value = value; }

    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }

    public World getWorld() { return world; }
    public void setWorld(World world) { this.world = world; }

    public Instant getScrapedAt() { return scrapedAt; }
    public void setScrapedAt(Instant scrapedAt) { this.scrapedAt = scrapedAt; }
}
JAVA

mkdir -p "src/main/java/com/nathan/tibiastats/infrastructure/persistence"
cat > "src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringHighscorePersistenceRepository.java" <<'JAVA'
package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.port.HighscorePersistencePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

@Repository
public class SpringHighscorePersistenceRepository implements HighscorePersistencePort {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void upsertDailyStat(Long characterId,
                                Integer worldId,
                                StatCategory category,
                                int vocationFilterId,
                                LocalDate date,
                                long value,
                                int rank,
                                Instant scrapedAt) {
        entityManager.createNativeQuery("""
            insert into character_statrecords
                (character_id, category, vocation_filter_id, date, value, rank, world_id, scraped_at)
            values
                (:characterId, :category, :vocationFilterId, :date, :value, :rank, :worldId, :scrapedAt)
            on conflict (character_id, world_id, category, vocation_filter_id, date)
            do update set
                value = excluded.value,
                rank = excluded.rank,
                scraped_at = excluded.scraped_at
            """)
            .setParameter("characterId", characterId)
            .setParameter("category", category.name())
            .setParameter("vocationFilterId", vocationFilterId)
            .setParameter("date", Date.valueOf(date))
            .setParameter("value", value)
            .setParameter("rank", rank)
            .setParameter("worldId", worldId)
            .setParameter("scrapedAt", Timestamp.from(scrapedAt))
            .executeUpdate();
    }
}
JAVA

mkdir -p "src/main/java/com/nathan/tibiastats/application/service"
cat > "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java" <<'JAVA'
package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.HighscorePersistencePort;
import com.nathan.tibiastats.domain.port.HighscorePort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class HighscoreService {
    private static final Logger log = LoggerFactory.getLogger(HighscoreService.class);
    private static final String LOG_PREFIX = "[HIGHSCORE_SCRAPER]";

    private final HighscorePort highscorePort;
    private final WorldRepositoryPort worlds;
    private final CharacterNamingService namingService;
    private final HighscorePersistencePort persistence;

    @Value("${tibiastats.scrape.highscores.max-pages:1}")
    private int maxPages;

    @Value("${tibiastats.scrape.highscores.page-delay-ms:1000}")
    private long pageDelayMs;

    @Value("${tibiastats.scrape.highscores.categories:EXPERIENCE}")
    private String configuredCategories;

    @Value("${tibiastats.scrape.highscores.vocations:0}")
    private String configuredVocations;

    @Value("${tibiastats.scrape.highscores.world-limit:0}")
    private int worldLimit;

    public HighscoreService(HighscorePort highscorePort,
                            WorldRepositoryPort worlds,
                            CharacterNamingService namingService,
                            HighscorePersistencePort persistence) {
        this.highscorePort = highscorePort;
        this.worlds = worlds;
        this.namingService = namingService;
        this.persistence = persistence;
    }

    public void updateAllHighscores() {
        LocalDate date = LocalDate.now();
        Instant startedAt = Instant.now();
        List<World> worldList = new ArrayList<>(worlds.findAll());
        List<StatCategory> categories = parseCategories(configuredCategories);
        List<Integer> vocations = parseVocations(configuredVocations);
        int safeMaxPages = Math.max(1, maxPages);
        int processedWorlds = 0;
        int savedRows = 0;
        int failedCombinations = 0;

        if (worldLimit > 0 && worldList.size() > worldLimit) {
            worldList = worldList.subList(0, worldLimit);
        }

        log.info("{} Starting highscores scrape: worlds={}, categories={}, vocationFilters={}, maxPages={}, date={}",
                LOG_PREFIX, worldList.size(), categories, vocations, safeMaxPages, date);

        for (World world : worldList) {
            processedWorlds++;
            for (StatCategory category : categories) {
                for (Integer vocationFilterId : vocations) {
                    try {
                        savedRows += scrapeCombination(world, category, vocationFilterId, date, safeMaxPages);
                    } catch (Exception e) {
                        failedCombinations++;
                        log.warn("{} Failed combination world={} category={} vocationFilter={}: {}",
                                LOG_PREFIX, world.getName(), category, vocationFilterId, e.getMessage(), e);
                    }
                }
            }
        }

        log.info("{} Finished highscores scrape: processedWorlds={}, savedRows={}, failedCombinations={}, startedAt={}, finishedAt={}",
                LOG_PREFIX, processedWorlds, savedRows, failedCombinations, startedAt, Instant.now());
    }

    private int scrapeCombination(World world, StatCategory category, int vocationFilterId, LocalDate date, int safeMaxPages) {
        int savedRows = 0;
        Instant scrapedAt = Instant.now();

        for (int page = 1; page <= safeMaxPages; page++) {
            List<HighscorePort.HighscoreRow> rows = highscorePort.fetchHighscores(world.getName(), category, vocationFilterId, page);
            if (rows.isEmpty()) {
                log.info("{} Empty page world={} category={} vocationFilter={} page={}", LOG_PREFIX, world.getName(), category, vocationFilterId, page);
                break;
            }

            for (HighscorePort.HighscoreRow row : rows) {
                CharacterEntity character = namingService.ensureCharacterForName(row.name(), row.name());
                persistence.upsertDailyStat(
                        character.getId(),
                        world.getId(),
                        category,
                        vocationFilterId,
                        date,
                        row.value(),
                        row.rank(),
                        scrapedAt
                );
                savedRows++;
            }

            log.info("{} Saved page world={} category={} vocationFilter={} page={} rows={}",
                    LOG_PREFIX, world.getName(), category, vocationFilterId, page, rows.size());

            delayBetweenPages();
        }

        return savedRows;
    }

    private void delayBetweenPages() {
        if (pageDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(pageDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Highscore scrape interrupted", e);
        }
    }

    private List<StatCategory> parseCategories(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of(StatCategory.EXPERIENCE);
        }

        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> StatCategory.valueOf(s.toUpperCase(Locale.ROOT)))
                .toList();
    }

    private List<Integer> parseVocations(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of(0);
        }

        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Integer::parseInt)
                .toList();
    }
}
JAVA

mkdir -p "src/main/resources/db/migration"
cat > "src/main/resources/db/migration/V31__highscore_vocation_filter_identity.sql" <<'SQL'
-- Store highscores by the ranking scope used on tibia.com.
-- world_id already identifies the world. vocation_filter_id identifies the profession filter:
-- 0 = all vocations/general; other values follow the tibia.com highscores profession parameter.

ALTER TABLE character_statrecords
    ADD COLUMN IF NOT EXISTS vocation_filter_id INTEGER NOT NULL DEFAULT 0;

UPDATE character_statrecords
SET vocation_filter_id = 0
WHERE vocation_filter_id IS NULL;

DROP INDEX IF EXISTS ux_character_statrecords_daily_identity;

DELETE FROM character_statrecords older
USING character_statrecords newer
WHERE older.character_id = newer.character_id
  AND older.world_id = newer.world_id
  AND older.category = newer.category
  AND older.vocation_filter_id = newer.vocation_filter_id
  AND older.date = newer.date
  AND older.id < newer.id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_character_statrecords_daily_scope
    ON character_statrecords(character_id, world_id, category, vocation_filter_id, date);

CREATE INDEX IF NOT EXISTS idx_csr_world_cat_voc_date_rank
    ON character_statrecords(world_id, category, vocation_filter_id, date, rank);

CREATE INDEX IF NOT EXISTS idx_csr_char_cat_voc_date
    ON character_statrecords(character_id, category, vocation_filter_id, date);
SQL

python3 - <<'PY'
from pathlib import Path

# Make the local dev compose explicit. Keep the setting easy to change.
compose = Path('docker-compose.dev.yml')
if compose.exists():
    text = compose.read_text()
    lines = text.splitlines()
    out = []
    seen = set()
    replacements = {
        'TIBIASTATS_SCRAPE_HIGHSCORES_ENABLED': '"true"',
        'TIBIASTATS_SCRAPE_HIGHSCORES_VOCATIONS': '"0,1,2,3,4,5,6"',
        'TIBIASTATS_SCRAPE_HIGHSCORES_MAX_PAGES': '"100"',
        'TIBIASTATS_SCRAPE_HIGHSCORES_PAGE_DELAY_MS': '"1000"',
        'TIBIASTATS_SCRAPE_HIGHSCORES_WORLD_LIMIT': '"0"',
    }
    for line in lines:
        stripped = line.strip()
        replaced = False
        for key, value in replacements.items():
            if stripped.startswith(key + ':'):
                indent = line[:len(line) - len(line.lstrip())]
                out.append(f'{indent}{key}: {value}')
                seen.add(key)
                replaced = True
                break
        if not replaced:
            out.append(line)

    # Insert missing vars after cron if possible.
    missing = [k for k in replacements if k not in seen]
    if missing:
        final = []
        inserted = False
        for line in out:
            final.append(line)
            if 'TIBIASTATS_SCRAPE_HIGHSCORES_CRON:' in line and not inserted:
                indent = line[:len(line) - len(line.lstrip())]
                for key in missing:
                    final.append(f'{indent}{key}: {replacements[key]}')
                inserted = True
        out = final
    compose.write_text('\n'.join(out) + '\n')

# Add application-dev defaults when file exists and has a tibiastats.scrape.highscores block.
appdev = Path('src/main/resources/application-dev.yml')
if appdev.exists():
    text = appdev.read_text()
    if 'vocation-filter' not in text and 'highscores:' in text:
        # Conservative fallback: do not try to parse YAML deeply. Compose envs above are source of truth locally.
        pass
PY

echo "Suporte a highscores por mundo + filtro de vocação aplicado. Backup criado em: $BACKUP_DIR"
echo "Valide com: docker compose -f docker-compose.dev.yml logs -f app | grep --line-buffered HIGHSCORE_SCRAPER"
