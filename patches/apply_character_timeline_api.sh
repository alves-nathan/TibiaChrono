#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

if [[ ! -f "pom.xml" || ! -d "src/main/java/com/nathan/tibiastats" ]]; then
  echo "ERROR: execute este script na raiz do projeto TibiaChrono." >&2
  exit 1
fi

if [[ -f "src/main/java/com/nathan/tibiastats/application/service/CharacterTimelineService.java" ]] \
   && grep -q "CharacterTimelineEvent" "src/main/java/com/nathan/tibiastats/application/service/CharacterTimelineService.java" \
   && grep -q 'GetMapping("/{name}/timeline")' "src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java" \
   && grep -q "CharacterTimelineIntegrationTest" "src/test/java/com/nathan/tibiastats/api/CharacterTimelineIntegrationTest.java" 2>/dev/null; then
  echo "Character timeline API já parece aplicada. Nada a fazer."
  exit 0
fi

BACKUP_DIR=".tibiachrono-character-timeline-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"

backup_file() {
  local file="$1"
  if [[ -f "$file" ]]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$file" "$BACKUP_DIR/$file"
  fi
}

backup_file "src/main/java/com/nathan/tibiastats/application/service/CharacterTimelineService.java"
backup_file "src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java"
backup_file "src/test/java/com/nathan/tibiastats/api/CharacterTimelineIntegrationTest.java"

