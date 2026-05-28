#!/usr/bin/env bash
set -euo pipefail

if [ ! -f "pom.xml" ] || [ ! -d "src/main/java" ]; then
  echo "Execute este script na raiz do projeto TibiaChrono." >&2
  exit 1
fi


BACKUP_DIR=".tibiachrono-repair-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"
for file in \
  src/main/java/com/nathan/tibiastats/domain/port/ScrapePort.java \
  src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java \
  src/main/java/com/nathan/tibiastats/application/service/ScrapeService.java \
  src/main/java/com/nathan/tibiastats/domain/port/CharacterRepositoryPort.java \
  src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java \
  src/main/java/com/nathan/tibiastats/domain/model/Vocation.java \
  src/main/resources/db/migration/V4__ensure_vocations.sql
do
  if [ -f "$file" ]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$file" "$BACKUP_DIR/$file"
  fi
done

echo "Backup criado em: $BACKUP_DIR"

mkdir -p "$(dirname "src/main/java/com/nathan/tibiastats/domain/port/ScrapePort.java")"
cat > "src/main/java/com/nathan/tibiastats/domain/port/ScrapePort.java" <<'EOF_FILE'
package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.World;

import java.time.LocalDate;
import java.util.List;

public interface ScrapePort {
    record WorldSummary(
            String name,
            String pvptype,
            String location,
            int playersOnline,
            String transferType,
            String gameWorldType
    ) {}

    record OnlineCharacterSnapshot(
            String name,
            Integer level,
            String vocation
    ) {}

    record WorldOnline(
            String world,
            int playersOnline,
            List<OnlineCharacterSnapshot> players,
            String onlineRecord,
            LocalDate creationDate,
            String transferType,
            String gameWorldType
    ) {}

    List<WorldSummary> fetchWorldsOverview();
    WorldOnline fetchWorldPage(String worldName, World world);

    String getFormerName(String oldName);
}
EOF_FILE

