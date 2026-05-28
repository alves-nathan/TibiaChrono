#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
if [ ! -f "$ROOT/pom.xml" ] || [ ! -d "$ROOT/src/main/java" ]; then
  echo "Run this script from the TibiaChrono project root." >&2
  exit 1
fi
BACKUP_DIR="$ROOT/.tibiachrono-steps-1-2-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"
backup_file() {
  local file="$1"
  if [ -f "$ROOT/$file" ]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$ROOT/$file" "$BACKUP_DIR/$file"
  fi
}

echo "Backing up affected files to $BACKUP_DIR"
backup_file 'docker-compose.dev.yml'
backup_file 'src/main/resources/application-dev.yml'
backup_file 'src/main/resources/graphql/schema.graphqls'
backup_file 'src/main/resources/db/migration/V40__scrape_jobs.sql'
backup_file 'src/main/resources/db/migration/V41__highscore_new_categories.sql'
backup_file 'src/main/java/com/nathan/tibiastats/application/service/ScrapeJobResult.java'
backup_file 'src/main/java/com/nathan/tibiastats/domain/model/ScrapeJobExecution.java'
backup_file 'src/main/java/com/nathan/tibiastats/infrastructure/persistence/ScrapeJobExecutionRepository.java'
backup_file 'src/main/java/com/nathan/tibiastats/application/service/ScrapeJobService.java'
backup_file 'src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java'
backup_file 'src/main/java/com/nathan/tibiastats/application/service/ScrapeService.java'
backup_file 'src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java'
backup_file 'src/main/java/com/nathan/tibiastats/application/service/CharacterDetailsService.java'
backup_file 'src/main/java/com/nathan/tibiastats/application/scheduler/WorldScrapeScheduler.java'
backup_file 'src/main/java/com/nathan/tibiastats/application/scheduler/CharacterDetailsScrapeScheduler.java'
backup_file 'src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java'
backup_file 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/WorldController.java'
backup_file 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java'
backup_file 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/HighscoreController.java'
backup_file 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/ScrapeJobController.java'
backup_file 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/graphql/StatsGraphQLController.java'
backup_file 'src/main/java/com/nathan/tibiastats/domain/model/StatCategory.java'
backup_file 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java'
backup_file 'src/main/java/com/nathan/tibiastats/config/AppProperties.java'

mkdir -p "$ROOT/."
cat > "$ROOT/docker-compose.dev.yml" <<'__TIBIACHRONO_FILE__'
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

mkdir -p "$ROOT/src/main/resources"
cat > "$ROOT/src/main/resources/application-dev.yml" <<'__TIBIACHRONO_FILE__'
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
    defer-datasource-initialization: false
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
      rate-ms: 60000
      initial-delay-ms: 15000
      batch-size: 50
    highscores:
      enabled: true
      cron: "0 */5 * * * *"
      categories: "ACHIEVEMENTS,AXE_FIGHTING,BOSS_POINTS,BOUNTY_POINTS_EARNED,CHARM_POINTS,CLUB_FIGHTING,DISTANCE_FIGHTING,DROME_SCORE,EXPERIENCE,FISHING,FIST_FIGHTING,GOSHNARS_TAINT,LOYALTY_POINTS,MAGIC_LEVEL,SHIELDING,SWORD_FIGHTING,WEEKLY_TASKS_COMPLETED"
      vocations: "0,1,2,3,4,5,6"
      max-pages: 100
      page-delay-ms: 1000
      world-limit: 0
  jwt:
    access-ttl-ms: 900000
    refresh-ttl-ms: 1209600000
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/resources/graphql"
cat > "$ROOT/src/main/resources/graphql/schema.graphqls" <<'__TIBIACHRONO_FILE__'
schema {
    query: Query
}

enum StatCategory {
    ACHIEVEMENTS
    AXE_FIGHTING
    BOSS_POINTS
    BOUNTY_POINTS_EARNED
    CHARM_POINTS
    CLUB_FIGHTING
    DISTANCE_FIGHTING
    DROME_SCORE
    EXPERIENCE
    FISHING
    FIST_FIGHTING
    GOSHNARS_TAINT
    LOYALTY_POINTS
    MAGIC_LEVEL
    SHIELDING
    SWORD_FIGHTING
    WEEKLY_TASKS_COMPLETED
}

type Query {
    onlineTotal: Int!
    worldsOnline: [WorldOnline!]!
    worldOnlineNow(name: String!): WorldOnline
    worldOnlineHistory(name: String!, from: String, to: String): [OnlinePoint!]!

    worlds: [World!]!
    world(name: String!): World
    character(name: String!): Character
    characterNames(name: String!): [CharacterName!]!
    characterHighscores(
        name: String!,
        category: StatCategory,
        world: String,
        vocationFilterId: Int,
        from: String,
        to: String,
        limit: Int
    ): [HighscoreRecord!]!
    highscores(
        world: String!,
        category: StatCategory!,
        vocationFilterId: Int,
        date: String,
        limit: Int
    ): [HighscoreRecord!]!
    scrapeJobs(jobName: String, status: String, limit: Int): [ScrapeJob!]!

    # Legacy alias kept for compatibility with the previous UI/tests.
    characterStatHistory(name: String!, category: StatCategory!): [HighscoreRecord!]!
}

