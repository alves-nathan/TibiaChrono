#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

if [ ! -f "pom.xml" ] || [ ! -d "src/main/java" ]; then
  echo "ERRO: rode este script na raiz do projeto TibiaChrono." >&2
  exit 1
fi

BACKUP_DIR=".tibiachrono-highscore-scraper-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"

backup_if_exists() {
  local file="$1"
  if [ -f "$file" ]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp -a "$file" "$BACKUP_DIR/$file"
  fi
}

backup_if_exists "src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/domain/port/HighscorePort.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/domain/model/CharacterStatRecord.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java"
backup_if_exists "src/main/resources/application-dev.yml"
backup_if_exists "docker-compose.dev.yml"

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
                         LocalDate date,
                         long value,
                         int rank,
                         Instant scrapedAt);
}
JAVA

cat > "src/main/java/com/nathan/tibiastats/domain/port/HighscorePort.java" <<'JAVA'
package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.StatCategory;
import java.util.List;

public interface HighscorePort {
    record HighscoreRow(int rank, String name, long value) {}
    List<HighscoreRow> fetchHighscores(String world, StatCategory category, int vocationId, int page);
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
                                LocalDate date,
                                long value,
                                int rank,
                                Instant scrapedAt) {
        entityManager.createNativeQuery("""
            insert into character_statrecords
                (character_id, category, date, value, rank, world_id, scraped_at)
            values
                (:characterId, :category, :date, :value, :rank, :worldId, :scrapedAt)
            on conflict (character_id, world_id, category, date)
            do update set
                value = excluded.value,
                rank = excluded.rank,
                scraped_at = excluded.scraped_at
            """)
            .setParameter("characterId", characterId)
            .setParameter("category", category.name())
            .setParameter("date", Date.valueOf(date))
            .setParameter("value", value)
            .setParameter("rank", rank)
            .setParameter("worldId", worldId)
            .setParameter("scrapedAt", Timestamp.from(scrapedAt))
            .executeUpdate();
    }
}
JAVA

mkdir -p "src/main/java/com/nathan/tibiastats/domain/model"
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

mkdir -p "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper"
cat > "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java" <<'JAVA'
package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.CharacterNameNormalizer;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.port.HighscorePort;
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
import java.util.ArrayList;
import java.util.List;

@Component
public class JsoupHighscoreAdapter implements HighscorePort {
    private static final Logger log = LoggerFactory.getLogger(JsoupHighscoreAdapter.class);
    private static final String LOG_PREFIX = "[HIGHSCORE_SCRAPER]";
    private static final String HS_URL = "https://www.tibia.com/community/?subtopic=highscores&world=%s&beprotection=-1&profession=%d&category=%d&currentpage=%d";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; TibiaChrono/1.0; +https://localhost)";
    private static final int TIMEOUT_MS = 20000;

