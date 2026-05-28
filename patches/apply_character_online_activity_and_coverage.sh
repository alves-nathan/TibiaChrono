#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

if [[ ! -f "pom.xml" || ! -d "src/main/java/com/nathan/tibiastats" ]]; then
  echo "ERROR: execute este script na raiz do projeto TibiaChrono." >&2
  exit 1
fi

if [[ -f "src/main/java/com/nathan/tibiastats/application/service/CharacterOnlineActivityService.java" ]] \
   && grep -q "findCharacterOnlineHistory" "src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java" \
   && grep -q "test-coverage" "Makefile" \
   && grep -q "jacoco-maven-plugin" "pom.xml"; then
  echo "Character online activity API e comando de cobertura já parecem aplicados. Nada a fazer."
  exit 0
fi

BACKUP_DIR=".tibiachrono-character-online-activity-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"
backup_file() {
  local file="$1"
  if [[ -f "$file" ]]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$file" "$BACKUP_DIR/$file"
  fi
}

backup_file "Makefile"
backup_file "pom.xml"
backup_file "src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java"
backup_file "src/main/java/com/nathan/tibiastats/application/service/CharacterOnlineActivityService.java"
backup_file "src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java"
backup_file "src/test/java/com/nathan/tibiastats/api/CharacterOnlineActivityIntegrationTest.java"

tmp_patch="$(mktemp)"
trap 'rm -f "$tmp_patch"' EXIT
cat > "$tmp_patch" <<'PATCH'
--- a/Makefile
+++ b/Makefile
@@ -26,6 +26,7 @@
 	@echo "  make down-dev-clean - stop dev compose and remove volumes"
 	@echo "  make logs-dev      - tail dev app logs"
 	@echo "  make test          - run full test suite in isolated copied Maven workspace"
+	@echo "  make test-coverage - run tests and open JaCoCo HTML coverage report"
 	@echo "  make test-host     - run full test suite with host Maven"
 	@echo "  make test-dev      - run Maven tests INSIDE dev container"
 	@echo "  make test-down     - stop test database container/network"
@@ -85,6 +86,22 @@
 	./run-tests.sh
 
 
+
+.PHONY: test-coverage
+test-coverage:
+	MAVEN_ARGS="-U clean jacoco:prepare-agent test jacoco:report" ./run-tests.sh
+	@report="$(PWD)/.test-maven/workspace/target/site/jacoco/index.html"; \
+	echo "JaCoCo coverage report: $$report"; \
+	if command -v wslview >/dev/null 2>&1; then \
+	  wslview "$$report"; \
+	elif command -v xdg-open >/dev/null 2>&1; then \
+	  xdg-open "$$report" >/dev/null 2>&1 || true; \
+	elif command -v explorer.exe >/dev/null 2>&1 && command -v wslpath >/dev/null 2>&1; then \
+	  explorer.exe "$$(wslpath -w "$$report")"; \
+	else \
+	  echo "Open the file above in your browser."; \
+	fi
+
 .PHONY: test-host
 test-host:
 	./run-tests.sh host
--- a/pom.xml
+++ b/pom.xml
@@ -15,6 +15,7 @@
         <java.version>21</java.version>
         <spring-boot.version>3.3.4</spring-boot.version>
         <testcontainers.version>2.0.5</testcontainers.version>
+        <jacoco.version>0.8.12</jacoco.version>
         <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
         <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
     </properties>
@@ -184,6 +185,13 @@
                 </configuration>
             </plugin>
 
+
+            <plugin>
+                <groupId>org.jacoco</groupId>
+                <artifactId>jacoco-maven-plugin</artifactId>
+                <version>${jacoco.version}</version>
+            </plugin>
+
             <plugin>
                 <groupId>org.apache.maven.plugins</groupId>
                 <artifactId>maven-surefire-plugin</artifactId>
--- a/src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java
+++ b/src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java
@@ -233,6 +233,202 @@
         return jdbc.query(sql.toString(), params, this::mapScrapeJob);
     }
 