type WorldOnline {
    name: String!
    playersOnline: Int!
}

type OnlinePoint {
    timestamp: String!
    playersOnline: Int!
}

type World {
    id: ID!
    name: String!
    pvpType: String
    location: String
    onlineRecord: String
    creationDate: String
    transferType: String
    gameWorldType: String
    playersOnline: Int
    lastScrapedAt: String
}

type Character {
    id: ID!
    activeName: String
    level: Int
    sex: String
    vocation: String
    vocationPromotionName: String
    achievementPoints: Int
    residence: String
    lastLogin: String
    accStatus: String
    creationDate: String
    detailsLastScrapedAt: String
    detailsLastScrapeStatus: String
}

type CharacterName {
    id: ID!
    characterId: ID!
    name: String!
    active: Boolean
    inactiveDate: String
}

type HighscoreRecord {
    id: ID!
    rank: Int
    characterName: String
    characterId: ID!
    world: String!
    category: String!
    vocationFilterId: Int
    date: String!
    value: String
    valueText: String
    scrapedAt: String!
}

type ScrapeJob {
    id: ID!
    jobName: String!
    status: String!
    startedAt: String!
    finishedAt: String
    durationMs: String
    itemsProcessed: Int
    itemsCreated: Int
    itemsUpdated: Int
    itemsFailed: Int
    errorMessage: String
}
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/resources/db/migration"
cat > "$ROOT/src/main/resources/db/migration/V40__scrape_jobs.sql" <<'__TIBIACHRONO_FILE__'
CREATE TABLE IF NOT EXISTS scrape_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_name TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,
    items_processed INTEGER NOT NULL DEFAULT 0,
    items_created INTEGER NOT NULL DEFAULT 0,
    items_updated INTEGER NOT NULL DEFAULT 0,
    items_failed INTEGER NOT NULL DEFAULT 0,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_scrape_jobs_name_started_at
    ON scrape_jobs(job_name, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_scrape_jobs_status_started_at
    ON scrape_jobs(status, started_at DESC);
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/resources/db/migration"
cat > "$ROOT/src/main/resources/db/migration/V41__highscore_new_categories.sql" <<'__TIBIACHRONO_FILE__'
ALTER TABLE character_statrecords
    DROP CONSTRAINT IF EXISTS character_statrecords_category_check;

ALTER TABLE character_statrecords
    ADD CONSTRAINT character_statrecords_category_check CHECK (
        category IN (
            'ACHIEVEMENTS',
            'AXE_FIGHTING',
            'BOSS_POINTS',
            'BOUNTY_POINTS_EARNED',
            'CHARM_POINTS',
            'CLUB_FIGHTING',
            'DISTANCE_FIGHTING',
            'DROME_SCORE',
            'EXPERIENCE',
            'FISHING',
            'FIST_FIGHTING',
            'GOSHNARS_TAINT',
            'LOYALTY_POINTS',
            'MAGIC_LEVEL',
            'SHIELDING',
            'SWORD_FIGHTING',
            'WEEKLY_TASKS_COMPLETED'
        )
    );
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/application/service"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/application/service/ScrapeJobResult.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.application.service;

public record ScrapeJobResult(
        int itemsProcessed,
        int itemsCreated,
        int itemsUpdated,
        int itemsFailed
) {
    public static ScrapeJobResult empty() {
        return new ScrapeJobResult(0, 0, 0, 0);
    }

    public static ScrapeJobResult of(int itemsProcessed, int itemsCreated, int itemsUpdated, int itemsFailed) {
        return new ScrapeJobResult(
                Math.max(0, itemsProcessed),
                Math.max(0, itemsCreated),
                Math.max(0, itemsUpdated),
                Math.max(0, itemsFailed)
        );
    }
}
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/domain/model"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/domain/model/ScrapeJobExecution.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scrape_jobs")
public class ScrapeJobExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "items_processed")
    private Integer itemsProcessed;

    @Column(name = "items_created")
    private Integer itemsCreated;

    @Column(name = "items_updated")
    private Integer itemsUpdated;

    @Column(name = "items_failed")
    private Integer itemsFailed;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Integer getItemsProcessed() { return itemsProcessed; }
    public void setItemsProcessed(Integer itemsProcessed) { this.itemsProcessed = itemsProcessed; }

    public Integer getItemsCreated() { return itemsCreated; }
    public void setItemsCreated(Integer itemsCreated) { this.itemsCreated = itemsCreated; }

    public Integer getItemsUpdated() { return itemsUpdated; }
    public void setItemsUpdated(Integer itemsUpdated) { this.itemsUpdated = itemsUpdated; }

    public Integer getItemsFailed() { return itemsFailed; }
    public void setItemsFailed(Integer itemsFailed) { this.itemsFailed = itemsFailed; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/persistence"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/persistence/ScrapeJobExecutionRepository.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.ScrapeJobExecution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrapeJobExecutionRepository extends JpaRepository<ScrapeJobExecution, Long> {
}
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/application/service"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/application/service/ScrapeJobService.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.ScrapeJobExecution;
import com.nathan.tibiastats.infrastructure.persistence.ScrapeJobExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class ScrapeJobService {
    public static final String WORLD_SCRAPER = "WORLD_SCRAPER";
    public static final String CHARACTER_DETAILS_SCRAPER = "CHARACTER_DETAILS_SCRAPER";
    public static final String HIGHSCORE_SCRAPER = "HIGHSCORE_SCRAPER";

    private final ScrapeJobExecutionRepository repository;

    public ScrapeJobService(ScrapeJobExecutionRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(String jobName) {
        ScrapeJobExecution job = new ScrapeJobExecution();
        job.setJobName(jobName);
        job.setStatus("RUNNING");
        job.setStartedAt(Instant.now());
        job.setItemsProcessed(0);
        job.setItemsCreated(0);
        job.setItemsUpdated(0);
        job.setItemsFailed(0);
        return repository.save(job).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishSuccess(Long jobId, ScrapeJobResult result) {
        ScrapeJobExecution job = repository.findById(jobId).orElseThrow();
        Instant finishedAt = Instant.now();
        job.setStatus("SUCCESS");
        job.setFinishedAt(finishedAt);
        job.setDurationMs(Duration.between(job.getStartedAt(), finishedAt).toMillis());
        job.setItemsProcessed(result.itemsProcessed());
        job.setItemsCreated(result.itemsCreated());
        job.setItemsUpdated(result.itemsUpdated());
        job.setItemsFailed(result.itemsFailed());
        job.setErrorMessage(null);
        repository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishFailure(Long jobId, ScrapeJobResult partialResult, Throwable error) {
        ScrapeJobExecution job = repository.findById(jobId).orElseThrow();
        Instant finishedAt = Instant.now();
        job.setStatus("FAILED");
        job.setFinishedAt(finishedAt);
        job.setDurationMs(Duration.between(job.getStartedAt(), finishedAt).toMillis());
        job.setItemsProcessed(partialResult == null ? 0 : partialResult.itemsProcessed());
        job.setItemsCreated(partialResult == null ? 0 : partialResult.itemsCreated());
        job.setItemsUpdated(partialResult == null ? 0 : partialResult.itemsUpdated());
        job.setItemsFailed(partialResult == null ? 1 : Math.max(1, partialResult.itemsFailed()));
        job.setErrorMessage(truncate(error == null ? "Unknown error" : error.getMessage(), 4000));
        repository.save(job);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/application/service"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ApiQueryService {
    private final NamedParameterJdbcTemplate jdbc;

    public ApiQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public List<WorldView> findWorlds() {
        return jdbc.query("""
                select
                    w.id,
                    w.name,
                    w.pvp_type,
                    w.location,
                    w.online_record,
                    w.creation_date,
                    w.transfer_type,
                    w.game_world_type,
                    latest.players_online,
                    latest.scrape_time as last_scraped_at
                from worlds w
                left join lateral (
                    select s.players_online, s.scrape_time
                    from scrapes s
                    where s.world_id = w.id
                    order by s.scrape_time desc
                    limit 1
                ) latest on true
                order by w.name
                """, new MapSqlParameterSource(), this::mapWorld);
    }

    public Optional<WorldView> findWorld(String name) {
        var params = new MapSqlParameterSource("name", name);
        var result = jdbc.query("""
                select
                    w.id,
                    w.name,
                    w.pvp_type,
                    w.location,
                    w.online_record,
                    w.creation_date,
                    w.transfer_type,
                    w.game_world_type,
                    latest.players_online,
                    latest.scrape_time as last_scraped_at
                from worlds w
                left join lateral (
                    select s.players_online, s.scrape_time
                    from scrapes s
                    where s.world_id = w.id
                    order by s.scrape_time desc
                    limit 1
                ) latest on true
                where lower(w.name) = lower(:name)
                limit 1
                """, params, this::mapWorld);
        return result.stream().findFirst();
    }

    public Optional<CharacterView> findCharacter(String name) {
        var params = new MapSqlParameterSource("name", name);
        var result = jdbc.query("""
                with resolved as (
                    select lookup.character_id
                    from character_names lookup
                    where lower(lookup.name) = lower(:name)
                      and (
                          lookup.active is true
                          or lookup.inactive_date >= now() - interval '6 months'
                      )
                    order by lookup.active desc, lookup.inactive_date desc nulls last
                    limit 1
                )
                select
                    c.id,
                    active_name.name as active_name,
                    c.level,
                    c.sex,
                    v.name as vocation,
                    v.promotion_name as vocation_promotion_name,
                    c.achievement_points,
                    c.residence,
                    c.last_login,
                    c.acc_status,
                    c.creation_date,
                    c.details_last_scraped_at,
                    c.details_last_scrape_status
                from resolved r
                join characters c on c.id = r.character_id
                left join character_names active_name on active_name.character_id = c.id and active_name.active is true
                left join vocations v on v.id = c.vocation_id
                limit 1
                """, params, this::mapCharacter);
        return result.stream().findFirst();
    }

    public List<CharacterNameView> findCharacterNames(String name) {
        var character = findCharacter(name);
        if (character.isEmpty()) {
            return List.of();
        }
        return findCharacterNames(character.get().id());
    }

    public List<CharacterNameView> findCharacterNames(Long characterId) {
        var params = new MapSqlParameterSource("characterId", characterId);
        return jdbc.query("""
                select id, character_id, name, active, inactive_date
                from character_names
                where character_id = :characterId
                order by active desc, inactive_date desc nulls first, name
                """, params, this::mapCharacterName);
    }

    public List<HighscoreView> findCharacterHighscores(String characterName,
                                                       StatCategory category,
                                                       String world,
                                                       Integer vocationFilterId,
                                                       LocalDate from,
                                                       LocalDate to,
                                                       int limit) {
        var sql = new StringBuilder("""
                with resolved as (
                    select lookup.character_id
                    from character_names lookup
                    where lower(lookup.name) = lower(:characterName)
                      and (
                          lookup.active is true
                          or lookup.inactive_date >= now() - interval '6 months'
                      )
                    order by lookup.active desc, lookup.inactive_date desc nulls last
                    limit 1
                )
                select
                    csr.id,
                    csr.rank,
                    active_name.name as character_name,
                    csr.character_id,
                    w.name as world,
                    csr.category,
                    csr.vocation_filter_id,
                    csr.date,
                    csr.value,
                    csr.scraped_at
                from character_statrecords csr
                join resolved r on r.character_id = csr.character_id
                join worlds w on w.id = csr.world_id
                left join character_names active_name on active_name.character_id = csr.character_id and active_name.active is true
                where 1 = 1
                """);
        var params = new MapSqlParameterSource("characterName", characterName)
                .addValue("limit", safeLimit(limit));
        appendHighscoreFilters(sql, params, category, world, vocationFilterId, from, to, null);
        sql.append(" order by csr.date desc, w.name, csr.category, csr.vocation_filter_id, csr.rank limit :limit");
        return jdbc.query(sql.toString(), params, this::mapHighscore);
    }

    public List<HighscoreView> findHighscores(String world,
                                              StatCategory category,
                                              Integer vocationFilterId,
                                              LocalDate date,
                                              int limit) {
        var sql = new StringBuilder("""
                select
                    csr.id,
                    csr.rank,
                    active_name.name as character_name,
                    csr.character_id,
                    w.name as world,
                    csr.category,
                    csr.vocation_filter_id,
                    csr.date,
                    csr.value,
                    csr.scraped_at
                from character_statrecords csr
                join worlds w on w.id = csr.world_id
                left join character_names active_name on active_name.character_id = csr.character_id and active_name.active is true
                where 1 = 1
                """);
        var params = new MapSqlParameterSource("limit", safeLimit(limit));
        appendHighscoreFilters(sql, params, category, world, vocationFilterId, null, null, date);
        sql.append(" order by csr.date desc, csr.rank asc limit :limit");
        return jdbc.query(sql.toString(), params, this::mapHighscore);
    }

    public List<ScrapeJobView> findScrapeJobs(String jobName, String status, int limit) {
        var sql = new StringBuilder("""
                select
                    id,
                    job_name,
                    status,
                    started_at,
                    finished_at,
                    duration_ms,
                    items_processed,
                    items_created,
                    items_updated,
                    items_failed,
                    error_message
                from scrape_jobs
                where 1 = 1
                """);
        var params = new MapSqlParameterSource("limit", safeLimit(limit));
        if (jobName != null && !jobName.isBlank()) {
            sql.append(" and job_name = :jobName");
            params.addValue("jobName", jobName.trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and status = :status");
            params.addValue("status", status.trim().toUpperCase());
        }
        sql.append(" order by started_at desc limit :limit");
        return jdbc.query(sql.toString(), params, this::mapScrapeJob);
    }

    private void appendHighscoreFilters(StringBuilder sql,
                                        MapSqlParameterSource params,
                                        StatCategory category,
                                        String world,
                                        Integer vocationFilterId,
                                        LocalDate from,
                                        LocalDate to,
                                        LocalDate exactDate) {
        if (category != null) {
            sql.append(" and csr.category = :category");
            params.addValue("category", category.name());
        }
        if (world != null && !world.isBlank()) {
            sql.append(" and lower(w.name) = lower(:world)");
            params.addValue("world", world.trim());
        }
        if (vocationFilterId != null) {
            sql.append(" and csr.vocation_filter_id = :vocationFilterId");
            params.addValue("vocationFilterId", vocationFilterId);
        }
        if (from != null) {
            sql.append(" and csr.date >= :fromDate");
            params.addValue("fromDate", from);
        }
        if (to != null) {
            sql.append(" and csr.date <= :toDate");
            params.addValue("toDate", to);
        }
        if (exactDate != null) {
            sql.append(" and csr.date = :exactDate");
            params.addValue("exactDate", exactDate);
        }
    }

    private int safeLimit(int requested) {
        if (requested <= 0) {
            return 100;
        }
        return Math.min(requested, 1000);
    }

    private WorldView mapWorld(ResultSet rs, int rowNum) throws SQLException {
        return new WorldView(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("pvp_type"),
                rs.getString("location"),
                rs.getString("online_record"),
                rs.getObject("creation_date", LocalDate.class),
                rs.getString("transfer_type"),
                rs.getString("game_world_type"),
                getNullableInteger(rs, "players_online"),
                toInstant(rs.getTimestamp("last_scraped_at"))
        );
    }

    private CharacterView mapCharacter(ResultSet rs, int rowNum) throws SQLException {
        return new CharacterView(
                rs.getLong("id"),
                rs.getString("active_name"),
                getNullableInteger(rs, "level"),
                rs.getString("sex"),
                rs.getString("vocation"),
                rs.getString("vocation_promotion_name"),
                getNullableInteger(rs, "achievement_points"),
                rs.getString("residence"),
                rs.getObject("last_login", OffsetDateTime.class),
                rs.getString("acc_status"),
                toInstant(rs.getTimestamp("creation_date")),
                toInstant(rs.getTimestamp("details_last_scraped_at")),
                rs.getString("details_last_scrape_status")
        );
    }

    private CharacterNameView mapCharacterName(ResultSet rs, int rowNum) throws SQLException {
        return new CharacterNameView(
                rs.getLong("id"),
                rs.getLong("character_id"),
                rs.getString("name"),
                rs.getBoolean("active"),
                toInstant(rs.getTimestamp("inactive_date"))
        );
    }

    private HighscoreView mapHighscore(ResultSet rs, int rowNum) throws SQLException {
        Long value = rs.getLong("value");
        if (rs.wasNull()) {
            value = null;
        }
        return new HighscoreView(
                rs.getLong("id"),
                getNullableInteger(rs, "rank"),
                rs.getString("character_name"),
                rs.getLong("character_id"),
                rs.getString("world"),
                rs.getString("category"),
                getNullableInteger(rs, "vocation_filter_id"),
                rs.getObject("date", LocalDate.class),
                value,
                toInstant(rs.getTimestamp("scraped_at"))
        );
    }

    private ScrapeJobView mapScrapeJob(ResultSet rs, int rowNum) throws SQLException {
        return new ScrapeJobView(
                rs.getLong("id"),
                rs.getString("job_name"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("finished_at")),
                getNullableLong(rs, "duration_ms"),
                getNullableInteger(rs, "items_processed"),
                getNullableInteger(rs, "items_created"),
                getNullableInteger(rs, "items_updated"),
                getNullableInteger(rs, "items_failed"),
                rs.getString("error_message")
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Integer getNullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record WorldView(
            Integer id,
            String name,
            String pvpType,
            String location,
            String onlineRecord,
            LocalDate creationDate,
            String transferType,
            String gameWorldType,
            Integer playersOnline,
            Instant lastScrapedAt
    ) {}

    public record CharacterView(
            Long id,
            String activeName,
            Integer level,
            String sex,
            String vocation,
            String vocationPromotionName,
            Integer achievementPoints,
            String residence,
            OffsetDateTime lastLogin,
            String accStatus,
            Instant creationDate,
            Instant detailsLastScrapedAt,
            String detailsLastScrapeStatus
    ) {}

    public record CharacterNameView(
            Long id,
            Long characterId,
            String name,
            Boolean active,
            Instant inactiveDate
    ) {}

    public record HighscoreView(
            Long id,
            Integer rank,
            String characterName,
            Long characterId,
            String world,
            String category,
            Integer vocationFilterId,
            LocalDate date,
            Long value,
            Instant scrapedAt
    ) {
        public String valueText() {
            return value == null ? null : value.toString();
        }
    }

    public record ScrapeJobView(
            Long id,
            String jobName,
            String status,
            Instant startedAt,
            Instant finishedAt,
            Long durationMs,
            Integer itemsProcessed,
            Integer itemsCreated,
            Integer itemsUpdated,
            Integer itemsFailed,
            String errorMessage
    ) {}
}
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/application/service"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/application/service/ScrapeService.java" <<'__TIBIACHRONO_FILE__'
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

    public ScrapeJobResult updateAllWorlds() {
        List<ScrapePort.WorldSummary> worlds = scrapePort.fetchWorldsOverview();

        if (worlds.isEmpty()) {
            log.warn("Worlds overview returned no worlds. No scrape records will be created.");
            return ScrapeJobResult.empty();
        }

        log.info("Starting world scrape for {} worlds", worlds.size());

        int processed = 0;
        int updated = 0;
        int failed = 0;

        for (ScrapePort.WorldSummary ws : worlds) {
            processed++;
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
                updated++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to scrape world {}. Continuing with next world.", ws.name(), e);
            }
        }

        log.info("Finished world scrape cycle: processed={}, updated={}, failed={}", processed, updated, failed);
        return ScrapeJobResult.of(processed, 0, updated, failed);
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
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/application/service"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/application/service/HighscoreService.java" <<'__TIBIACHRONO_FILE__'
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

    public ScrapeJobResult updateAllHighscores() {
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
        return ScrapeJobResult.of(savedRows, 0, savedRows, failedCombinations);
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
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/application/service"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/application/service/CharacterDetailsService.java" <<'__TIBIACHRONO_FILE__'
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
    public ScrapeJobResult updateMissingDetailsBatch() {
        int batchSize = Math.max(1, appProperties.getCharacterDetails().getBatchSize());
        List<CharacterName> namesToRefresh = characterRepo.findActiveNamesForDetailsRefresh(batchSize);

        if (namesToRefresh.isEmpty()) {
            log.info("{} No active characters available for detail scrape", LOG_PREFIX);
            return ScrapeJobResult.empty();
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
        int processed = updated + unchanged + notFound + empty + failed;
        return ScrapeJobResult.of(processed, 0, updated + unchanged, notFound + empty + failed);
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
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/application/scheduler"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/application/scheduler/WorldScrapeScheduler.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.ScrapeJobResult;
import com.nathan.tibiastats.application.service.ScrapeJobService;
import com.nathan.tibiastats.application.service.ScrapeService;
import com.nathan.tibiastats.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorldScrapeScheduler {
    private final ScrapeService scrapeService;
    private final AppProperties props;
    private final ScrapeJobService scrapeJobService;

    public WorldScrapeScheduler(ScrapeService scrapeService,
                                AppProperties props,
                                ScrapeJobService scrapeJobService) {
        this.scrapeService = scrapeService;
        this.props = props;
        this.scrapeJobService = scrapeJobService;
    }

    @Scheduled(fixedRateString = "${tibiastats.scrape.worlds.rate-ms:60000}")
    public void run() {
        Long jobId = scrapeJobService.start(ScrapeJobService.WORLD_SCRAPER);
        try {
            ScrapeJobResult result = scrapeService.updateAllWorlds();
            scrapeJobService.finishSuccess(jobId, result);
        } catch (Exception e) {
            scrapeJobService.finishFailure(jobId, ScrapeJobResult.empty(), e);
            throw e;
        }
    }
}
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/application/scheduler"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/application/scheduler/CharacterDetailsScrapeScheduler.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.CharacterDetailsService;
import com.nathan.tibiastats.application.service.ScrapeJobResult;
import com.nathan.tibiastats.application.service.ScrapeJobService;
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
    private final ScrapeJobService scrapeJobService;

    public CharacterDetailsScrapeScheduler(CharacterDetailsService characterDetailsService,
                                           AppProperties appProperties,
                                           ScrapeJobService scrapeJobService) {
        this.characterDetailsService = characterDetailsService;
        this.appProperties = appProperties;
        this.scrapeJobService = scrapeJobService;
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

        Long jobId = scrapeJobService.start(ScrapeJobService.CHARACTER_DETAILS_SCRAPER);
        int batchSize = Math.max(1, appProperties.getCharacterDetails().getBatchSize());
        log.info("{} Scheduler tick started. batchSize={}", LOG_PREFIX, batchSize);
        try {
            ScrapeJobResult result = characterDetailsService.updateMissingDetailsBatch();
            scrapeJobService.finishSuccess(jobId, result);
            log.info("{} Scheduler tick finished", LOG_PREFIX);
        } catch (Exception e) {
            scrapeJobService.finishFailure(jobId, ScrapeJobResult.empty(), e);
            throw e;
        }
    }
}
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/application/scheduler"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/application/scheduler/HighscoreScrapeScheduler.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.HighscoreService;
import com.nathan.tibiastats.application.service.ScrapeJobResult;
import com.nathan.tibiastats.application.service.ScrapeJobService;
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
    private final ScrapeJobService scrapeJobService;

    @Value("${tibiastats.scrape.highscores.enabled:true}")
    private boolean enabled;

    @Value("${tibiastats.scrape.highscores.cron:0 0 7 * * *}")
    private String cron;

    public HighscoreScrapeScheduler(HighscoreService service,
                                    ScrapeJobService scrapeJobService) {
        this.service = service;
        this.scrapeJobService = scrapeJobService;
    }

    @Scheduled(cron = "${tibiastats.scrape.highscores.cron:0 0 7 * * *}")
    public void run() {
        if (!enabled) {
            log.info("{} Scheduler tick ignored because highscores scraper is disabled.", LOG_PREFIX);
            return;
        }

        Long jobId = scrapeJobService.start(ScrapeJobService.HIGHSCORE_SCRAPER);
        log.info("{} Scheduler tick started. cron={}", LOG_PREFIX, cron);
        try {
            ScrapeJobResult result = service.updateAllHighscores();
            scrapeJobService.finishSuccess(jobId, result);
            log.info("{} Scheduler tick finished.", LOG_PREFIX);
        } catch (Exception e) {
            scrapeJobService.finishFailure(jobId, ScrapeJobResult.empty(), e);
            throw e;
        }
    }
}
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/WorldController.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.ApiQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/worlds")
public class WorldController {
    private final ApiQueryService queries;

    public WorldController(ApiQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<ApiQueryService.WorldView> listWorlds() {
        return queries.findWorlds();
    }

    @GetMapping("/{name}")
    public ApiQueryService.WorldView getWorld(@PathVariable String name) {
        return queries.findWorld(name).orElseThrow();
    }
}
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.ApiQueryService;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {
    private final ApiQueryService queries;

    public CharacterController(ApiQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/{name}")
    public ApiQueryService.CharacterView getCharacter(@PathVariable String name) {
        return queries.findCharacter(name).orElseThrow();
    }

    @GetMapping("/{name}/names")
    public List<ApiQueryService.CharacterNameView> getCharacterNames(@PathVariable String name) {
        return queries.findCharacterNames(name);
    }

    @GetMapping("/{name}/highscores")
    public List<ApiQueryService.HighscoreView> getCharacterHighscores(
            @PathVariable String name,
            @RequestParam(required = false) StatCategory category,
            @RequestParam(required = false) String world,
            @RequestParam(required = false) Integer vocationFilterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return queries.findCharacterHighscores(name, category, world, vocationFilterId, from, to, limit);
    }
}
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/HighscoreController.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.ApiQueryService;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/highscores")
public class HighscoreController {
    private final ApiQueryService queries;

    public HighscoreController(ApiQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<ApiQueryService.HighscoreView> getHighscores(
            @RequestParam String world,
            @RequestParam StatCategory category,
            @RequestParam(required = false) Integer vocationFilterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return queries.findHighscores(world, category, vocationFilterId, date, limit);
    }
}
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/ScrapeJobController.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.ApiQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scrape-jobs")
public class ScrapeJobController {
    private final ApiQueryService queries;

    public ScrapeJobController(ApiQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<ApiQueryService.ScrapeJobView> listJobs(
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return queries.findScrapeJobs(jobName, status, limit);
    }
}
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/graphql"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/graphql/StatsGraphQLController.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.infrastructure.adapter.web.graphql;

import com.nathan.tibiastats.application.service.AnalyticsService;
import com.nathan.tibiastats.application.service.ApiQueryService;
import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class StatsGraphQLController {

    private final AnalyticsService analytics;
    private final WorldRepositoryPort worlds;
    private final ApiQueryService queries;

    public StatsGraphQLController(AnalyticsService analytics,
                                  WorldRepositoryPort worlds,
                                  ApiQueryService queries) {
        this.analytics = analytics;
        this.worlds = worlds;
        this.queries = queries;
    }

    @QueryMapping
    public Integer onlineTotal() {
        return analytics.getCurrentOnlineTotal();
    }

    @QueryMapping
    public List<Map<String, Object>> worldsOnline() {
        return worlds.findAll().stream()
                .map(w -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", w.getName());
                    int online = worlds.findLatestByWorld(w)
                            .map(Scrape::getPlayersOnline)
                            .orElse(0);
                    m.put("playersOnline", online);
                    return m;
                })
                .collect(Collectors.toList());
    }

    @QueryMapping
    public Map<String, Object> worldOnlineNow(@Argument String name) {
        var w = worlds.findByName(name).orElseThrow();
        var latest = worlds.findLatestByWorld(w);
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("playersOnline", latest.map(Scrape::getPlayersOnline).orElse(0));
        return m;
    }

    @QueryMapping
    public List<Map<String, Object>> worldOnlineHistory(
            @Argument String name,
            @Argument String from,
            @Argument String to) {
        Instant start = (from == null)
                ? Instant.now().minusSeconds(86400)
                : Instant.parse(from);
        Instant end = (to == null)
                ? Instant.now()
                : Instant.parse(to);

        return analytics.getWorldOnlineHistory(name, start, end).stream()
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("timestamp", p.timestamp().toString());
                    m.put("playersOnline", p.playersOnline());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @QueryMapping
    public List<ApiQueryService.WorldView> worlds() {
        return queries.findWorlds();
    }

    @QueryMapping
    public ApiQueryService.WorldView world(@Argument String name) {
        return queries.findWorld(name).orElse(null);
    }

    @QueryMapping
    public ApiQueryService.CharacterView character(@Argument String name) {
        return queries.findCharacter(name).orElse(null);
    }

    @QueryMapping
    public List<ApiQueryService.CharacterNameView> characterNames(@Argument String name) {
        return queries.findCharacterNames(name);
    }

    @QueryMapping
    public List<ApiQueryService.HighscoreView> characterHighscores(
            @Argument String name,
            @Argument StatCategory category,
            @Argument String world,
            @Argument Integer vocationFilterId,
            @Argument String from,
            @Argument String to,
            @Argument Integer limit) {
        return queries.findCharacterHighscores(
                name,
                category,
                world,
                vocationFilterId,
                from == null ? null : LocalDate.parse(from),
                to == null ? null : LocalDate.parse(to),
                limit == null ? 100 : limit
        );
    }

    @QueryMapping
    public List<ApiQueryService.HighscoreView> highscores(
            @Argument String world,
            @Argument StatCategory category,
            @Argument Integer vocationFilterId,
            @Argument String date,
            @Argument Integer limit) {
        return queries.findHighscores(
                world,
                category,
                vocationFilterId,
                date == null ? null : LocalDate.parse(date),
                limit == null ? 100 : limit
        );
    }

    @QueryMapping
    public List<ApiQueryService.ScrapeJobView> scrapeJobs(
            @Argument String jobName,
            @Argument String status,
            @Argument Integer limit) {
        return queries.findScrapeJobs(jobName, status, limit == null ? 50 : limit);
    }

    /** Legacy query kept for compatibility with the previous schema. */
    @QueryMapping
    public List<ApiQueryService.HighscoreView> characterStatHistory(
            @Argument String name,
            @Argument StatCategory category) {
        return queries.findCharacterHighscores(name, category, null, null, null, null, 100);
    }
}
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/domain/model"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/domain/model/StatCategory.java" <<'__TIBIACHRONO_FILE__'
package com.nathan.tibiastats.domain.model;

public enum StatCategory {
    ACHIEVEMENTS,
    AXE_FIGHTING,
    BOSS_POINTS,
    BOUNTY_POINTS_EARNED,
    CHARM_POINTS,
    CLUB_FIGHTING,
    DISTANCE_FIGHTING,
    DROME_SCORE,
    EXPERIENCE,
    FISHING,
    FIST_FIGHTING,
    GOSHNARS_TAINT,
    LOYALTY_POINTS,
    MAGIC_LEVEL,
    SHIELDING,
    SWORD_FIGHTING,
    WEEKLY_TASKS_COMPLETED
}
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java" <<'__TIBIACHRONO_FILE__'
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
            case BOSS_POINTS -> 15;
            case BOUNTY_POINTS_EARNED -> 16;
            case CHARM_POINTS -> 3;
            case CLUB_FIGHTING -> 4;
            case DISTANCE_FIGHTING -> 5;
            case DROME_SCORE -> 14;
            case EXPERIENCE -> 6;
            case FISHING -> 7;
            case FIST_FIGHTING -> 8;
            case GOSHNARS_TAINT -> 9;
            case LOYALTY_POINTS -> 10;
            case MAGIC_LEVEL -> 11;
            case SHIELDING -> 12;
            case SWORD_FIGHTING -> 13;
            case WEEKLY_TASKS_COMPLETED -> 17;
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
__TIBIACHRONO_FILE__

mkdir -p "$ROOT/src/main/java/com/nathan/tibiastats/config"
cat > "$ROOT/src/main/java/com/nathan/tibiastats/config/AppProperties.java" <<'__TIBIACHRONO_FILE__'
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
        private boolean enabled = true;
        private String cron = "0 0 7 * * *";
        private String categories = "EXPERIENCE";
        private String vocations = "0";
        private int maxPages = 1;
        private long pageDelayMs = 1000L;
        private int worldLimit = 0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getCron(){return cron;}
        public void setCron(String c){this.cron=c;}

        public String getCategories() { return categories; }
        public void setCategories(String categories) { this.categories = categories; }

        public String getVocations() { return vocations; }
        public void setVocations(String vocations) { this.vocations = vocations; }

        public int getMaxPages() { return maxPages; }
        public void setMaxPages(int maxPages) { this.maxPages = maxPages; }

        public long getPageDelayMs() { return pageDelayMs; }
        public void setPageDelayMs(long pageDelayMs) { this.pageDelayMs = pageDelayMs; }

        public int getWorldLimit() { return worldLimit; }
        public void setWorldLimit(int worldLimit) { this.worldLimit = worldLimit; }
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

echo "Applied scrape_jobs + REST + GraphQL changes."
echo "Backup: $BACKUP_DIR"
