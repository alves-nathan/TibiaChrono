package com.nathan.tibiastats.application.query;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class CharacterOnlineHistoryReadModelService extends CharacterOnlineJdbcSupport {

    public CharacterOnlineHistoryReadModelService(JdbcTemplate jdbcTemplate) {
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
        var params = baseCharacterOnlineParams(characterName)
                .addValue("limit", safeLimit(limit));
        appendCharacterOnlineFilters(sql, params, world, from, to);
        sql.append(" order by s.scrape_time asc, w.name asc, s.id asc limit :limit");
        return jdbc.query(sql.toString(), prepareParams(params), this::mapCharacterOnlinePoint);
    }
}
