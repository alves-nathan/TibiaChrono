package com.nathan.tibiastats.application.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class CharacterOnlineReadModelService extends JdbcReadModelSupport {

    public CharacterOnlineReadModelService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public List<ApiQueryService.CharacterOnlinePointView> findHistory(String characterName,
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

    public List<ApiQueryService.CharacterOnlineSessionView> findSessions(String characterName,
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

    public List<ApiQueryService.CharacterOnlineWorldSummaryView> findWorldSummaries(String characterName,
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

    private ApiQueryService.CharacterOnlinePointView mapCharacterOnlinePoint(ResultSet rs, int rowNum) throws SQLException {
        return new ApiQueryService.CharacterOnlinePointView(
                rs.getLong("character_id"),
                rs.getString("character_name"),
                rs.getLong("scrape_id"),
                rs.getString("world"),
                toInstant(rs.getTimestamp("scrape_time")),
                getNullableInteger(rs, "players_online")
        );
    }

    private ApiQueryService.CharacterOnlineSessionView mapCharacterOnlineSession(ResultSet rs, int rowNum) throws SQLException {
        return new ApiQueryService.CharacterOnlineSessionView(
                rs.getLong("character_id"),
                rs.getString("character_name"),
                rs.getString("world"),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("ended_at")),
                getNullableLong(rs, "observed_minutes"),
                getNullableInteger(rs, "samples")
        );
    }

    private ApiQueryService.CharacterOnlineWorldSummaryView mapCharacterOnlineWorldSummary(ResultSet rs, int rowNum) throws SQLException {
        return new ApiQueryService.CharacterOnlineWorldSummaryView(
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
}