+
+    public List<CharacterOnlinePointView> findCharacterOnlineHistory(String characterName,
+                                                                     String world,
+                                                                     Instant from,
+                                                                     Instant to,
+                                                                     int limit) {
+        var sql = new StringBuilder("""
+                with resolved as (
+                    select lookup.character_id
+                    from character_names lookup
+                    where lower(lookup.name) = lower(:characterName)
+                      and (
+                          lookup.active is true
+                          or lookup.inactive_date >= now() - interval '6 months'
+                      )
+                    order by lookup.active desc, lookup.inactive_date desc nulls last
+                    limit 1
+                )
+                select
+                    r.character_id,
+                    active_name.name as character_name,
+                    s.id as scrape_id,
+                    w.name as world,
+                    s.scrape_time,
+                    s.players_online
+                from resolved r
+                join scrape_players sp on sp.character_id = r.character_id
+                join scrapes s on s.id = sp.scrape_id
+                join worlds w on w.id = s.world_id
+                left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
+                where 1 = 1
+                """);
+        var params = new MapSqlParameterSource("characterName", characterName)
+                .addValue("limit", safeLimit(limit));
+        appendCharacterOnlineFilters(sql, params, world, from, to);
+        sql.append(" order by s.scrape_time asc, w.name asc, s.id asc limit :limit");
+        return jdbc.query(sql.toString(), params, this::mapCharacterOnlinePoint);
+    }
+
+    public List<CharacterOnlineSessionView> findCharacterOnlineSessions(String characterName,
+                                                                        String world,
+                                                                        Instant from,
+                                                                        Instant to,
+                                                                        int maxGapMinutes,
+                                                                        int limit) {
+        var sql = new StringBuilder("""
+                with resolved as (
+                    select lookup.character_id
+                    from character_names lookup
+                    where lower(lookup.name) = lower(:characterName)
+                      and (
+                          lookup.active is true
+                          or lookup.inactive_date >= now() - interval '6 months'
+                      )
+                    order by lookup.active desc, lookup.inactive_date desc nulls last
+                    limit 1
+                ), raw as (
+                    select
+                        r.character_id,
+                        active_name.name as character_name,
+                        w.name as world,
+                        s.scrape_time,
+                        lag(s.scrape_time) over (partition by w.id order by s.scrape_time, s.id) as previous_seen_at
+                    from resolved r
+                    join scrape_players sp on sp.character_id = r.character_id
+                    join scrapes s on s.id = sp.scrape_id
+                    join worlds w on w.id = s.world_id
+                    left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
+                    where 1 = 1
+                """);
+        var params = new MapSqlParameterSource("characterName", characterName)
+                .addValue("limit", safeLimit(limit))
+                .addValue("maxGapMinutes", maxGapMinutes);
+        appendCharacterOnlineFilters(sql, params, world, from, to);
+        sql.append("""
+                ), grouped as (
+                    select
+                        raw.*,
+                        sum(
+                            case
+                                when previous_seen_at is null
+                                  or scrape_time - previous_seen_at > (:maxGapMinutes * interval '1 minute')
+                                then 1
+                                else 0
+                            end
+                        ) over (partition by world order by scrape_time rows unbounded preceding) as session_no
+                    from raw
+                )
+                select
+                    character_id,
+                    character_name,
+                    world,
+                    min(scrape_time) as started_at,
+                    max(scrape_time) as ended_at,
+                    cast(floor(extract(epoch from (max(scrape_time) - min(scrape_time))) / 60) as bigint) as observed_minutes,
+                    cast(count(*) as int) as samples
+                from grouped
+                group by character_id, character_name, world, session_no
+                order by ended_at desc, world asc
+                limit :limit
+                """);
+        return jdbc.query(sql.toString(), params, this::mapCharacterOnlineSession);
+    }
+
+    public List<CharacterOnlineWorldSummaryView> findCharacterOnlineWorldSummaries(String characterName,
+                                                                                  String world,
+                                                                                  Instant from,
+                                                                                  Instant to,
+                                                                                  int maxGapMinutes) {
+        var sql = new StringBuilder("""
+                with resolved as (
+                    select lookup.character_id
+                    from character_names lookup
+                    where lower(lookup.name) = lower(:characterName)
+                      and (
+                          lookup.active is true
+                          or lookup.inactive_date >= now() - interval '6 months'
+                      )
+                    order by lookup.active desc, lookup.inactive_date desc nulls last
+                    limit 1
+                ), raw as (
+                    select
+                        r.character_id,
+                        active_name.name as character_name,
+                        w.name as world,
+                        w.id as world_id,
+                        s.scrape_time,
+                        lag(s.scrape_time) over (partition by w.id order by s.scrape_time, s.id) as previous_seen_at
+                    from resolved r
+                    join scrape_players sp on sp.character_id = r.character_id
+                    join scrapes s on s.id = sp.scrape_id
+                    join worlds w on w.id = s.world_id
+                    left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
+                    where 1 = 1
+                """);
+        var params = new MapSqlParameterSource("characterName", characterName)
+                .addValue("maxGapMinutes", maxGapMinutes);
+        appendCharacterOnlineFilters(sql, params, world, from, to);
+        sql.append("""
+                ), grouped as (
+                    select
+                        raw.*,
+                        sum(
+                            case
+                                when previous_seen_at is null
+                                  or scrape_time - previous_seen_at > (:maxGapMinutes * interval '1 minute')
+                                then 1
+                                else 0
+                            end
+                        ) over (partition by world_id order by scrape_time rows unbounded preceding) as session_no
+                    from raw
+                ), sessions as (
+                    select
+                        character_id,
+                        character_name,
+                        world,
+                        session_no,
+                        min(scrape_time) as started_at,
+                        max(scrape_time) as ended_at,
+                        cast(floor(extract(epoch from (max(scrape_time) - min(scrape_time))) / 60) as bigint) as observed_minutes
+                    from grouped
+                    group by character_id, character_name, world, session_no
+                ), session_summary as (
+                    select
+                        world,
+                        cast(count(*) as int) as sessions,
+                        cast(coalesce(sum(observed_minutes), 0) as bigint) as observed_minutes
+                    from sessions
+                    group by world
+                ), point_summary as (
+                    select
+                        character_id,
+                        character_name,
+                        world,
+                        cast(count(*) as int) as appearances,
+                        min(scrape_time) as first_seen_at,
+                        max(scrape_time) as last_seen_at
+                    from raw
+                    group by character_id, character_name, world
+                )
+                select
+                    ps.character_id,
+                    ps.character_name,
+                    ps.world,
+                    ps.appearances,
+                    coalesce(ss.sessions, 0) as sessions,
+                    cast(coalesce(ss.observed_minutes, 0) as bigint) as observed_minutes,
+                    ps.first_seen_at,
+                    ps.last_seen_at
+                from point_summary ps
+                left join session_summary ss on ss.world = ps.world
+                order by ps.world asc
+                """);
+        return jdbc.query(sql.toString(), params, this::mapCharacterOnlineWorldSummary);
+    }
+
     private void appendHighscoreFilters(StringBuilder sql,
                                         MapSqlParameterSource params,
                                         StatCategory category,
@@ -267,6 +463,26 @@
         }
     }
 
