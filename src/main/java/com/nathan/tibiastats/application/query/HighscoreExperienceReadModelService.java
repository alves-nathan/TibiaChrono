package com.nathan.tibiastats.application.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.LocalDate;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class HighscoreExperienceReadModelService extends HighscoreApiJdbcSupport {

    public HighscoreExperienceReadModelService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public List<HighscoreApiQueryService.ExperienceDailyView> findExperienceDaily(String world,
                                                                                  LocalDate date,
                                                                                  Integer vocationFilterId,
                                                                                  int limit) {
        var params = new MapSqlParameterSource()
                .addValue("world", normalizeRequired(world, "world"))
                .addValue("date", date)
                .addValue("vocationFilterId", vocationFilterId == null ? 0 : vocationFilterId)
                .addValue("limit", safeLimit(limit));

        return jdbc.query("""
                with selected_date as (
                    select coalesce(:date, max(d.date)) as value
                    from highscore_exp_daily d
                    join worlds w on w.id = d.world_id
                    where lower(w.name) = lower(:world)
                )
                select
                    d.date,
                    r.rank,
                    active_name.name as character_name,
                    d.character_id,
                    w.name as world,
                    cast(:vocationFilterId as integer) as vocation_filter_id,
                    d.experience,
                    d.level,
                    d.first_seen_filter,
                    d.scraped_at
                from highscore_exp_daily d
                join worlds w on w.id = d.world_id
                join selected_date sd on sd.value = d.date
                left join highscore_exp_rank_daily r
                    on r.date = d.date
                   and r.character_id = d.character_id
                   and r.world_id = d.world_id
                   and r.vocation_filter_id = :vocationFilterId
                left join character_names active_name
                    on active_name.character_id = d.character_id
                   and active_name.active is true
                where lower(w.name) = lower(:world)
                order by r.rank nulls last, d.experience desc, active_name.name nulls last
                limit :limit
                """, params, this::mapExperienceDaily);
    }

    public List<HighscoreApiQueryService.ExperienceDailyView> findExperienceRanks(String world,
                                                                                  LocalDate date,
                                                                                  Integer vocationFilterId,
                                                                                  int limit) {
        var params = new MapSqlParameterSource()
                .addValue("world", normalizeRequired(world, "world"))
                .addValue("date", date)
                .addValue("vocationFilterId", vocationFilterId == null ? 0 : vocationFilterId)
                .addValue("limit", safeLimit(limit));

        return jdbc.query("""
                with selected_date as (
                    select coalesce(:date, max(r.date)) as value
                    from highscore_exp_rank_daily r
                    join worlds w on w.id = r.world_id
                    where lower(w.name) = lower(:world)
                      and r.vocation_filter_id = :vocationFilterId
                )
                select
                    r.date,
                    r.rank,
                    active_name.name as character_name,
                    r.character_id,
                    w.name as world,
                    r.vocation_filter_id,
                    d.experience,
                    d.level,
                    d.first_seen_filter,
                    greatest(r.scraped_at, d.scraped_at) as scraped_at
                from highscore_exp_rank_daily r
                join worlds w on w.id = r.world_id
                join selected_date sd on sd.value = r.date
                join highscore_exp_daily d
                    on d.date = r.date
                   and d.character_id = r.character_id
                   and d.world_id = r.world_id
                left join character_names active_name
                    on active_name.character_id = r.character_id
                   and active_name.active is true
                where lower(w.name) = lower(:world)
                  and r.vocation_filter_id = :vocationFilterId
                order by r.rank asc
                limit :limit
                """, params, this::mapExperienceDaily);
    }

    public List<HighscoreApiQueryService.ExperienceGainView> findExperienceGains(String world,
                                                                                 LocalDate startDate,
                                                                                 LocalDate endDate,
                                                                                 Integer vocationFilterId,
                                                                                 int limit) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be greater than or equal to startDate");
        }

        var params = new MapSqlParameterSource()
                .addValue("world", normalizeRequired(world, "world"))
                .addValue("startDate", startDate)
                .addValue("endDate", endDate)
                .addValue("vocationFilterId", vocationFilterId == null ? 0 : vocationFilterId)
                .addValue("limit", safeLimit(limit));

        return jdbc.query("""
                with candidates as (
                    select d.*
                    from highscore_exp_daily d
                    join worlds w on w.id = d.world_id
                    where lower(w.name) = lower(:world)
                      and d.date between :startDate and :endDate
                ),
                start_rows as (
                    select distinct on (character_id, world_id)
                        character_id, world_id, date, experience
                    from candidates
                    order by character_id, world_id, date asc
                ),
                end_rows as (
                    select distinct on (character_id, world_id)
                        character_id, world_id, date, experience
                    from candidates
                    order by character_id, world_id, date desc
                )
                select
                    active_name.name as character_name,
                    e.character_id,
                    w.name as world,
                    s.date as start_date,
                    e.date as end_date,
                    s.experience as start_experience,
                    e.experience as end_experience,
                    e.experience - s.experience as gain,
                    sr.rank as start_rank,
                    er.rank as end_rank,
                    cast(:vocationFilterId as integer) as vocation_filter_id
                from end_rows e
                join start_rows s
                  on s.character_id = e.character_id
                 and s.world_id = e.world_id
                join worlds w on w.id = e.world_id
                left join highscore_exp_rank_daily sr
                  on sr.date = s.date
                 and sr.character_id = s.character_id
                 and sr.world_id = s.world_id
                 and sr.vocation_filter_id = :vocationFilterId
                left join highscore_exp_rank_daily er
                  on er.date = e.date
                 and er.character_id = e.character_id
                 and er.world_id = e.world_id
                 and er.vocation_filter_id = :vocationFilterId
                left join character_names active_name
                  on active_name.character_id = e.character_id
                 and active_name.active is true
                where e.date > s.date
                  and e.experience >= s.experience
                  and er.rank is not null
                order by gain desc, end_experience desc, active_name.name nulls last
                limit :limit
                """, params, this::mapExperienceGain);
    }

    public List<HighscoreApiQueryService.ExperienceDailyView> findCharacterExperienceDaily(String characterName,
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
                    d.date,
                    r.rank,
                    active_name.name as character_name,
                    d.character_id,
                    w.name as world,
                    cast(:vocationFilterId as integer) as vocation_filter_id,
                    d.experience,
                    d.level,
                    d.first_seen_filter,
                    d.scraped_at
                from highscore_exp_daily d
                join resolved resolved on resolved.character_id = d.character_id
                join worlds w on w.id = d.world_id
                left join highscore_exp_rank_daily r
                    on r.date = d.date
                   and r.character_id = d.character_id
                   and r.world_id = d.world_id
                   and r.vocation_filter_id = :vocationFilterId
                left join character_names active_name
                    on active_name.character_id = d.character_id
                   and active_name.active is true
                where 1 = 1
                """);
        var params = new MapSqlParameterSource()
                .addValue("characterName", normalizeRequired(characterName, "characterName"))
                .addValue("world", world == null ? null : world.trim())
                .addValue("vocationFilterId", vocationFilterId == null ? 0 : vocationFilterId)
                .addValue("fromDate", from)
                .addValue("toDate", to)
                .addValue("limit", safeLimit(limit));
        if (world != null && !world.isBlank()) {
            sql.append(" and lower(w.name) = lower(:world)");
        }
        if (from != null) {
            sql.append(" and d.date >= :fromDate");
        }
        if (to != null) {
            sql.append(" and d.date <= :toDate");
        }
        sql.append(" order by d.date desc, w.name limit :limit");
        return jdbc.query(sql.toString(), params, this::mapExperienceDaily);
    }
}
