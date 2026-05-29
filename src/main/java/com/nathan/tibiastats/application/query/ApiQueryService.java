package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.sql.Types;
import java.time.ZoneOffset;

@ReadModelService
@ReadModelComponent
public class ApiQueryService {
    private final NamedParameterJdbcTemplate jdbc;

    public ApiQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private static OffsetDateTime toSqlTimestamp(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
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
                """, prepareParams(params), this::mapWorld);
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
                """, prepareParams(params), this::mapCharacter);
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
                """, prepareParams(params), this::mapCharacterName);
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
        return jdbc.query(sql.toString(), prepareParams(params), this::mapHighscore);
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
        return jdbc.query(sql.toString(), prepareParams(params), this::mapHighscore);
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
        return jdbc.query(sql.toString(), prepareParams(params), this::mapScrapeJob);
    }


    public List<CharacterOnlinePointView> findCharacterOnlineHistory(String characterName,
                                                                     String world,
                                                                     Instant from,
                                                                     Instant to,
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
                    r.character_id,
                    active_name.name as character_name,
                    s.id as scrape_id,
                    w.name as world,
                    s.scrape_time,
                    s.players_online
                from resolved r
                join scrape_players sp on sp.character_id = r.character_id
                join scrapes s on s.id = sp.scrape_id
                join worlds w on w.id = s.world_id
                left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
                where 1 = 1
                """);
        var params = new MapSqlParameterSource("characterName", characterName)
                .addValue("limit", safeLimit(limit));
        appendCharacterOnlineFilters(sql, params, world, from, to);
        sql.append(" order by s.scrape_time asc, w.name asc, s.id asc limit :limit");
        return jdbc.query(sql.toString(), prepareParams(params), this::mapCharacterOnlinePoint);
    }

    public List<CharacterOnlineSessionView> findCharacterOnlineSessions(String characterName,
                                                                        String world,
                                                                        Instant from,
                                                                        Instant to,
                                                                        int maxGapMinutes,
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
                ), raw as (
                    select
                        r.character_id,
                        active_name.name as character_name,
                        w.name as world,
                        s.scrape_time,
                        lag(s.scrape_time) over (partition by w.id order by s.scrape_time, s.id) as previous_seen_at
                    from resolved r
                    join scrape_players sp on sp.character_id = r.character_id
                    join scrapes s on s.id = sp.scrape_id
                    join worlds w on w.id = s.world_id
                    left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
                    where 1 = 1
                """);
        var params = new MapSqlParameterSource("characterName", characterName)
                .addValue("limit", safeLimit(limit))
                .addValue("maxGapMinutes", maxGapMinutes);
        appendCharacterOnlineFilters(sql, params, world, from, to);
        sql.append("""
                ), grouped as (
                    select
                        raw.*,
                        sum(
                            case
                                when previous_seen_at is null
                                  or scrape_time - previous_seen_at > (:maxGapMinutes * interval '1 minute')
                                then 1
                                else 0
                            end
                        ) over (partition by world order by scrape_time rows unbounded preceding) as session_no
                    from raw
                )
                select
                    character_id,
                    character_name,
                    world,
                    min(scrape_time) as started_at,
                    max(scrape_time) as ended_at,
                    cast(floor(extract(epoch from (max(scrape_time) - min(scrape_time))) / 60) as bigint) as observed_minutes,
                    cast(count(*) as int) as samples
                from grouped
                group by character_id, character_name, world, session_no
                order by ended_at desc, world asc
                limit :limit
                """);
        return jdbc.query(sql.toString(), prepareParams(params), this::mapCharacterOnlineSession);
    }

    public List<CharacterOnlineWorldSummaryView> findCharacterOnlineWorldSummaries(String characterName,
                                                                                  String world,
                                                                                  Instant from,
                                                                                  Instant to,
                                                                                  int maxGapMinutes) {
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
                ), raw as (
                    select
                        r.character_id,
                        active_name.name as character_name,
                        w.name as world,
                        w.id as world_id,
                        s.scrape_time,
                        lag(s.scrape_time) over (partition by w.id order by s.scrape_time, s.id) as previous_seen_at
                    from resolved r
                    join scrape_players sp on sp.character_id = r.character_id
                    join scrapes s on s.id = sp.scrape_id
                    join worlds w on w.id = s.world_id
                    left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
                    where 1 = 1
                """);
        var params = new MapSqlParameterSource("characterName", characterName)
                .addValue("maxGapMinutes", maxGapMinutes);
        appendCharacterOnlineFilters(sql, params, world, from, to);
        sql.append("""
                ), grouped as (
                    select
                        raw.*,
                        sum(
                            case
                                when previous_seen_at is null
                                  or scrape_time - previous_seen_at > (:maxGapMinutes * interval '1 minute')
                                then 1
                                else 0
                            end
                        ) over (partition by world_id order by scrape_time rows unbounded preceding) as session_no
                    from raw
                ), sessions as (
                    select
                        character_id,
                        character_name,
                        world,
                        session_no,
                        min(scrape_time) as started_at,
                        max(scrape_time) as ended_at,
                        cast(floor(extract(epoch from (max(scrape_time) - min(scrape_time))) / 60) as bigint) as observed_minutes
                    from grouped
                    group by character_id, character_name, world, session_no
                ), session_summary as (
                    select
                        world,
                        cast(count(*) as int) as sessions,
                        cast(coalesce(sum(observed_minutes), 0) as bigint) as observed_minutes
                    from sessions
                    group by world
                ), point_summary as (
                    select
                        character_id,
                        character_name,
                        world,
                        cast(count(*) as int) as appearances,
                        min(scrape_time) as first_seen_at,
                        max(scrape_time) as last_seen_at
                    from raw
                    group by character_id, character_name, world
                )
                select
                    ps.character_id,
                    ps.character_name,
                    ps.world,
                    ps.appearances,
                    coalesce(ss.sessions, 0) as sessions,
                    cast(coalesce(ss.observed_minutes, 0) as bigint) as observed_minutes,
                    ps.first_seen_at,
                    ps.last_seen_at
                from point_summary ps
                left join session_summary ss on ss.world = ps.world
                order by ps.world asc
                """);
        return jdbc.query(sql.toString(), prepareParams(params), this::mapCharacterOnlineWorldSummary);
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


    private void appendCharacterOnlineFilters(StringBuilder sql,
                                              MapSqlParameterSource params,
                                              String world,
                                              Instant from,
                                              Instant to) {
        if (world != null && !world.isBlank()) {
            sql.append(" and lower(w.name) = lower(:onlineWorld)");
            params.addValue("onlineWorld", world.trim());
        }
        if (from != null) {
            sql.append(" and s.scrape_time >= :onlineFrom");
            params.addValue("onlineFrom", from);
        }
        if (to != null) {
            sql.append(" and s.scrape_time <= :onlineTo");
            params.addValue("onlineTo", to);
        }
    }


    private MapSqlParameterSource prepareParams(MapSqlParameterSource params) {
        if (params == null) {
            return new MapSqlParameterSource();
        }

        for (var entry : new ArrayList<>(params.getValues().entrySet())) {
            if (entry.getValue() instanceof Instant instant) {
                params.addValue(entry.getKey(), Timestamp.from(instant), Types.TIMESTAMP);
            }
        }

        return params;
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


    private CharacterOnlinePointView mapCharacterOnlinePoint(ResultSet rs, int rowNum) throws SQLException {
        return new CharacterOnlinePointView(
                rs.getLong("character_id"),
                rs.getString("character_name"),
                rs.getLong("scrape_id"),
                rs.getString("world"),
                toInstant(rs.getTimestamp("scrape_time")),
                getNullableInteger(rs, "players_online")
        );
    }

    private CharacterOnlineSessionView mapCharacterOnlineSession(ResultSet rs, int rowNum) throws SQLException {
        return new CharacterOnlineSessionView(
                rs.getLong("character_id"),
                rs.getString("character_name"),
                rs.getString("world"),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("ended_at")),
                getNullableLong(rs, "observed_minutes"),
                getNullableInteger(rs, "samples")
        );
    }

    private CharacterOnlineWorldSummaryView mapCharacterOnlineWorldSummary(ResultSet rs, int rowNum) throws SQLException {
        return new CharacterOnlineWorldSummaryView(
                rs.getLong("character_id"),
                rs.getString("character_name"),
                rs.getString("world"),
                getNullableInteger(rs, "appearances"),
                getNullableInteger(rs, "sessions"),
                getNullableLong(rs, "observed_minutes"),
                toInstant(rs.getTimestamp("first_seen_at")),
                toInstant(rs.getTimestamp("last_seen_at"))
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


    public record CharacterOnlinePointView(
            Long characterId,
            String characterName,
            Long scrapeId,
            String world,
            Instant timestamp,
            Integer playersOnline
    ) {}

    public record CharacterOnlineSessionView(
            Long characterId,
            String characterName,
            String world,
            Instant startedAt,
            Instant endedAt,
            Long observedMinutes,
            Integer samples
    ) {}

    public record CharacterOnlineWorldSummaryView(
            Long characterId,
            String characterName,
            String world,
            Integer appearances,
            Integer sessions,
            Long observedMinutes,
            Instant firstSeenAt,
            Instant lastSeenAt
    ) {}

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