+
+    private void appendCharacterOnlineFilters(StringBuilder sql,
+                                              MapSqlParameterSource params,
+                                              String world,
+                                              Instant from,
+                                              Instant to) {
+        if (world != null && !world.isBlank()) {
+            sql.append(" and lower(w.name) = lower(:onlineWorld)");
+            params.addValue("onlineWorld", world.trim());
+        }
+        if (from != null) {
+            sql.append(" and s.scrape_time >= :onlineFrom");
+            params.addValue("onlineFrom", from);
+        }
+        if (to != null) {
+            sql.append(" and s.scrape_time <= :onlineTo");
+            params.addValue("onlineTo", to);
+        }
+    }
+
     private int safeLimit(int requested) {
         if (requested <= 0) {
             return 100;
@@ -336,6 +552,43 @@
         );
     }
 
+
+    private CharacterOnlinePointView mapCharacterOnlinePoint(ResultSet rs, int rowNum) throws SQLException {
+        return new CharacterOnlinePointView(
+                rs.getLong("character_id"),
+                rs.getString("character_name"),
+                rs.getLong("scrape_id"),
+                rs.getString("world"),
+                toInstant(rs.getTimestamp("scrape_time")),
+                getNullableInteger(rs, "players_online")
+        );
+    }
+
+    private CharacterOnlineSessionView mapCharacterOnlineSession(ResultSet rs, int rowNum) throws SQLException {
+        return new CharacterOnlineSessionView(
+                rs.getLong("character_id"),
+                rs.getString("character_name"),
+                rs.getString("world"),
+                toInstant(rs.getTimestamp("started_at")),
+                toInstant(rs.getTimestamp("ended_at")),
+                getNullableLong(rs, "observed_minutes"),
+                getNullableInteger(rs, "samples")
+        );
+    }
+
+    private CharacterOnlineWorldSummaryView mapCharacterOnlineWorldSummary(ResultSet rs, int rowNum) throws SQLException {
+        return new CharacterOnlineWorldSummaryView(
+                rs.getLong("character_id"),
+                rs.getString("character_name"),
+                rs.getString("world"),
+                getNullableInteger(rs, "appearances"),
+                getNullableInteger(rs, "sessions"),
+                getNullableLong(rs, "observed_minutes"),
+                toInstant(rs.getTimestamp("first_seen_at")),
+                toInstant(rs.getTimestamp("last_seen_at"))
+        );
+    }
+
     private ScrapeJobView mapScrapeJob(ResultSet rs, int rowNum) throws SQLException {
         return new ScrapeJobView(
                 rs.getLong("id"),
@@ -420,6 +673,37 @@
         }
     }
 
