#!/usr/bin/env bash
set -euo pipefail

if [ ! -f "pom.xml" ] || [ ! -d "src/main/java/com/nathan/tibiastats" ]; then
  echo "ERROR: run this patch from the TibiaChrono project root." >&2
  exit 1
fi

TARGET="src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java"
BACKUP_DIR="patches/.backups/api-query-service-compatibility-$(date +%Y%m%d%H%M%S)"

if [ -f "$TARGET" ] \
  && grep -q "package com.nathan.tibiastats.application.service;" "$TARGET" \
  && grep -q "class ApiQueryService" "$TARGET"; then
  echo "ApiQueryService already exists in the expected package at $TARGET. Nothing to restore."
  echo "If tests still fail, attach the new testresult.txt so the remaining references can be adjusted."
  exit 0
fi

mkdir -p "$(dirname "$TARGET")" "$BACKUP_DIR"
if [ -f "$TARGET" ]; then
  cp "$TARGET" "$BACKUP_DIR/ApiQueryService.java.bak"
fi

cat > "$TARGET" <<'JAVA'
package com.nathan.tibiastats.application.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Compatibility query facade kept for services that still depend on the old ApiQueryService
 * contract while the read side is being split into smaller query services.
 *
 * This class intentionally contains only the query methods still referenced by application
 * services after the architecture cleanup.
 */