    @Override
    public List<HighscoreRow> fetchHighscores(String world, StatCategory category, int vocationId, int page) {
        int categoryId = mapCategory(category);
        String encodedWorld = URLEncoder.encode(world, StandardCharsets.UTF_8).replace("+", "%20");
        String url = String.format(HS_URL, encodedWorld, vocationId, categoryId, page);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            List<HighscoreRow> rows = parseRows(doc);
            log.debug("{} fetched world={} category={} vocation={} page={} rows={}", LOG_PREFIX, world, category, vocationId, page, rows.size());
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch highscores for world=" + world + ", category=" + category + ", vocation=" + vocationId + ", page=" + page, e);
        }
    }

    private List<HighscoreRow> parseRows(Document doc) {
        List<HighscoreRow> out = new ArrayList<>();

        for (Element table : doc.select("table.TableContent")) {
            for (Element tr : table.select("tr")) {
                Elements tds = tr.select("td");
                if (tds.size() < 3) {
                    continue;
                }

                int rank = parseIntSafe(tds.get(0).text());
                if (rank <= 0) {
                    continue;
                }

                String name = CharacterNameNormalizer.normalize(tds.get(1).text());
                if (name == null || name.isBlank()) {
                    continue;
                }

                long value = parseLongSafe(tds.get(tds.size() - 1).text());
                if (value <= 0) {
                    continue;
                }

                out.add(new HighscoreRow(rank, name, value));
            }
        }

        return out;
    }

    private int mapCategory(StatCategory c) {
        return switch (c) {
            case ACHIEVEMENTS -> 1;
            case AXE_FIGHTING -> 2;
            case CHARM_POINTS -> 3;
            case CLUB_FIGHTING -> 4;
            case DISTANCE_FIGHTING -> 5;
            case EXPERIENCE -> 6;
            case FISHING -> 7;
            case FIST_FIGHTING -> 8;
            case GOSHNARS_TAINT -> 9;
            case LOYALTY_POINTS -> 10;
            case MAGIC_LEVEL -> 11;
            case SHIELDING -> 12;
            case SWORD_FIGHTING -> 13;
            case DROME_SCORE -> 14;
            case BOSS_POINTS -> 15;
        };
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseLongSafe(String s) {
        try {
            return Long.parseLong(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0L;
        }
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

        log.info("{} Starting highscores scrape: worlds={}, categories={}, vocations={}, maxPages={}, date={}",
                LOG_PREFIX, worldList.size(), categories, vocations, safeMaxPages, date);

        for (World world : worldList) {
            processedWorlds++;
            for (StatCategory category : categories) {
                for (Integer vocationId : vocations) {
                    try {
                        savedRows += scrapeCombination(world, category, vocationId, date, safeMaxPages);
                    } catch (Exception e) {
                        failedCombinations++;
                        log.warn("{} Failed combination world={} category={} vocation={}: {}",
                                LOG_PREFIX, world.getName(), category, vocationId, e.getMessage(), e);
                    }
                }
            }
        }

        log.info("{} Finished highscores scrape: processedWorlds={}, savedRows={}, failedCombinations={}, startedAt={}, finishedAt={}",
                LOG_PREFIX, processedWorlds, savedRows, failedCombinations, startedAt, Instant.now());
    }

    private int scrapeCombination(World world, StatCategory category, int vocationId, LocalDate date, int safeMaxPages) {
        int savedRows = 0;
        Instant scrapedAt = Instant.now();

        for (int page = 1; page <= safeMaxPages; page++) {
            List<HighscorePort.HighscoreRow> rows = highscorePort.fetchHighscores(world.getName(), category, vocationId, page);
            if (rows.isEmpty()) {
                log.info("{} Empty page world={} category={} vocation={} page={}", LOG_PREFIX, world.getName(), category, vocationId, page);
                break;
            }

            for (HighscorePort.HighscoreRow row : rows) {
                CharacterEntity character = namingService.ensureCharacterForName(row.name(), row.name());
                persistence.upsertDailyStat(character.getId(), world.getId(), category, date, row.value(), row.rank(), scrapedAt);
                savedRows++;
            }

            log.info("{} Saved page world={} category={} vocation={} page={} rows={}",
                    LOG_PREFIX, world.getName(), category, vocationId, page, rows.size());

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

mkdir -p "src/main/java/com/nathan/tibiastats/application/scheduler"
cat > "src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java" <<'JAVA'
package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.HighscoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HighscoreScrapeScheduler {
    private static final Logger log = LoggerFactory.getLogger(HighscoreScrapeScheduler.class);
    private static final String LOG_PREFIX = "[HIGHSCORE_SCRAPER]";

    private final HighscoreService service;

    @Value("${tibiastats.scrape.highscores.enabled:true}")
    private boolean enabled;

    public HighscoreScrapeScheduler(HighscoreService service) {
        this.service = service;
    }

    @Scheduled(cron = "${tibiastats.scrape.highscores.cron:0 0 7 * * *}")
    public void run() {
        if (!enabled) {
            log.info("{} Scheduler tick ignored because highscores scraper is disabled.", LOG_PREFIX);
            return;
        }

        log.info("{} Scheduler tick started.", LOG_PREFIX);
        service.updateAllHighscores();
    }
}
JAVA

mkdir -p "src/main/resources/db/migration"
cat > "src/main/resources/db/migration/V30__highscore_daily_upsert.sql" <<'SQL'
-- Highscores are daily snapshots. Keep only one row per character/world/category/date.
-- If older experiments created duplicates, keep the most recently scraped row.

delete from character_statrecords older
using character_statrecords newer
where older.character_id = newer.character_id
  and older.world_id = newer.world_id
  and older.category = newer.category
  and older.date = newer.date
  and older.id < newer.id;

create unique index if not exists ux_character_statrecords_daily_identity
    on character_statrecords(character_id, world_id, category, date);

create index if not exists idx_csr_world_cat_rank_date
    on character_statrecords(world_id, category, date, rank);

create index if not exists idx_csr_scraped_at
    on character_statrecords(scraped_at);
SQL

python3 - <<'PY'
from pathlib import Path

# Keep docker-compose settings explicit for local/dev tests.
compose = Path('docker-compose.dev.yml')
if compose.exists():
    text = compose.read_text()
    insert_after = 'TIBIASTATS_SCRAPE_HIGHSCORES_CRON:'
    if 'TIBIASTATS_SCRAPE_HIGHSCORES_ENABLED' not in text:
        lines = text.splitlines()
        out = []
        inserted = False
        for line in lines:
            out.append(line)
            if insert_after in line and not inserted:
                indent = line[:len(line) - len(line.lstrip())]
                out.extend([
                    f'{indent}TIBIASTATS_SCRAPE_HIGHSCORES_ENABLED: "true"',
                    f'{indent}TIBIASTATS_SCRAPE_HIGHSCORES_CATEGORIES: "EXPERIENCE"',
                    f'{indent}TIBIASTATS_SCRAPE_HIGHSCORES_VOCATIONS: "0"',
                    f'{indent}TIBIASTATS_SCRAPE_HIGHSCORES_MAX_PAGES: "1"',
                    f'{indent}TIBIASTATS_SCRAPE_HIGHSCORES_PAGE_DELAY_MS: "1000"',
                    f'{indent}TIBIASTATS_SCRAPE_HIGHSCORES_WORLD_LIMIT: "0"',
                ])
                inserted = True
        compose.write_text('\n'.join(out) + '\n')

appdev = Path('src/main/resources/application-dev.yml')
if appdev.exists():
    text = appdev.read_text()
    if 'max-pages:' not in text and 'highscores:' in text:
        text = text.replace(
            '    highscores:\n      cron: "0 0 7 * * *"       # daily at 3 AM\n',
            '    highscores:\n      enabled: true\n      cron: "0 0 7 * * *"\n      categories: EXPERIENCE\n      vocations: 0\n      max-pages: 1\n      page-delay-ms: 1000\n      world-limit: 0\n'
        )
        appdev.write_text(text)
PY

echo "Highscore scraper aplicado. Backup criado em: $BACKUP_DIR"
echo "Sugestão para validar logs: docker compose -f docker-compose.dev.yml logs -f app | grep --line-buffered HIGHSCORE_SCRAPER"