+
+    public record CharacterOnlinePointView(
+            Long characterId,
+            String characterName,
+            Long scrapeId,
+            String world,
+            Instant timestamp,
+            Integer playersOnline
+    ) {}
+
+    public record CharacterOnlineSessionView(
+            Long characterId,
+            String characterName,
+            String world,
+            Instant startedAt,
+            Instant endedAt,
+            Long observedMinutes,
+            Integer samples
+    ) {}
+
+    public record CharacterOnlineWorldSummaryView(
+            Long characterId,
+            String characterName,
+            String world,
+            Integer appearances,
+            Integer sessions,
+            Long observedMinutes,
+            Instant firstSeenAt,
+            Instant lastSeenAt
+    ) {}
+
     public record ScrapeJobView(
             Long id,
             String jobName,
--- a/src/main/java/com/nathan/tibiastats/application/service/CharacterOnlineActivityService.java
+++ b/src/main/java/com/nathan/tibiastats/application/service/CharacterOnlineActivityService.java
@@ -0,0 +1,138 @@
+package com.nathan.tibiastats.application.service;
+
+import org.springframework.stereotype.Service;
+
+import java.time.Instant;
+import java.util.List;
+
+@Service
+public class CharacterOnlineActivityService {
+    private static final int DEFAULT_HISTORY_LIMIT = 1000;
+    private static final int DEFAULT_SESSION_LIMIT = 100;
+    private static final int DEFAULT_MAX_GAP_MINUTES = 15;
+
+    private final ApiQueryService queries;
+
+    public CharacterOnlineActivityService(ApiQueryService queries) {
+        this.queries = queries;
+    }
+
+    public List<ApiQueryService.CharacterOnlinePointView> history(String characterName,
+                                                                  String world,
+                                                                  Instant from,
+                                                                  Instant to,
+                                                                  Integer limit) {
+        return queries.findCharacterOnlineHistory(
+                characterName,
+                world,
+                defaultFrom(from),
+                defaultTo(to),
+                limitOrDefault(limit, DEFAULT_HISTORY_LIMIT)
+        );
+    }
+
+    public List<ApiQueryService.CharacterOnlineSessionView> sessions(String characterName,
+                                                                     String world,
+                                                                     Instant from,
+                                                                     Instant to,
+                                                                     Integer maxGapMinutes,
+                                                                     Integer limit) {
+        return queries.findCharacterOnlineSessions(
+                characterName,
+                world,
+                defaultFrom(from),
+                defaultTo(to),
+                normalizeMaxGapMinutes(maxGapMinutes),
+                limitOrDefault(limit, DEFAULT_SESSION_LIMIT)
+        );
+    }
+
+    public CharacterOnlineActivitySummary summary(String characterName,
+                                                  String world,
+                                                  Instant from,
+                                                  Instant to,
+                                                  Integer maxGapMinutes) {
+        ApiQueryService.CharacterView character = queries.findCharacter(characterName).orElseThrow();
+        Instant effectiveFrom = defaultFrom(from);
+        Instant effectiveTo = defaultTo(to);
+        int effectiveMaxGapMinutes = normalizeMaxGapMinutes(maxGapMinutes);
+
+        List<ApiQueryService.CharacterOnlineWorldSummaryView> worlds = queries.findCharacterOnlineWorldSummaries(
+                characterName,
+                world,
+                effectiveFrom,
+                effectiveTo,
+                effectiveMaxGapMinutes
+        );
+
+        int appearances = worlds.stream().mapToInt(ApiQueryService.CharacterOnlineWorldSummaryView::appearances).sum();
+        int sessions = worlds.stream().mapToInt(ApiQueryService.CharacterOnlineWorldSummaryView::sessions).sum();
+        long observedMinutes = worlds.stream().mapToLong(ApiQueryService.CharacterOnlineWorldSummaryView::observedMinutes).sum();
+        Instant firstSeenAt = worlds.stream()
+                .map(ApiQueryService.CharacterOnlineWorldSummaryView::firstSeenAt)
+                .filter(value -> value != null)
+                .min(Instant::compareTo)
+                .orElse(null);
+        Instant lastSeenAt = worlds.stream()
+                .map(ApiQueryService.CharacterOnlineWorldSummaryView::lastSeenAt)
+                .filter(value -> value != null)
+                .max(Instant::compareTo)
+                .orElse(null);
+
+        return new CharacterOnlineActivitySummary(
+                character.id(),
+                character.activeName(),
+                normalizeBlank(world),
+                effectiveFrom,
+                effectiveTo,
+                effectiveMaxGapMinutes,
+                appearances,
+                sessions,
+                observedMinutes,
+                firstSeenAt,
+                lastSeenAt,
+                worlds
+        );
+    }
+
+    public Instant defaultFrom(Instant from) {
+        return from == null ? Instant.now().minusSeconds(24 * 60 * 60) : from;
+    }
+
+    public Instant defaultTo(Instant to) {
+        return to == null ? Instant.now() : to;
+    }
+
+    public int normalizeMaxGapMinutes(Integer maxGapMinutes) {
+        if (maxGapMinutes == null || maxGapMinutes <= 0) {
+            return DEFAULT_MAX_GAP_MINUTES;
+        }
+        return Math.min(maxGapMinutes, 24 * 60);
+    }
+
+    private int limitOrDefault(Integer limit, int defaultValue) {
+        if (limit == null || limit <= 0) {
+            return defaultValue;
+        }
+        return limit;
+    }
+
+    private String normalizeBlank(String value) {
+        return value == null || value.isBlank() ? null : value.trim();
+    }
+
+    public record CharacterOnlineActivitySummary(
+            Long characterId,
+            String characterName,
+            String world,
+            Instant from,
+            Instant to,
+            Integer maxGapMinutes,
+            Integer appearances,
+            Integer sessions,
+            Long observedMinutes,
+            Instant firstSeenAt,
+            Instant lastSeenAt,
+            List<ApiQueryService.CharacterOnlineWorldSummaryView> worlds
+    ) {}
+}
--- a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java
+++ b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java
@@ -1,6 +1,7 @@
 package com.nathan.tibiastats.infrastructure.adapter.web.rest;
 
 import com.nathan.tibiastats.application.service.ApiQueryService;
+import com.nathan.tibiastats.application.service.CharacterOnlineActivityService;
 import com.nathan.tibiastats.application.service.HighscoreApiQueryService;
 import com.nathan.tibiastats.domain.model.StatCategory;
 import org.springframework.format.annotation.DateTimeFormat;
@@ -8,6 +9,7 @@
 import org.springframework.web.bind.annotation.*;
 import org.springframework.web.server.ResponseStatusException;
 
+import java.time.Instant;
 import java.time.LocalDate;
 import java.util.List;
 
@@ -16,10 +18,14 @@
 public class CharacterController {
     private final ApiQueryService queries;
     private final HighscoreApiQueryService highscores;
+    private final CharacterOnlineActivityService onlineActivity;
 
-    public CharacterController(ApiQueryService queries, HighscoreApiQueryService highscores) {
+    public CharacterController(ApiQueryService queries,
+                               HighscoreApiQueryService highscores,
+                               CharacterOnlineActivityService onlineActivity) {
         this.queries = queries;
         this.highscores = highscores;
+        this.onlineActivity = onlineActivity;
     }
 
     @GetMapping("/{name}")
@@ -32,6 +38,58 @@
         return queries.findCharacterNames(name);
     }
 
+
+    @GetMapping("/{name}/online-history")
+    public List<ApiQueryService.CharacterOnlinePointView> getCharacterOnlineHistory(
+            @PathVariable String name,
+            @RequestParam(required = false) String world,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
+            @RequestParam(required = false) Integer limit
+    ) {
+        requireCharacter(name);
+        validateRange(from, to);
+        return onlineActivity.history(name, world, from, to, limit);
+    }
+
+    @GetMapping("/{name}/online-sessions")
+    public List<ApiQueryService.CharacterOnlineSessionView> getCharacterOnlineSessions(
+            @PathVariable String name,
+            @RequestParam(required = false) String world,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
+            @RequestParam(required = false) Integer maxGapMinutes,
+            @RequestParam(required = false) Integer limit
+    ) {
+        requireCharacter(name);
+        validateRange(from, to);
+        return onlineActivity.sessions(name, world, from, to, maxGapMinutes, limit);
+    }
+
+    @GetMapping("/{name}/activity-summary")
+    public CharacterOnlineActivityService.CharacterOnlineActivitySummary getCharacterActivitySummary(
+            @PathVariable String name,
+            @RequestParam(required = false) String world,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
+            @RequestParam(required = false) Integer maxGapMinutes
+    ) {
+        requireCharacter(name);
+        validateRange(from, to);
+        return onlineActivity.summary(name, world, from, to, maxGapMinutes);
+    }
+
+    private ApiQueryService.CharacterView requireCharacter(String name) {
+        return queries.findCharacter(name)
+                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found: " + name));
+    }
+
+    private void validateRange(Instant from, Instant to) {
+        if (from != null && to != null && from.isAfter(to)) {
+            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before or equal to to");
+        }
+    }
+
     @GetMapping("/{name}/highscores")
     public List<?> getCharacterHighscores(
             @PathVariable String name,
--- a/src/test/java/com/nathan/tibiastats/api/CharacterOnlineActivityIntegrationTest.java
+++ b/src/test/java/com/nathan/tibiastats/api/CharacterOnlineActivityIntegrationTest.java
@@ -0,0 +1,116 @@
+package com.nathan.tibiastats.api;
+
+import com.jayway.jsonpath.JsonPath;
+import com.nathan.tibiastats.AbstractPostgresTest;
+import com.nathan.tibiastats.application.service.CharacterNamingService;
+import com.nathan.tibiastats.domain.model.Scrape;
+import com.nathan.tibiastats.domain.model.ScrapePlayer;
+import com.nathan.tibiastats.domain.model.World;
+import com.nathan.tibiastats.infrastructure.persistence.SpringWorldRepository;
+import org.junit.jupiter.api.BeforeEach;
+import org.junit.jupiter.api.Test;
+import org.springframework.beans.factory.annotation.Autowired;
+import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
+import org.springframework.boot.test.context.SpringBootTest;
+import org.springframework.http.MediaType;
+import org.springframework.test.web.servlet.MockMvc;
+
+import java.time.Instant;
+
+import static org.hamcrest.Matchers.*;
+import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
+import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
+import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
+import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
+
+@SpringBootTest
+@AutoConfigureMockMvc
+class CharacterOnlineActivityIntegrationTest extends AbstractPostgresTest {
+    @Autowired MockMvc mvc;
+    @Autowired SpringWorldRepository worlds;
+    @Autowired CharacterNamingService naming;
+
+    String token;
+    Instant base;
+
+    @BeforeEach
+    void setup() throws Exception {
+        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
+                .content("{\"username\":\"online-tester\",\"password\":\"secret\"}"));
+        var login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
+                        .content("{\"username\":\"online-tester\",\"password\":\"secret\"}"))
+                .andReturn();
+        token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
+
+        var world = worlds.save(new World("Antica", "Open PvP", "Europe"));
+        var character = naming.ensureCharacterForName("Knight Sample", "Knight Sample");
+        base = Instant.parse("2026-05-28T10:00:00Z");
+
+        saveOnlinePoint(world, character, base, 120);
+        saveOnlinePoint(world, character, base.plusSeconds(60), 121);
+        saveOnlinePoint(world, character, base.plusSeconds(25 * 60), 130);
+    }
+
+    @Test
+    void character_online_history_returns_scrape_points() throws Exception {
+        mvc.perform(get("/api/characters/Knight%20Sample/online-history")
+                        .header("Authorization", "Bearer " + token)
+                        .param("from", base.minusSeconds(60).toString())
+                        .param("to", base.plusSeconds(30 * 60).toString()))
+                .andExpect(status().isOk())
+                .andExpect(jsonPath("$", hasSize(3)))
+                .andExpect(jsonPath("$[0].characterName", is("Knight Sample")))
+                .andExpect(jsonPath("$[0].world", is("Antica")))
+                .andExpect(jsonPath("$[0].playersOnline", is(120)));
+    }
+
+    @Test
+    void character_online_sessions_group_points_by_gap() throws Exception {
+        mvc.perform(get("/api/characters/Knight%20Sample/online-sessions")
+                        .header("Authorization", "Bearer " + token)
+                        .param("from", base.minusSeconds(60).toString())
+                        .param("to", base.plusSeconds(30 * 60).toString())
+                        .param("maxGapMinutes", "10"))
+                .andExpect(status().isOk())
+                .andExpect(jsonPath("$", hasSize(2)))
+                .andExpect(jsonPath("$[0].world", is("Antica")))
+                .andExpect(jsonPath("$[0].samples", is(1)))
+                .andExpect(jsonPath("$[1].samples", is(2)))
+                .andExpect(jsonPath("$[1].observedMinutes", is(1)));
+    }
+
+    @Test
+    void character_activity_summary_aggregates_history_and_sessions() throws Exception {
+        mvc.perform(get("/api/characters/Knight%20Sample/activity-summary")
+                        .header("Authorization", "Bearer " + token)
+                        .param("from", base.minusSeconds(60).toString())
+                        .param("to", base.plusSeconds(30 * 60).toString())
+                        .param("maxGapMinutes", "10"))
+                .andExpect(status().isOk())
+                .andExpect(jsonPath("$.characterName", is("Knight Sample")))
+                .andExpect(jsonPath("$.appearances", is(3)))
+                .andExpect(jsonPath("$.sessions", is(2)))
+                .andExpect(jsonPath("$.observedMinutes", is(1)))
+                .andExpect(jsonPath("$.worlds[0].world", is("Antica")))
+                .andExpect(jsonPath("$.worlds[0].appearances", is(3)));
+    }
+
+    @Test
+    void missing_character_returns_not_found() throws Exception {
+        mvc.perform(get("/api/characters/Unknown/online-history")
+                        .header("Authorization", "Bearer " + token))
+                .andExpect(status().isNotFound());
+    }
+
+    private void saveOnlinePoint(World world,
+                                 com.nathan.tibiastats.domain.model.CharacterEntity character,
+                                 Instant timestamp,
+                                 int playersOnline) {
+        Scrape scrape = new Scrape();
+        scrape.setWorld(world);
+        scrape.setScrapeTime(timestamp);
+        scrape.setPlayersOnline(playersOnline);
+        scrape.addPlayer(new ScrapePlayer(null, character));
+        worlds.saveScrape(scrape);
+    }
+}
PATCH

if patch --dry-run -p1 < "$tmp_patch" >/dev/null; then
  patch -p1 < "$tmp_patch"
else
  echo "ERROR: patch não pôde ser aplicado limpo. Verifique se a base já divergiu ou se a feature foi aplicada parcialmente." >&2
  echo "Backup criado em: $BACKUP_DIR" >&2
  exit 1
fi

chmod +x "$0" 2>/dev/null || true

echo "Patch aplicado com sucesso."
echo "Novos endpoints:"
echo "  GET /api/characters/{name}/online-history"
echo "  GET /api/characters/{name}/online-sessions"
echo "  GET /api/characters/{name}/activity-summary"
echo "Cobertura de testes:"
echo "  make test-coverage"
echo "Validação sugerida:"
echo "  ./run-tests.sh"