tmp_patch="$(mktemp)"
trap 'rm -f "$tmp_patch"' EXIT
cat > "$tmp_patch" <<'PATCH'
--- a/src/main/java/com/nathan/tibiastats/application/service/CharacterTimelineService.java	1970-01-01 00:00:00.000000000 +0000
+++ b/src/main/java/com/nathan/tibiastats/application/service/CharacterTimelineService.java	2026-05-28 18:47:12.996991070 +0000
@@ -0,0 +1,657 @@
+package com.nathan.tibiastats.application.service;
+
+import com.nathan.tibiastats.domain.model.StatCategory;
+import org.springframework.jdbc.core.JdbcTemplate;
+import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
+import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
+import org.springframework.stereotype.Service;
+
+import java.sql.ResultSet;
+import java.sql.SQLException;
+import java.sql.Timestamp;
+import java.sql.Types;
+import java.time.Instant;
+import java.util.ArrayList;
+import java.util.Comparator;
+import java.util.LinkedHashMap;
+import java.util.List;
+import java.util.Map;
+
+@Service
+public class CharacterTimelineService {
+    private static final int DEFAULT_LIMIT = 200;
+    private static final int DEFAULT_MAX_GAP_MINUTES = 15;
+
+    private final NamedParameterJdbcTemplate jdbc;
+    private final ApiQueryService queries;
+
+    public CharacterTimelineService(JdbcTemplate jdbcTemplate, ApiQueryService queries) {
+        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
+        this.queries = queries;
+    }
+
+    public List<CharacterDeathView> deaths(String characterName, Instant from, Instant to, Integer limit) {
+        var params = baseParams(characterName, from, to, limit);
+        return jdbc.query("""
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
+                    d.id,
+                    r.character_id,
+                    active_name.name as character_name,
+                    d.death_date,
+                    d.killed_by
+                from resolved r
+                join character_deaths d on d.character_id = r.character_id
+                left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
+                where d.death_date is not null
+                  and (:fromInstant is null or d.death_date >= :fromInstant)
+                  and (:toInstant is null or d.death_date <= :toInstant)
+                order by d.death_date desc, d.id desc
+                limit :limit
+                """, prepareParams(params), this::mapDeath);
+    }
+
+    public List<CharacterWorldHistoryView> worldHistory(String characterName, Instant from, Instant to, Integer limit) {
+        var params = baseParams(characterName, from, to, limit);
+        return jdbc.query("""
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
+                    cw.id,
+                    r.character_id,
+                    active_name.name as character_name,
+                    w.name as world,
+                    cw.active,
+                    cw.inactive_date
+                from resolved r
+                join character_worlds cw on cw.character_id = r.character_id
+                join worlds w on w.id = cw.world_id
+                left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
+                where (:fromInstant is null or cw.inactive_date is null or cw.inactive_date >= :fromInstant)
+                  and (:toInstant is null or cw.inactive_date is null or cw.inactive_date <= :toInstant)
+                order by cw.active desc, cw.inactive_date desc nulls first, cw.id desc
+                limit :limit
+                """, prepareParams(params), this::mapWorldHistory);
+    }
+
+    public List<CharacterGuildHistoryView> guildHistory(String characterName, Instant from, Instant to, Integer limit) {
+        var params = baseParams(characterName, from, to, limit);
+        return jdbc.query("""
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
+                    gm.id as membership_id,
+                    r.character_id,
+                    active_name.name as character_name,
+                    g.id as guild_id,
+                    g.name as guild_name,
+                    w.name as world,
+                    gm.rank_name,
+                    gm.title,
+                    gm.vocation,
+                    gm.level,
+                    gm.joined_at,
+                    gm.first_seen_at,
+                    gm.last_seen_at,
+                    gm.left_at,
+                    gm.active
+                from resolved r
+                join guild_memberships gm on gm.character_id = r.character_id
+                join guilds g on g.id = gm.guild_id
+                join worlds w on w.id = g.world_id
+                left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
+                where (:fromInstant is null or coalesce(gm.left_at, gm.last_seen_at, gm.joined_at) >= :fromInstant)
+                  and (:toInstant is null or gm.joined_at <= :toInstant)
+                order by gm.active desc, gm.joined_at desc, gm.id desc
+                limit :limit
+                """, prepareParams(params), this::mapGuildHistory);
+    }
+
+    public List<CharacterTimelineEvent> timeline(String characterName,
+                                                 Instant from,
+                                                 Instant to,
+                                                 Integer limit,
+                                                 boolean includeOnlineSessions,
+                                                 boolean includeHighscores,
+                                                 Integer maxGapMinutes) {
+        int safeLimit = safeLimit(limit == null ? DEFAULT_LIMIT : limit);
+        List<CharacterTimelineEvent> events = new ArrayList<>();
+        events.addAll(coreTimeline(characterName, from, to, safeLimit));
+
+        if (includeHighscores) {
+            events.addAll(experienceTimeline(characterName, from, to, safeLimit));
+            events.addAll(nonExperienceHighscoreTimeline(characterName, from, to, safeLimit));
+        }
+
+        if (includeOnlineSessions) {
+            int gap = normalizeMaxGapMinutes(maxGapMinutes);
+            queries.findCharacterOnlineSessions(characterName, null, from, to, gap, safeLimit).stream()
+                    .map(session -> new CharacterTimelineEvent(
+                            session.characterId(),
+                            session.characterName(),
+                            "ONLINE_SESSION",
+                            session.startedAt(),
+                            session.world(),
+                            null,
+                            null,
+                            null,
+                            session.observedMinutes(),
+                            null,
+                            "Online session observed from scrape samples",
+                            metadata(
+                                    "endedAt", session.endedAt(),
+                                    "observedMinutes", session.observedMinutes(),
+                                    "samples", session.samples(),
+                                    "maxGapMinutes", gap
+                            )
+                    ))
+                    .filter(event -> event.occurredAt() != null)
+                    .forEach(events::add);
+        }
+
+        return events.stream()
+                .filter(event -> event.occurredAt() != null)
+                .filter(event -> from == null || !event.occurredAt().isBefore(from))
+                .filter(event -> to == null || !event.occurredAt().isAfter(to))
+                .sorted(Comparator.comparing(CharacterTimelineEvent::occurredAt).reversed()
+                        .thenComparing(CharacterTimelineEvent::eventType))
+                .limit(safeLimit)
+                .toList();
+    }
+
+    private List<CharacterTimelineEvent> coreTimeline(String characterName, Instant from, Instant to, int limit) {
+        var params = baseParams(characterName, from, to, limit);
+        return jdbc.query("""
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
+                ), active_name as (
+                    select cn.character_id, cn.name
+                    from character_names cn
+                    join resolved r on r.character_id = cn.character_id
+                    where cn.active is true
+                    limit 1
+                )
+                select *
+                from (
+                    select
+                        r.character_id,
+                        an.name as character_name,
+                        'CHARACTER_CREATED' as event_type,
+                        c.creation_date as occurred_at,
+                        cast(null as text) as world,
+                        cast(null as text) as guild_name,
+                        cast(null as text) as category,
+                        cast(null as integer) as rank,
+                        cast(null as bigint) as value,
+                        cast(null as text) as title,
+                        'Character creation date from Tibia character details' as description
+                    from resolved r
+                    join characters c on c.id = r.character_id
+                    left join active_name an on an.character_id = r.character_id
+                    where c.creation_date is not null
+
+                    union all
+
+                    select
+                        r.character_id,
+                        an.name as character_name,
+                        'LAST_LOGIN' as event_type,
+                        c.last_login as occurred_at,
+                        cast(null as text) as world,
+                        cast(null as text) as guild_name,
+                        cast(null as text) as category,
+                        cast(null as integer) as rank,
+                        cast(null as bigint) as value,
+                        cast(null as text) as title,
+                        'Last login date from Tibia character details' as description
+                    from resolved r
+                    join characters c on c.id = r.character_id
+                    left join active_name an on an.character_id = r.character_id
+                    where c.last_login is not null
+
+                    union all
+
+                    select
+                        r.character_id,
+                        an.name as character_name,
+                        'CHARACTER_DETAILS_SCRAPED' as event_type,
+                        c.details_last_scraped_at as occurred_at,
+                        cast(null as text) as world,
+                        cast(null as text) as guild_name,
+                        cast(null as text) as category,
+                        cast(null as integer) as rank,
+                        cast(null as bigint) as value,
+                        c.details_last_scrape_status as title,
+                        'Latest character details scrape status' as description
+                    from resolved r
+                    join characters c on c.id = r.character_id
+                    left join active_name an on an.character_id = r.character_id
+                    where c.details_last_scraped_at is not null
+
+                    union all
+
+                    select
+                        r.character_id,
+                        an.name as character_name,
+                        'NAME_INACTIVATED' as event_type,
+                        cn.inactive_date as occurred_at,
+                        cast(null as text) as world,
+                        cast(null as text) as guild_name,
+                        cast(null as text) as category,
+                        cast(null as integer) as rank,
+                        cast(null as bigint) as value,
+                        cn.name as title,
+                        'Character name became inactive' as description
+                    from resolved r
+                    join character_names cn on cn.character_id = r.character_id
+                    left join active_name an on an.character_id = r.character_id
+                    where cn.active is false
+                      and cn.inactive_date is not null
+
+                    union all
+
+                    select
+                        r.character_id,
+                        an.name as character_name,
+                        'DEATH' as event_type,
+                        d.death_date as occurred_at,
+                        cast(null as text) as world,
+                        cast(null as text) as guild_name,
+                        cast(null as text) as category,
+                        cast(null as integer) as rank,
+                        cast(null as bigint) as value,
+                        d.killed_by as title,
+                        'Character death recorded from Tibia character details' as description
+                    from resolved r
+                    join character_deaths d on d.character_id = r.character_id
+                    left join active_name an on an.character_id = r.character_id
+                    where d.death_date is not null
+
+                    union all
+
+                    select
+                        r.character_id,
+                        an.name as character_name,
+                        'WORLD_LEFT' as event_type,
+                        cw.inactive_date as occurred_at,
+                        w.name as world,
+                        cast(null as text) as guild_name,
+                        cast(null as text) as category,
+                        cast(null as integer) as rank,
+                        cast(null as bigint) as value,
+                        w.name as title,
+                        'Character world history transition observed' as description
+                    from resolved r
+                    join character_worlds cw on cw.character_id = r.character_id
+                    join worlds w on w.id = cw.world_id
+                    left join active_name an on an.character_id = r.character_id
+                    where cw.active is false
+                      and cw.inactive_date is not null
+
+                    union all
+
+                    select
+                        r.character_id,
+                        an.name as character_name,
+                        case e.event_type
+                            when 'JOINED' then 'GUILD_JOINED'
+                            when 'LEFT' then 'GUILD_LEFT'
+                            when 'TRANSFERRED' then 'GUILD_TRANSFERRED'
+                            else 'GUILD_EVENT'
+                        end as event_type,
+                        e.observed_at as occurred_at,
+                        coalesce(to_world.name, from_world.name) as world,
+                        coalesce(to_guild.name, from_guild.name) as guild_name,
+                        cast(null as text) as category,
+                        cast(null as integer) as rank,
+                        cast(null as bigint) as value,
+                        e.event_type as title,
+                        e.description
+                    from resolved r
+                    join guild_membership_events e on e.character_id = r.character_id
+                    left join guilds from_guild on from_guild.id = e.from_guild_id
+                    left join guilds to_guild on to_guild.id = e.to_guild_id
+                    left join worlds from_world on from_world.id = from_guild.world_id
+                    left join worlds to_world on to_world.id = to_guild.world_id
+                    left join active_name an on an.character_id = r.character_id
+                    where e.observed_at is not null
+                ) events
+                where (:fromInstant is null or events.occurred_at >= :fromInstant)
+                  and (:toInstant is null or events.occurred_at <= :toInstant)
+                order by events.occurred_at desc, events.event_type asc
+                limit :limit
+                """, prepareParams(params), this::mapTimelineEvent);
+    }
+
+    private List<CharacterTimelineEvent> experienceTimeline(String characterName, Instant from, Instant to, int limit) {
+        var params = baseParams(characterName, from, to, limit);
+        return jdbc.query("""
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
+                    'HIGHSCORE_EXPERIENCE' as event_type,
+                    (d.date::timestamp at time zone 'UTC') as occurred_at,
+                    w.name as world,
+                    cast(null as text) as guild_name,
+                    'EXPERIENCE' as category,
+                    rank_daily.rank,
+                    d.experience as value,
+                    cast(d.level as text) as title,
+                    'Daily experience highscore snapshot' as description
+                from resolved r
+                join highscore_exp_daily d on d.character_id = r.character_id
+                join worlds w on w.id = d.world_id
+                left join highscore_exp_rank_daily rank_daily
+                    on rank_daily.date = d.date
+                   and rank_daily.character_id = d.character_id
+                   and rank_daily.world_id = d.world_id
+                   and rank_daily.vocation_filter_id = 0
+                left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
+                where (:fromInstant is null or (d.date::timestamp at time zone 'UTC') >= :fromInstant)
+                  and (:toInstant is null or (d.date::timestamp at time zone 'UTC') <= :toInstant)
+                order by d.date desc, w.name asc
+                limit :limit
+                """, prepareParams(params), this::mapTimelineEvent);
+    }
+
+    private List<CharacterTimelineEvent> nonExperienceHighscoreTimeline(String characterName, Instant from, Instant to, int limit) {
+        var params = baseParams(characterName, from, to, limit);
+        return jdbc.query("""
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
+                    'HIGHSCORE_PERIOD_STARTED' as event_type,
+                    (p.valid_from::timestamp at time zone 'UTC') as occurred_at,
+                    w.name as world,
+                    cast(null as text) as guild_name,
+                    cast(p.category as text) as category,
+                    p.rank,
+                    p.value,
+                    cast(null as text) as title,
+                    'Non-experience highscore period started' as description
+                from resolved r
+                join highscore_record_periods p on p.character_id = r.character_id
+                join worlds w on w.id = p.world_id
+                left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
+                where (:fromInstant is null or (p.valid_from::timestamp at time zone 'UTC') >= :fromInstant)
+                  and (:toInstant is null or (p.valid_from::timestamp at time zone 'UTC') <= :toInstant)
+                order by p.valid_from desc, w.name asc, p.category asc
+                limit :limit
+                """, prepareParams(params), (rs, rowNum) -> {
+                    CharacterTimelineEvent event = mapTimelineEvent(rs, rowNum);
+                    return new CharacterTimelineEvent(
+                            event.characterId(),
+                            event.characterName(),
+                            event.eventType(),
+                            event.occurredAt(),
+                            event.world(),
+                            event.guildName(),
+                            categoryName(Integer.parseInt(event.category())),
+                            event.rank(),
+                            event.value(),
+                            event.title(),
+                            event.description(),
+                            event.metadata()
+                    );
+                });
+    }
+
+    private MapSqlParameterSource baseParams(String characterName, Instant from, Instant to, Integer limit) {
+        return new MapSqlParameterSource()
+                .addValue("characterName", normalizeRequired(characterName, "characterName"))
+                .addValue("fromInstant", from == null ? null : Timestamp.from(from), Types.TIMESTAMP)
+                .addValue("toInstant", to == null ? null : Timestamp.from(to), Types.TIMESTAMP)
+                .addValue("limit", safeLimit(limit == null ? DEFAULT_LIMIT : limit));
+    }
+
+    private MapSqlParameterSource prepareParams(MapSqlParameterSource params) {
+        if (params == null) {
+            return new MapSqlParameterSource();
+        }
+        for (var entry : new ArrayList<>(params.getValues().entrySet())) {
+            if (entry.getValue() instanceof Instant instant) {
+                params.addValue(entry.getKey(), Timestamp.from(instant), Types.TIMESTAMP);
+            }
+        }
+        return params;
+    }
+
+    private CharacterDeathView mapDeath(ResultSet rs, int rowNum) throws SQLException {
+        return new CharacterDeathView(
+                rs.getLong("id"),
+                rs.getLong("character_id"),
+                rs.getString("character_name"),
+                toInstant(rs.getTimestamp("death_date")),
+                rs.getString("killed_by")
+        );
+    }
+
+    private CharacterWorldHistoryView mapWorldHistory(ResultSet rs, int rowNum) throws SQLException {
+        return new CharacterWorldHistoryView(
+                rs.getLong("id"),
+                rs.getLong("character_id"),
+                rs.getString("character_name"),
+                rs.getString("world"),
+                getNullableBoolean(rs, "active"),
+                toInstant(rs.getTimestamp("inactive_date"))
+        );
+    }
+
+    private CharacterGuildHistoryView mapGuildHistory(ResultSet rs, int rowNum) throws SQLException {
+        return new CharacterGuildHistoryView(
+                rs.getLong("membership_id"),
+                rs.getLong("character_id"),
+                rs.getString("character_name"),
+                rs.getLong("guild_id"),
+                rs.getString("guild_name"),
+                rs.getString("world"),
+                rs.getString("rank_name"),
+                rs.getString("title"),
+                rs.getString("vocation"),
+                getNullableInteger(rs, "level"),
+                toInstant(rs.getTimestamp("joined_at")),
+                toInstant(rs.getTimestamp("first_seen_at")),
+                toInstant(rs.getTimestamp("last_seen_at")),
+                toInstant(rs.getTimestamp("left_at")),
+                getNullableBoolean(rs, "active")
+        );
+    }
+
+    private Boolean getNullableBoolean(ResultSet rs, String column) throws SQLException {
+        boolean value = rs.getBoolean(column);
+        return rs.wasNull() ? null : value;
+    }
+
+    private CharacterTimelineEvent mapTimelineEvent(ResultSet rs, int rowNum) throws SQLException {
+        return new CharacterTimelineEvent(
+                rs.getLong("character_id"),
+                rs.getString("character_name"),
+                rs.getString("event_type"),
+                toInstant(rs.getTimestamp("occurred_at")),
+                rs.getString("world"),
+                rs.getString("guild_name"),
+                rs.getString("category"),
+                getNullableInteger(rs, "rank"),
+                getNullableLong(rs, "value"),
+                rs.getString("title"),
+                rs.getString("description"),
+                Map.of()
+        );
+    }
+
+    private int safeLimit(int requested) {
+        if (requested <= 0) {
+            return DEFAULT_LIMIT;
+        }
+        return Math.min(requested, 1000);
+    }
+
+    private int normalizeMaxGapMinutes(Integer maxGapMinutes) {
+        if (maxGapMinutes == null || maxGapMinutes <= 0) {
+            return DEFAULT_MAX_GAP_MINUTES;
+        }
+        return Math.min(maxGapMinutes, 24 * 60);
+    }
+
+    private String normalizeRequired(String value, String name) {
+        if (value == null || value.isBlank()) {
+            throw new IllegalArgumentException(name + " is required");
+        }
+        return value.trim();
+    }
+
+    private Instant toInstant(Timestamp timestamp) {
+        return timestamp == null ? null : timestamp.toInstant();
+    }
+
+    private Integer getNullableInteger(ResultSet rs, String column) throws SQLException {
+        int value = rs.getInt(column);
+        return rs.wasNull() ? null : value;
+    }
+
+    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
+        long value = rs.getLong(column);
+        return rs.wasNull() ? null : value;
+    }
+
+    private static Map<String, Object> metadata(Object... keyValues) {
+        Map<String, Object> values = new LinkedHashMap<>();
+        for (int i = 0; i + 1 < keyValues.length; i += 2) {
+            if (keyValues[i] != null && keyValues[i + 1] != null) {
+                values.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
+            }
+        }
+        return values;
+    }
+
+    private String categoryName(int code) {
+        return switch (code) {
+            case 1 -> StatCategory.ACHIEVEMENTS.name();
+            case 2 -> StatCategory.AXE_FIGHTING.name();
+            case 3 -> StatCategory.CHARM_POINTS.name();
+            case 4 -> StatCategory.CLUB_FIGHTING.name();
+            case 5 -> StatCategory.DISTANCE_FIGHTING.name();
+            case 6 -> StatCategory.EXPERIENCE.name();
+            case 7 -> StatCategory.FISHING.name();
+            case 8 -> StatCategory.FIST_FIGHTING.name();
+            case 9 -> StatCategory.GOSHNARS_TAINT.name();
+            case 10 -> StatCategory.LOYALTY_POINTS.name();
+            case 11 -> StatCategory.MAGIC_LEVEL.name();
+            case 12 -> StatCategory.SHIELDING.name();
+            case 13 -> StatCategory.SWORD_FIGHTING.name();
+            case 14 -> StatCategory.DROME_SCORE.name();
+            case 15 -> StatCategory.BOSS_POINTS.name();
+            case 16 -> StatCategory.BOUNTY_POINTS_EARNED.name();
+            case 17 -> StatCategory.WEEKLY_TASKS_COMPLETED.name();
+            default -> "UNKNOWN";
+        };
+    }
+
+    public record CharacterDeathView(
+            Long id,
+            Long characterId,
+            String characterName,
+            Instant deathDate,
+            String killedBy
+    ) {}
+
+    public record CharacterWorldHistoryView(
+            Long id,
+            Long characterId,
+            String characterName,
+            String world,
+            Boolean active,
+            Instant inactiveDate
+    ) {}
+
+    public record CharacterGuildHistoryView(
+            Long membershipId,
+            Long characterId,
+            String characterName,
+            Long guildId,
+            String guildName,
+            String world,
+            String rankName,
+            String title,
+            String vocation,
+            Integer level,
+            Instant joinedAt,
+            Instant firstSeenAt,
+            Instant lastSeenAt,
+            Instant leftAt,
+            Boolean active
+    ) {}
+
+    public record CharacterTimelineEvent(
+            Long characterId,
+            String characterName,
+            String eventType,
+            Instant occurredAt,
+            String world,
+            String guildName,
+            String category,
+            Integer rank,
+            Long value,
+            String title,
+            String description,
+            Map<String, Object> metadata
+    ) {}
+}
--- a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java	2026-05-28 16:45:30.000000000 +0000
+++ b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java	2026-05-28 18:46:53.869020380 +0000
@@ -2,6 +2,7 @@
 
 import com.nathan.tibiastats.application.service.ApiQueryService;
 import com.nathan.tibiastats.application.service.CharacterOnlineActivityService;
