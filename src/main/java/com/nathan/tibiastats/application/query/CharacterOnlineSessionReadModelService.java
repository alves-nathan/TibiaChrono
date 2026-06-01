package com.nathan.tibiastats.application.query;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class CharacterOnlineSessionReadModelService extends CharacterOnlineJdbcSupport {

    public CharacterOnlineSessionReadModelService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
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
        var params = baseCharacterOnlineParams(characterName)
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
}