@Service
public class ApiQueryService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final NamedParameterJdbcTemplate jdbc;

    public ApiQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
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
            sql.append(" and job_name = :jobName\n");
            params.addValue("jobName", jobName.trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and status = :status\n");
            params.addValue("status", status.trim().toUpperCase());
        }
        sql.append(" order by started_at desc limit :limit");
        return jdbc.query(sql.toString(), params, this::mapScrapeJob);
    }

    public List<CharacterOnlinePointView> findCharacterOnlineHistory(String characterName,
                                                                     Instant from,
                                                                     Instant to,
                                                                     int limit) {
        var params = characterOnlineParams(characterName, from, to)
                .addValue("limit", safeLimit(limit));

        return jdbc.query("""
                select
                    coalesce(active_name.name, searched_name.name) as character_name,
                    w.name as world,
                    s.scrape_time,
                    c.level,
                    coalesce(v.promotion_name, v.name) as vocation
                from scrape_players sp
                join scrapes s on s.id = sp.scrape_id
                join worlds w on w.id = s.world_id
                join characters c on c.id = sp.character_id
                join character_names searched_name on searched_name.character_id = c.id
                left join character_names active_name on active_name.character_id = c.id and active_name.active is true
                left join vocations v on v.id = c.vocation_id
                where lower(searched_name.name) = lower(:characterName)
                  and (cast(:from as timestamp with time zone) is null or s.scrape_time >= cast(:from as timestamp with time zone))
                  and (cast(:to as timestamp with time zone) is null or s.scrape_time <= cast(:to as timestamp with time zone))
                order by s.scrape_time asc
                limit :limit
                """, params, this::mapCharacterOnlinePoint);
    }

    public List<CharacterOnlineSessionView> findCharacterOnlineSessions(String characterName,
                                                                       Instant from,
                                                                       Instant to,
                                                                       int sessionGapMinutes,
                                                                       int limit) {
        int safeGapMinutes = Math.max(1, sessionGapMinutes);
        var params = characterOnlineParams(characterName, from, to)
                .addValue("gapMinutes", safeGapMinutes)
                .addValue("limit", safeLimit(limit));

        return jdbc.query("""
                with points as (
                    select
                        w.name as world,
                        s.scrape_time,
                        lag(s.scrape_time) over (partition by w.name order by s.scrape_time) as previous_scrape_time
                    from scrape_players sp
                    join scrapes s on s.id = sp.scrape_id
                    join worlds w on w.id = s.world_id
                    join character_names searched_name on searched_name.character_id = sp.character_id
                    where lower(searched_name.name) = lower(:characterName)
                      and (cast(:from as timestamp with time zone) is null or s.scrape_time >= cast(:from as timestamp with time zone))
                      and (cast(:to as timestamp with time zone) is null or s.scrape_time <= cast(:to as timestamp with time zone))
                ), marked as (
                    select
                        world,
                        scrape_time,
                        case
                            when previous_scrape_time is null then 1
                            when scrape_time - previous_scrape_time > (:gapMinutes * interval '1 minute') then 1
                            else 0
                        end as new_session
                    from points
                ), grouped as (
                    select
                        world,
                        scrape_time,
                        sum(new_session) over (partition by world order by scrape_time) as session_id
                    from marked
                )
                select
                    world,
                    min(scrape_time) as started_at,
                    max(scrape_time) as ended_at,
                    greatest(0, floor(extract(epoch from (max(scrape_time) - min(scrape_time))) / 60))::bigint as duration_minutes,
                    count(*)::int as points
                from grouped
                group by world, session_id
                order by started_at desc
                limit :limit
                """, params, this::mapCharacterOnlineSession);
    }

    public List<CharacterOnlineWorldSummaryView> findCharacterOnlineWorldSummaries(String characterName,
                                                                                  Instant from,
                                                                                  Instant to) {
        var params = characterOnlineParams(characterName, from, to);

        return jdbc.query("""
                select
                    w.name as world,
                    count(*)::int as points,
                    min(s.scrape_time) as first_seen_at,
                    max(s.scrape_time) as last_seen_at,
                    greatest(0, floor(extract(epoch from (max(s.scrape_time) - min(s.scrape_time))) / 60))::bigint as duration_minutes
                from scrape_players sp
                join scrapes s on s.id = sp.scrape_id
                join worlds w on w.id = s.world_id
                join character_names searched_name on searched_name.character_id = sp.character_id
                where lower(searched_name.name) = lower(:characterName)
                  and (cast(:from as timestamp with time zone) is null or s.scrape_time >= cast(:from as timestamp with time zone))
                  and (cast(:to as timestamp with time zone) is null or s.scrape_time <= cast(:to as timestamp with time zone))
                group by w.name
                order by points desc, w.name asc
                """, params, this::mapCharacterOnlineWorldSummary);
    }

    private MapSqlParameterSource characterOnlineParams(String characterName, Instant from, Instant to) {
        return new MapSqlParameterSource("characterName", characterName)
                .addValue("from", toSqlTimestamp(from), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("to", toSqlTimestamp(to), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private static OffsetDateTime toSqlTimestamp(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static int safeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private ScrapeJobView mapScrapeJob(ResultSet rs, int rowNum) throws SQLException {
        return new ScrapeJobView(
                rs.getLong("id"),
                rs.getString("job_name"),
                rs.getString("status"),
                toInstant(rs, "started_at"),
                toInstant(rs, "finished_at"),
                getLongOrNull(rs, "duration_ms"),
                getIntegerOrNull(rs, "items_processed"),
                getIntegerOrNull(rs, "items_created"),
                getIntegerOrNull(rs, "items_updated"),
                getIntegerOrNull(rs, "items_failed"),
                rs.getString("error_message")
        );
    }

    private CharacterOnlinePointView mapCharacterOnlinePoint(ResultSet rs, int rowNum) throws SQLException {
        return new CharacterOnlinePointView(
                rs.getString("character_name"),
                rs.getString("world"),
                toInstant(rs, "scrape_time"),
                getIntegerOrNull(rs, "level"),
                rs.getString("vocation")
        );
    }

    private CharacterOnlineSessionView mapCharacterOnlineSession(ResultSet rs, int rowNum) throws SQLException {
        return new CharacterOnlineSessionView(
                rs.getString("world"),
                toInstant(rs, "started_at"),
                toInstant(rs, "ended_at"),
                rs.getLong("duration_minutes"),
                rs.getInt("points")
        );
    }

    private CharacterOnlineWorldSummaryView mapCharacterOnlineWorldSummary(ResultSet rs, int rowNum) throws SQLException {
        return new CharacterOnlineWorldSummaryView(
                rs.getString("world"),
                rs.getInt("points"),
                toInstant(rs, "first_seen_at"),
                toInstant(rs, "last_seen_at"),
                rs.getLong("duration_minutes")
        );
    }

    private static Instant toInstant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Integer getIntegerOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long getLongOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
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

    public record CharacterOnlinePointView(
            String characterName,
            String world,
            Instant timestamp,
            Integer level,
            String vocation
    ) {
        public Instant scrapeTime() {
            return timestamp;
        }
    }

    public record CharacterOnlineSessionView(
            String world,
            Instant startedAt,
            Instant endedAt,
            long durationMinutes,
            int points
    ) {
        public Instant startTime() {
            return startedAt;
        }

        public Instant endTime() {
            return endedAt;
        }

        public int snapshotCount() {
            return points;
        }

        public long durationSeconds() {
            return Duration.ofMinutes(durationMinutes).toSeconds();
        }
    }

    public record CharacterOnlineWorldSummaryView(
            String world,
            int points,
            Instant firstSeenAt,
            Instant lastSeenAt,
            long durationMinutes
    ) {
        public int snapshotCount() {
            return points;
        }

        public Instant startedAt() {
            return firstSeenAt;
        }

        public Instant endedAt() {
            return lastSeenAt;
        }

        public long durationSeconds() {
            return Duration.ofMinutes(durationMinutes).toSeconds();
        }
    }
}
JAVA

echo "Restored ApiQueryService compatibility facade at: $TARGET"
echo "Backup directory: $BACKUP_DIR"
echo "Next step: make test"
