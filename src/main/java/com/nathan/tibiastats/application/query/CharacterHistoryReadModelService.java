package com.nathan.tibiastats.application.query;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class CharacterHistoryReadModelService extends CharacterTimelineJdbcSupport {
    private static final int DEFAULT_LIMIT = 200;

    public CharacterHistoryReadModelService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public List<CharacterTimelineService.CharacterDeathView> deaths(String characterName, Instant from, Instant to, Integer limit) {
        var params = baseParams(characterName, from, to, limit, DEFAULT_LIMIT);
        return jdbc.query("""
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
                    d.id,
                    r.character_id,
                    active_name.name as character_name,
                    d.death_date,
                    d.killed_by
                from resolved r
                join character_deaths d on d.character_id = r.character_id
                left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
                where d.death_date is not null
                  and (cast(:fromInstant as timestamp with time zone) is null or d.death_date >= :fromInstant)
                  and (cast(:toInstant as timestamp with time zone) is null or d.death_date <= :toInstant)
                order by d.death_date desc, d.id desc
                limit :limit
                """, prepareParams(params), this::mapDeath);
    }

    public List<CharacterTimelineService.CharacterWorldHistoryView> worldHistory(String characterName, Instant from, Instant to, Integer limit) {
        var params = baseParams(characterName, from, to, limit, DEFAULT_LIMIT);
        return jdbc.query("""
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
                    cw.id,
                    r.character_id,
                    active_name.name as character_name,
                    w.name as world,
                    cw.active,
                    cw.inactive_date
                from resolved r
                join character_worlds cw on cw.character_id = r.character_id
                join worlds w on w.id = cw.world_id
                left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
                where (cast(:fromInstant as timestamp with time zone) is null or cw.inactive_date is null or cw.inactive_date >= :fromInstant)
                  and (cast(:toInstant as timestamp with time zone) is null or cw.inactive_date is null or cw.inactive_date <= :toInstant)
                order by cw.active desc, cw.inactive_date desc nulls first, cw.id desc
                limit :limit
                """, prepareParams(params), this::mapWorldHistory);
    }

    public List<CharacterTimelineService.CharacterGuildHistoryView> guildHistory(String characterName, Instant from, Instant to, Integer limit) {
        var params = baseParams(characterName, from, to, limit, DEFAULT_LIMIT);
        return jdbc.query("""
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
                    gm.id as membership_id,
                    r.character_id,
                    active_name.name as character_name,
                    g.id as guild_id,
                    g.name as guild_name,
                    w.name as world,
                    gm.rank_name,
                    gm.title,
                    gm.vocation,
                    gm.level,
                    gm.joined_at,
                    gm.first_seen_at,
                    gm.last_seen_at,
                    gm.left_at,
                    gm.active
                from resolved r
                join guild_memberships gm on gm.character_id = r.character_id
                join guilds g on g.id = gm.guild_id
                join worlds w on w.id = g.world_id
                left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
                where (cast(:fromInstant as timestamp with time zone) is null or coalesce(gm.left_at, gm.last_seen_at, gm.joined_at) >= :fromInstant)
                  and (cast(:toInstant as timestamp with time zone) is null or gm.joined_at <= :toInstant)
                order by gm.active desc, gm.joined_at desc, gm.id desc
                limit :limit
                """, prepareParams(params), this::mapGuildHistory);
    }

    private CharacterTimelineService.CharacterDeathView mapDeath(ResultSet rs, int rowNum) throws SQLException {
        return new CharacterTimelineService.CharacterDeathView(
                rs.getLong("id"),
                rs.getLong("character_id"),
                rs.getString("character_name"),
                toInstant(rs.getTimestamp("death_date")),
                rs.getString("killed_by")
        );
    }

    private CharacterTimelineService.CharacterWorldHistoryView mapWorldHistory(ResultSet rs, int rowNum) throws SQLException {
        return new CharacterTimelineService.CharacterWorldHistoryView(
                rs.getLong("id"),
                rs.getLong("character_id"),
                rs.getString("character_name"),
                rs.getString("world"),
                getNullableBoolean(rs, "active"),
                toInstant(rs.getTimestamp("inactive_date"))
        );
    }

    private CharacterTimelineService.CharacterGuildHistoryView mapGuildHistory(ResultSet rs, int rowNum) throws SQLException {
        return new CharacterTimelineService.CharacterGuildHistoryView(
                rs.getLong("membership_id"),
                rs.getLong("character_id"),
                rs.getString("character_name"),
                rs.getLong("guild_id"),
                rs.getString("guild_name"),
                rs.getString("world"),
                rs.getString("rank_name"),
                rs.getString("title"),
                rs.getString("vocation"),
                getNullableInteger(rs, "level"),
                toInstant(rs.getTimestamp("joined_at")),
                toInstant(rs.getTimestamp("first_seen_at")),
                toInstant(rs.getTimestamp("last_seen_at")),
                toInstant(rs.getTimestamp("left_at")),
                getNullableBoolean(rs, "active")
        );
    }
}