+import com.nathan.tibiastats.application.service.CharacterTimelineService;
 import com.nathan.tibiastats.application.service.HighscoreApiQueryService;
 import com.nathan.tibiastats.domain.model.StatCategory;
 import org.springframework.format.annotation.DateTimeFormat;
@@ -19,13 +20,16 @@
     private final ApiQueryService queries;
     private final HighscoreApiQueryService highscores;
     private final CharacterOnlineActivityService onlineActivity;
+    private final CharacterTimelineService timeline;
 
     public CharacterController(ApiQueryService queries,
                                HighscoreApiQueryService highscores,
-                               CharacterOnlineActivityService onlineActivity) {
+                               CharacterOnlineActivityService onlineActivity,
+                               CharacterTimelineService timeline) {
         this.queries = queries;
         this.highscores = highscores;
         this.onlineActivity = onlineActivity;
+        this.timeline = timeline;
     }
 
     @GetMapping("/{name}")
@@ -39,6 +43,58 @@
     }
 
 
+
+    @GetMapping("/{name}/deaths")
+    public List<CharacterTimelineService.CharacterDeathView> getCharacterDeaths(
+            @PathVariable String name,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
+            @RequestParam(required = false) Integer limit
+    ) {
+        requireCharacter(name);
+        validateRange(from, to);
+        return timeline.deaths(name, from, to, limit);
+    }
+
+    @GetMapping("/{name}/world-history")
+    public List<CharacterTimelineService.CharacterWorldHistoryView> getCharacterWorldHistory(
+            @PathVariable String name,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
+            @RequestParam(required = false) Integer limit
+    ) {
+        requireCharacter(name);
+        validateRange(from, to);
+        return timeline.worldHistory(name, from, to, limit);
+    }
+
+    @GetMapping("/{name}/guild-history")
+    public List<CharacterTimelineService.CharacterGuildHistoryView> getCharacterGuildHistory(
+            @PathVariable String name,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
+            @RequestParam(required = false) Integer limit
+    ) {
+        requireCharacter(name);
+        validateRange(from, to);
+        return timeline.guildHistory(name, from, to, limit);
+    }
+
+    @GetMapping("/{name}/timeline")
+    public List<CharacterTimelineService.CharacterTimelineEvent> getCharacterTimeline(
+            @PathVariable String name,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
+            @RequestParam(required = false) Integer limit,
+            @RequestParam(defaultValue = "false") boolean includeOnlineSessions,
+            @RequestParam(defaultValue = "true") boolean includeHighscores,
+            @RequestParam(required = false) Integer maxGapMinutes
+    ) {
+        requireCharacter(name);
+        validateRange(from, to);
+        return timeline.timeline(name, from, to, limit, includeOnlineSessions, includeHighscores, maxGapMinutes);
+    }
+
     @GetMapping("/{name}/online-history")
     public List<ApiQueryService.CharacterOnlinePointView> getCharacterOnlineHistory(
             @PathVariable String name,
--- a/src/test/java/com/nathan/tibiastats/api/CharacterTimelineIntegrationTest.java	1970-01-01 00:00:00.000000000 +0000
+++ b/src/test/java/com/nathan/tibiastats/api/CharacterTimelineIntegrationTest.java	2026-05-28 18:47:46.444188979 +0000
@@ -0,0 +1,188 @@
+package com.nathan.tibiastats.api;
+
+import com.jayway.jsonpath.JsonPath;
+import com.nathan.tibiastats.AbstractPostgresTest;
+import com.nathan.tibiastats.application.service.CharacterNamingService;
+import com.nathan.tibiastats.domain.model.CharacterEntity;
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
+import java.sql.Timestamp;
+import java.time.Instant;
+import java.time.LocalDate;
+
+import static org.hamcrest.Matchers.hasItem;
+import static org.hamcrest.Matchers.hasSize;
+import static org.hamcrest.Matchers.is;
+import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
+import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
+import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
+import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
+
+@SpringBootTest
+@AutoConfigureMockMvc
+class CharacterTimelineIntegrationTest extends AbstractPostgresTest {
+    @Autowired MockMvc mvc;
+    @Autowired SpringWorldRepository worlds;
+    @Autowired CharacterNamingService naming;
+
+    String token;
+    Instant base;
+    CharacterEntity character;
+    World antica;
+
+    @BeforeEach
+    void setup() throws Exception {
+        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
+                .content("{\"username\":\"timeline-tester\",\"password\":\"secret\"}"));
+        var login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
+                        .content("{\"username\":\"timeline-tester\",\"password\":\"secret\"}"))
+                .andReturn();
+        token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
+
+        base = Instant.parse("2026-05-28T10:00:00Z");
+        antica = worlds.save(new World("Antica", "Open PvP", "Europe"));
+        character = naming.ensureCharacterForName("Timeline Hero", "Timeline Hero");
+
+        jdbc.update("""
+                update characters
+                   set creation_date = ?,
+                       last_login = ?,
+                       details_last_scraped_at = ?,
+                       details_last_scrape_status = 'SUCCESS'
+                 where id = ?
+                """,
+                Timestamp.from(base.minusSeconds(60 * 60)),
+                Timestamp.from(base.minusSeconds(30 * 60)),
+                Timestamp.from(base.minusSeconds(20 * 60)),
+                character.getId());
+
+        jdbc.update("insert into character_deaths(character_id, death_date, killed_by) values (?, ?, ?)",
+                character.getId(), Timestamp.from(base.plusSeconds(15 * 60)), "a dragon");
+
+        jdbc.update("insert into character_worlds(character_id, world_id, active, inactive_date) values (?, ?, false, ?)",
+                character.getId(), antica.getId(), Timestamp.from(base.plusSeconds(10 * 60)));
+
+        Long guildId = jdbc.queryForObject("""
+                insert into guilds(name, normalized_name, world_id, active)
+                values ('Timeline Guild', 'timeline guild', ?, true)
+                returning id
+                """, Long.class, antica.getId());
+
+        jdbc.update("""
+                insert into guild_memberships(
+                    guild_id, character_id, character_name_snapshot, rank_name, title, vocation, level,
+                    joined_at, first_seen_at, last_seen_at, left_at, active
+                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false)
+                """,
+                guildId, character.getId(), "Timeline Hero", "Leader", "Founder", "Elite Knight", 100,
+                Timestamp.from(base.plusSeconds(5 * 60)),
+                Timestamp.from(base.plusSeconds(5 * 60)),
+                Timestamp.from(base.plusSeconds(30 * 60)),
+                Timestamp.from(base.plusSeconds(30 * 60)));
+
+        jdbc.update("""
+                insert into guild_membership_events(
+                    character_id, character_name_snapshot, event_type, to_guild_id, observed_at, description
+                ) values (?, ?, 'JOINED', ?, ?, ?)
+                """,
+                character.getId(), "Timeline Hero", guildId,
+                Timestamp.from(base.plusSeconds(5 * 60)), "Timeline Hero joined Timeline Guild");
+
+        jdbc.update("""
+                insert into highscore_exp_daily(date, character_id, world_id, level, experience, vocation_id, first_seen_filter, scraped_at)
+                values (?, ?, ?, 100, 123456789, 4, 0, ?)
+                """, LocalDate.parse("2026-05-28"), character.getId(), antica.getId(), Timestamp.from(base.plusSeconds(40 * 60)));
+        jdbc.update("""
+                insert into highscore_exp_rank_daily(date, character_id, world_id, vocation_filter_id, rank, scraped_at)
+                values (?, ?, ?, 0, 50, ?)
+                """, LocalDate.parse("2026-05-28"), character.getId(), antica.getId(), Timestamp.from(base.plusSeconds(40 * 60)));
+
+        saveOnlinePoint(antica, character, base.plusSeconds(80 * 60), 120);
+        saveOnlinePoint(antica, character, base.plusSeconds(81 * 60), 121);
+    }
+
+    @Test
+    void deaths_endpoint_returns_character_deaths() throws Exception {
+        mvc.perform(get("/api/characters/{name}/deaths", "Timeline Hero")
+                        .header("Authorization", "Bearer " + token)
+                        .param("from", base.toString())
+                        .param("to", base.plusSeconds(60 * 60).toString()))
+                .andExpect(status().isOk())
+                .andExpect(jsonPath("$", hasSize(1)))
+                .andExpect(jsonPath("$[0].characterName", is("Timeline Hero")))
+                .andExpect(jsonPath("$[0].killedBy", is("a dragon")));
+    }
+
+    @Test
+    void world_and_guild_history_endpoints_return_character_history() throws Exception {
+        mvc.perform(get("/api/characters/{name}/world-history", "Timeline Hero")
+                        .header("Authorization", "Bearer " + token))
+                .andExpect(status().isOk())
+                .andExpect(jsonPath("$", hasSize(1)))
+                .andExpect(jsonPath("$[0].world", is("Antica")))
+                .andExpect(jsonPath("$[0].active", is(false)));
+
+        mvc.perform(get("/api/characters/{name}/guild-history", "Timeline Hero")
+                        .header("Authorization", "Bearer " + token))
+                .andExpect(status().isOk())
+                .andExpect(jsonPath("$", hasSize(1)))
+                .andExpect(jsonPath("$[0].guildName", is("Timeline Guild")))
+                .andExpect(jsonPath("$[0].rankName", is("Leader")))
+                .andExpect(jsonPath("$[0].active", is(false)));
+    }
+
+    @Test
+    void timeline_returns_core_highscore_and_online_events() throws Exception {
+        mvc.perform(get("/api/characters/{name}/timeline", "Timeline Hero")
+                        .header("Authorization", "Bearer " + token)
+                        .param("from", base.minusSeconds(24 * 60 * 60).toString())
+                        .param("to", base.plusSeconds(2 * 60 * 60).toString())
+                        .param("includeOnlineSessions", "true")
+                        .param("includeHighscores", "true")
+                        .param("maxGapMinutes", "10"))
+                .andExpect(status().isOk())
+                .andExpect(jsonPath("$[*].eventType", hasItem("CHARACTER_CREATED")))
+                .andExpect(jsonPath("$[*].eventType", hasItem("GUILD_JOINED")))
+                .andExpect(jsonPath("$[*].eventType", hasItem("WORLD_LEFT")))
+                .andExpect(jsonPath("$[*].eventType", hasItem("DEATH")))
+                .andExpect(jsonPath("$[*].eventType", hasItem("HIGHSCORE_EXPERIENCE")))
+                .andExpect(jsonPath("$[*].eventType", hasItem("ONLINE_SESSION")))
+                .andExpect(jsonPath("$[0].eventType", is("ONLINE_SESSION")))
+                .andExpect(jsonPath("$[0].metadata.samples", is(2)));
+    }
+
+    @Test
+    void timeline_invalid_range_returns_bad_request() throws Exception {
+        mvc.perform(get("/api/characters/{name}/timeline", "Timeline Hero")
+                        .header("Authorization", "Bearer " + token)
+                        .param("from", base.plusSeconds(60).toString())
+                        .param("to", base.toString()))
+                .andExpect(status().isBadRequest());
+    }
+
+    @Test
+    void timeline_missing_character_returns_not_found() throws Exception {
+        mvc.perform(get("/api/characters/{name}/timeline", "Unknown")
+                        .header("Authorization", "Bearer " + token))
+                .andExpect(status().isNotFound());
+    }
+
+    private void saveOnlinePoint(World world, CharacterEntity character, Instant timestamp, int playersOnline) {
+        Scrape scrape = new Scrape();
+        scrape.setWorld(world);
+        scrape.setScrapeTime(timestamp);
+        scrape.setPlayersOnline(playersOnline);
+        scrape.addPlayer(new ScrapePlayer(null, character));
+        worlds.saveScrape(scrape);
+    }
+}
PATCH

patch -p1 < "$tmp_patch"

cat <<'MSG'
Character timeline API aplicada com sucesso.

Novos endpoints:
  GET /api/characters/{name}/timeline
  GET /api/characters/{name}/deaths
  GET /api/characters/{name}/world-history
  GET /api/characters/{name}/guild-history

Sugestão de validação:
  ./run-tests.sh
  make test-coverage
MSG
