package com.nathan.tibiastats.application.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.Instant;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class WorldOnlineBucketReadModelService extends WorldOnlineAnalyticsJdbcSupport {

    public WorldOnlineBucketReadModelService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public List<WorldOnlineAnalyticsService.WorldOnlineBucketView> buckets(String world, Instant from, Instant to, String bucket) {
        validateRange(from, to);
        String canonicalWorld = requireWorld(world);
        OnlineBucket onlineBucket = OnlineBucket.from(bucket);
        var params = new MapSqlParameterSource("world", canonicalWorld);
        var sql = new StringBuilder("""
                select
                    w.name as world,
                    date_trunc('__BUCKET__', s.scrape_time) as bucket_start,
                    cast(count(*) as int) as samples,
                    cast(avg(s.players_online) as double precision) as average_players_online,
                    min(s.players_online) as min_players_online,
                    max(s.players_online) as max_players_online,
                    (array_agg(s.players_online order by s.scrape_time asc, s.id asc))[1] as first_players_online,
                    (array_agg(s.players_online order by s.scrape_time desc, s.id desc))[1] as last_players_online
                from scrapes s
                join worlds w on w.id = s.world_id
                where lower(w.name) = lower(:world)
                """.replace("__BUCKET__", onlineBucket.sqlValue()));
        appendRange(sql, params, from, to);
        sql.append("""
                group by w.name, bucket_start
                order by bucket_start asc
                """);
        return jdbc.query(sql.toString(), params, this::mapBucket);
    }

    public List<WorldOnlineAnalyticsService.WorldOnlineBucketView> compare(String worlds, Instant from, Instant to, String bucket) {
        validateRange(from, to);
        List<String> requestedWorlds = parseWorlds(worlds);
        OnlineBucket onlineBucket = OnlineBucket.from(bucket);

        var params = new MapSqlParameterSource("worlds", requestedWorlds);
        var sql = new StringBuilder("""
                select
                    w.name as world,
                    date_trunc('__BUCKET__', s.scrape_time) as bucket_start,
                    cast(count(*) as int) as samples,
                    cast(avg(s.players_online) as double precision) as average_players_online,
                    min(s.players_online) as min_players_online,
                    max(s.players_online) as max_players_online,
                    (array_agg(s.players_online order by s.scrape_time asc, s.id asc))[1] as first_players_online,
                    (array_agg(s.players_online order by s.scrape_time desc, s.id desc))[1] as last_players_online
                from scrapes s
                join worlds w on w.id = s.world_id
                where lower(w.name) in (:worlds)
                """.replace("__BUCKET__", onlineBucket.sqlValue()));
        appendRange(sql, params, from, to);
        sql.append("""
                group by w.name, bucket_start
                order by w.name asc, bucket_start asc
                """);
        return jdbc.query(sql.toString(), params, this::mapBucket);
    }
}
