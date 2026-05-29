package com.nathan.tibiastats.application.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.Instant;

@ReadModelService
@ReadModelComponent
public class WorldOnlineSummaryReadModelService extends WorldOnlineAnalyticsJdbcSupport {

    public WorldOnlineSummaryReadModelService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public WorldOnlineAnalyticsService.WorldOnlineSummaryView summary(String world, Instant from, Instant to) {
        validateRange(from, to);
        String canonicalWorld = requireWorld(world);
        var params = new MapSqlParameterSource("world", canonicalWorld);
        var sql = new StringBuilder("""
                select
                    w.name as world,
                    cast(count(s.id) as int) as samples,
                    min(s.scrape_time) as first_scrape_at,
                    max(s.scrape_time) as last_scrape_at,
                    min(s.players_online) as min_players_online,
                    max(s.players_online) as peak_players_online,
                    cast(avg(s.players_online) as double precision) as average_players_online,
                    (array_agg(s.players_online order by s.scrape_time asc, s.id asc) filter (where s.id is not null))[1] as first_players_online,
                    (array_agg(s.players_online order by s.scrape_time desc, s.id desc) filter (where s.id is not null))[1] as latest_players_online,
                    (array_agg(s.scrape_time order by s.players_online desc, s.scrape_time asc, s.id asc) filter (where s.id is not null))[1] as peak_at
                from worlds w
                left join scrapes s on s.world_id = w.id
                """);
        appendJoinRange(sql, params, from, to);
        sql.append("""
                where lower(w.name) = lower(:world)
                group by w.name
                limit 1
                """);
        return jdbc.query(sql.toString(), params, this::mapSummary)
                .stream()
                .findFirst()
                .orElseGet(() -> WorldOnlineAnalyticsService.WorldOnlineSummaryView.empty(canonicalWorld));
    }
}