mkdir -p "$(dirname "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java")"
cat > "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java" <<'EOF_FILE'
package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.ScrapePort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class JsoupScrapeAdapter implements ScrapePort {
    private static final String WORLDS_URL = "https://www.tibia.com/community/?subtopic=worlds";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; TibiaChronoBot/1.0; +https://localhost)";
    private static final int TIMEOUT_MS = 15000;

    @Override
    public List<WorldSummary> fetchWorldsOverview() {
        try {
            Document doc = Jsoup.connect(WORLDS_URL)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            List<WorldSummary> worlds = new ArrayList<>();
            Elements rows = doc.select("div.TableContentContainer table.TableContent tr");

            for (Element tr : rows) {
                Elements tds = tr.select("> td");
                if (tds.size() < 4) {
                    continue;
                }

                String name = tds.get(0).text().trim();
                if (name.isBlank() || name.equalsIgnoreCase("World")) {
                    continue;
                }

                int online = parseIntSafe(tds.get(1).text());
                String location = tds.get(2).text().trim();
                String pvp = tds.get(3).text().trim();
                String additionalInfo = tds.stream()
                        .skip(4)
                        .map(this::cellTextIncludingImageLabels)
                        .collect(Collectors.joining(" "))
                        .trim();

                String transferType = extractTransferType(additionalInfo).orElse("Regular");
                String gameWorldType = extractGameWorldType(additionalInfo).orElse(null);

                worlds.add(new WorldSummary(name, pvp, location, online, transferType, gameWorldType));
            }

            return worlds;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public WorldOnline fetchWorldPage(String worldName, World world) {
        try {
            String url = WORLDS_URL + "&world=" + URLEncoder.encode(worldName, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            int online = 0;
            List<OnlineCharacterSnapshot> players = new ArrayList<>();

            Elements rowsT1 = doc.select("table.Table1 div.InnerTableContainer tbody tr");
            for (Element tr : rowsT1) {
                String rowText = tr.text();
                String value = lastCellText(tr);

                if (rowText.contains("Players Online:")) {
                    online = parseIntSafe(value);
                    continue;
                }

                if (rowText.contains("Creation Date:") && world.getCreationDate() == null) {
                    DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                            .appendPattern("MMMM uuuu")
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter(Locale.ENGLISH);
                    world.setCreationDate(LocalDate.parse(value, formatter));
                    continue;
                }

                if (rowText.contains("Online Record:") && world.getOnlineRecord() == null) {
                    world.setOnlineRecord(value);
                    continue;
                }

                if (rowText.contains("PvP Type:") && world.getPvpType() == null) {
                    world.setPvpType(value);
                    continue;
                }

                if (rowText.contains("Transfer Type:")) {
                    world.setTransferType(value);
                    continue;
                }

                if (rowText.contains("Game World Type:")) {
                    world.setGameWorldType(value);
                }
            }

            Elements rowsT2 = doc.select("table.Table2 div.InnerTableContainer tr");
            for (Element tr : rowsT2) {
                String rowText = tr.text();
                if (rowText.contains("Name [sort] Level [sort] Vocation [sort]")) {
                    continue;
                }

                Elements cols = tr.select("> td");
                if (cols.isEmpty()) {
                    continue;
                }

                String name = tr.select("a[href*=?name=]").text().trim();
                if (name.isBlank()) {
                    continue;
                }

                Integer level = cols.size() > 1 ? parseIntegerOrNull(cols.get(1).text()) : null;
                String vocation = cols.size() > 2 ? blankToNull(cols.get(2).text()) : null;

                players.add(new OnlineCharacterSnapshot(name, level, vocation));
            }

            return new WorldOnline(
                    worldName,
                    online,
                    players,
                    world.getOnlineRecord(),
                    world.getCreationDate(),
                    world.getTransferType(),
                    world.getGameWorldType()
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getFormerName(String name) {
        try {
            String url = "https://www.tibia.com/community/?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            Elements tr = doc.select("table.TableContent tr");
            if (!doc.text().contains("Former Names:")) {
                return name;
            }
            for (Element line : tr) {
                if (line.text().contains("Former Names:")) {
                    return line.select("td:last-child").text();
                }
            }
            return name;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String cellTextIncludingImageLabels(Element cell) {
        StringBuilder value = new StringBuilder(cell.text().trim());
        for (Element img : cell.select("img")) {
            appendIfPresent(value, img.attr("title"));
            appendIfPresent(value, img.attr("alt"));
        }
        return value.toString().trim();
    }

    private Optional<String> extractTransferType(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        if (normalized.contains("blocked") || normalized.contains("closed")) {
            return Optional.of("Blocked");
        }

        if (normalized.contains("locked")) {
            return Optional.of("Locked");
        }

        if (normalized.contains("transfer")) {
            return Optional.of(value.trim());
        }

        return Optional.empty();
    }

    private Optional<String> extractGameWorldType(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        if (normalized.contains("premium")) {
            return Optional.of("Premium");
        }

        if (normalized.contains("experimental")) {
            return Optional.of("Experimental");
        }

        if (normalized.contains("restricted")) {
            return Optional.of("Restricted");
        }

        if (normalized.contains("tournament")) {
            return Optional.of("Tournament");
        }

        return Optional.empty();
    }

    private String lastCellText(Element row) {
        return Objects.requireNonNull(row.lastElementChild()).text().trim();
    }

    private void appendIfPresent(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(value.trim());
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
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

    private int parseIntSafe(String value) {
        return parseIntegerOrNull(value) == null ? 0 : parseIntegerOrNull(value);
    }
}
EOF_FILE

mkdir -p "$(dirname "src/main/java/com/nathan/tibiastats/application/service/ScrapeService.java")"
cat > "src/main/java/com/nathan/tibiastats/application/service/ScrapeService.java" <<'EOF_FILE'
package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.ScrapePlayer;
import com.nathan.tibiastats.domain.model.Vocation;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import com.nathan.tibiastats.domain.port.ScrapePort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ScrapeService {
    private static final Logger log = LoggerFactory.getLogger(ScrapeService.class);

    private final ScrapePort scrapePort;
    private final WorldRepositoryPort worldRepo;
    private final CharacterRepositoryPort characterRepo;
    private final CharacterNamingService namingService;
    private final TransactionTemplate transactionTemplate;

    public ScrapeService(ScrapePort scrapePort,
                         WorldRepositoryPort worldRepo,
                         CharacterRepositoryPort characterRepo,
                         CharacterNamingService namingService,
                         TransactionTemplate transactionTemplate) {
        this.scrapePort = scrapePort;
        this.worldRepo = worldRepo;
        this.characterRepo = characterRepo;
        this.namingService = namingService;
        this.transactionTemplate = transactionTemplate;
    }

    public void updateAllWorlds() {
        List<ScrapePort.WorldSummary> worlds = scrapePort.fetchWorldsOverview();

        if (worlds.isEmpty()) {
            log.warn("Worlds overview returned no worlds. No scrape records will be created.");
            return;
        }

        log.info("Starting world scrape for {} worlds", worlds.size());

        for (ScrapePort.WorldSummary ws : worlds) {
            try {
                World pageWorld = new WorldBuilder()
                        .name(ws.name())
                        .pvpType(ws.pvptype())
                        .location(ws.location())
                        .transferType(ws.transferType())
                        .gameWorldType(ws.gameWorldType())
                        .build();

                ScrapePort.WorldOnline online = scrapePort.fetchWorldPage(ws.name(), pageWorld);
                saveWorldScrape(ws, online);
            } catch (Exception e) {
                log.error("Failed to scrape world {}. Continuing with next world.", ws.name(), e);
            }
        }

        log.info("Finished world scrape cycle");
    }

    private void saveWorldScrape(ScrapePort.WorldSummary ws, ScrapePort.WorldOnline online) {
        transactionTemplate.executeWithoutResult(status -> {
            World world = worldRepo.findByName(ws.name())
                    .orElseGet(() -> worldRepo.save(new WorldBuilder()
                            .name(ws.name())
                            .pvpType(ws.pvptype())
                            .location(ws.location())
                            .transferType(ws.transferType())
                            .gameWorldType(ws.gameWorldType())
                            .build()));

            world.setPvpType(firstNonBlank(ws.pvptype(), world.getPvpType()));
            world.setLocation(firstNonBlank(ws.location(), world.getLocation()));
            world.setOnlineRecord(firstNonBlank(online.onlineRecord(), world.getOnlineRecord()));
            world.setCreationDate(online.creationDate() != null ? online.creationDate() : world.getCreationDate());
            world.setTransferType(firstNonBlank(online.transferType(), ws.transferType(), world.getTransferType()));
            world.setGameWorldType(firstNonBlank(online.gameWorldType(), ws.gameWorldType(), world.getGameWorldType()));
            worldRepo.save(world);

            Scrape scrape = new Scrape();
            scrape.setWorld(world);
            scrape.setScrapeTime(Instant.now());
            scrape.setPlayersOnline(online.playersOnline());

            int addedPlayers = 0;
            for (ScrapePort.OnlineCharacterSnapshot player : online.players()) {
                if (player == null || player.name() == null || player.name().isBlank()) {
                    continue;
                }

                String normalizedPlayerName = player.name().trim();

                // Avoid one HTTP request per online character during the scheduled world scrape.
                // Rename reconciliation can be done later by a dedicated character-detail job.
                var character = namingService.ensureCharacterForName(normalizedPlayerName, normalizedPlayerName);

                characterRepo.findCharacterActiveName(character.getId()).ifPresent(name -> {
                    if (!name.getName().equals(normalizedPlayerName)) {
                        namingService.handleRenamed(character, normalizedPlayerName, name);
                    }
                });

                boolean characterChanged = false;
                if (player.level() != null && !Objects.equals(character.getLevel(), player.level())) {
                    character.setLevel(player.level());
                    characterChanged = true;
                }

                if (player.vocation() != null && !player.vocation().isBlank()) {
                    var vocation = characterRepo.findVocationByNameOrPromotionName(player.vocation().trim());
                    if (vocation.isPresent() && !sameVocation(character.getVocation(), vocation.get())) {
                        character.setVocation(vocation.get());
                        characterChanged = true;
                    }
                }

                if (characterChanged) {
                    characterRepo.save(character);
                }

                ScrapePlayer sp = new ScrapePlayer();
                sp.setCharacter(character);
                scrape.addPlayer(sp);
                addedPlayers++;
            }

            worldRepo.saveScrape(scrape);
            log.info("Saved scrape for world {}: playersOnline={}, listedPlayers={}",
                    world.getName(), scrape.getPlayersOnline(), addedPlayers);
        });
    }

    private boolean sameVocation(Vocation current, Vocation scraped) {
        return current != null && scraped != null && Objects.equals(current.getId(), scraped.getId());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static class WorldBuilder {
        private String name, pvpType, location, transferType, gameWorldType;

        WorldBuilder name(String v) { this.name = v; return this; }
        WorldBuilder pvpType(String v) { this.pvpType = v; return this; }
        WorldBuilder location(String v) { this.location = v; return this; }
        WorldBuilder transferType(String v) { this.transferType = v; return this; }
        WorldBuilder gameWorldType(String v) { this.gameWorldType = v; return this; }

        World build() {
            World w = new World();
            w.setName(name);
            w.setPvpType(pvpType);
            w.setLocation(location);
            w.setTransferType(transferType);
            w.setGameWorldType(gameWorldType);
            return w;
        }
    }
}
EOF_FILE

mkdir -p "$(dirname "src/main/java/com/nathan/tibiastats/domain/port/CharacterRepositoryPort.java")"
cat > "src/main/java/com/nathan/tibiastats/domain/port/CharacterRepositoryPort.java" <<'EOF_FILE'
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
    Optional<Vocation> findVocationByNameOrPromotionName(String name);
    CharacterStatRecord saveStat(CharacterStatRecord r);
    List<CharacterStatRecord> findStatsBy(CharacterEntity c, StatCategory category);
}
EOF_FILE

mkdir -p "$(dirname "src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java")"
cat > "src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java" <<'EOF_FILE'
package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
EOF_FILE

mkdir -p "$(dirname "src/main/java/com/nathan/tibiastats/domain/model/Vocation.java")"
cat > "src/main/java/com/nathan/tibiastats/domain/model/Vocation.java" <<'EOF_FILE'
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
EOF_FILE

mkdir -p "$(dirname "src/main/resources/db/migration/V4__ensure_vocations.sql")"
cat > "src/main/resources/db/migration/V4__ensure_vocations.sql" <<'EOF_FILE'
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
EOF_FILE

chmod 644 src/main/java/com/nathan/tibiastats/domain/port/ScrapePort.java \
  src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java \
  src/main/java/com/nathan/tibiastats/application/service/ScrapeService.java \
  src/main/java/com/nathan/tibiastats/domain/port/CharacterRepositoryPort.java \
  src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java \
  src/main/java/com/nathan/tibiastats/domain/model/Vocation.java

echo "Arquivos normalizados. Agora rode: docker compose -f docker-compose.dev.yml restart app"
