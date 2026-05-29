package com.nathan.tibiastats.application.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.Instant;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class WorldOnlineRankingReadModelService extends WorldOnlineAnalyticsJdbcSupport {

    public WorldOnlineRankingReadModelService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public List<WorldOnlineAnalyticsService.WorldOnlineRankingView> ranking(String metric, Instant from, Instant to, Integer limit) {
        validateRange(from, to);
        OnlineRankingMetric rankingMetric = OnlineRankingMetric.from(metric);
        var params = new MapSqlParameterSource("limit", safeRankingLimit(limit));
        var sql = new StringBuilder("""
                with filtered as (
                    select
                        w.name as world,
                        s.id,
                        s.scrape_time,
                        s.players_online
                    from scrapes s
                    join worlds w on w.id = s.world_id
                    where 1 = 1
                """);
        appendRange(sql, params, from, to);
        sql.append("""
                ), aggregated as (
                    select
                        world,
                        cast(count(*) as int) as samples,
                        min(scrape_time) as first_scrape_at,
                        max(scrape_time) as last_scrape_at,
                        max(players_online) as peak_players_online,
                        cast(avg(players_online) as double precision) as average_players_online,
                        (array_agg(players_online order by scrape_time asc, id asc))[1] as first_players_online,
                        (array_agg(players_online order by scrape_time desc, id desc))[1] as latest_players_online
                    from filtered
                    group by world
                )
                select
                    world,
                    '__METRIC__' as metric,
                    cast(__METRIC_EXPR__ as double precision) as metric_value,
                    samples,
                    first_scrape_at,
                    last_scrape_at,
                    peak_players_online,
                    average_players_online,
                    first_players_online,
                    latest_players_online
                from aggregated
                order by __METRIC_EXPR__ desc nulls last, world asc
                limit :limit
                """.replace("__METRIC__", rankingMetric.apiValue())
                .replace("__METRIC_EXPR__", rankingMetric.sqlExpression()));
        return jdbc.query(sql.toString(), params, this::mapRanking);
    }
}
