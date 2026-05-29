package com.nathan.tibiastats.application.query;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@ReadModelService
@ReadModelComponent
public class WorldOnlineAnalyticsService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;
    private static final int MAX_COMPARE_WORLDS = 20;

    private final NamedParameterJdbcTemplate jdbc;

    public WorldOnlineAnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public List<WorldOnlineBucketView> buckets(String world, Instant from, Instant to, String bucket) {
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

    public WorldOnlineSummaryView summary(String world, Instant from, Instant to) {
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
                .orElseGet(() -> WorldOnlineSummaryView.empty(canonicalWorld));
    }

    public List<WorldOnlineBucketView> compare(String worlds, Instant from, Instant to, String bucket) {
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

    public List<WorldOnlineRankingView> ranking(String metric, Instant from, Instant to, Integer limit) {
        validateRange(from, to);
        OnlineRankingMetric rankingMetric = OnlineRankingMetric.from(metric);
        var params = new MapSqlParameterSource("limit", safeLimit(limit));
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

    private String requireWorld(String world) {
        if (world == null || world.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "world is required");
        }
        var params = new MapSqlParameterSource("world", world.trim());
        return jdbc.query("""
                select name
                from worlds
                where lower(name) = lower(:world)
                limit 1
                """, params, (rs, rowNum) -> rs.getString("name"))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "World not found: " + world));
    }

    private List<String> parseWorlds(String worlds) {
        if (worlds == null || worlds.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "worlds is required. Use a comma-separated list, e.g. worlds=Antica,Secura");
        }
        List<String> parsed = Arrays.stream(worlds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (parsed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "worlds must contain at least one world name");
        }
        if (parsed.size() > MAX_COMPARE_WORLDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "worlds supports at most " + MAX_COMPARE_WORLDS + " names per request");
        }
        return parsed;
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before or equal to to");
        }
    }

    private void appendRange(StringBuilder sql, MapSqlParameterSource params, Instant from, Instant to) {
        if (from != null) {
            sql.append(" and s.scrape_time >= :from\n");
            params.addValue("from", Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" and s.scrape_time <= :to\n");
            params.addValue("to", Timestamp.from(to));
        }
    }

    private void appendJoinRange(StringBuilder sql, MapSqlParameterSource params, Instant from, Instant to) {
        if (from != null) {
            sql.append(" and s.scrape_time >= :from\n");
            params.addValue("from", Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" and s.scrape_time <= :to\n");
            params.addValue("to", Timestamp.from(to));
        }
        sql.append('\n');
    }

    private int safeLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    private WorldOnlineBucketView mapBucket(ResultSet rs, int rowNum) throws SQLException {
        Integer first = getNullableInteger(rs, "first_players_online");
        Integer last = getNullableInteger(rs, "last_players_online");
        return new WorldOnlineBucketView(
                rs.getString("world"),
                toInstant(rs.getTimestamp("bucket_start")),
                getNullableInteger(rs, "samples"),
                getNullableDouble(rs, "average_players_online"),
                getNullableInteger(rs, "min_players_online"),
                getNullableInteger(rs, "max_players_online"),
                first,
                last,
                first == null || last == null ? null : last - first
        );
    }

    private WorldOnlineSummaryView mapSummary(ResultSet rs, int rowNum) throws SQLException {
        Integer first = getNullableInteger(rs, "first_players_online");
        Integer latest = getNullableInteger(rs, "latest_players_online");
        return new WorldOnlineSummaryView(
                rs.getString("world"),
                getNullableInteger(rs, "samples"),
                toInstant(rs.getTimestamp("first_scrape_at")),
                toInstant(rs.getTimestamp("last_scrape_at")),
                getNullableInteger(rs, "min_players_online"),
                getNullableInteger(rs, "peak_players_online"),
                toInstant(rs.getTimestamp("peak_at")),
                getNullableDouble(rs, "average_players_online"),
                first,
                latest,
                first == null || latest == null ? null : latest - first
        );
    }

    private WorldOnlineRankingView mapRanking(ResultSet rs, int rowNum) throws SQLException {
        Integer first = getNullableInteger(rs, "first_players_online");
        Integer latest = getNullableInteger(rs, "latest_players_online");
        return new WorldOnlineRankingView(
                rs.getString("world"),
                rs.getString("metric"),
                getNullableDouble(rs, "metric_value"),
                getNullableInteger(rs, "samples"),
                toInstant(rs.getTimestamp("first_scrape_at")),
                toInstant(rs.getTimestamp("last_scrape_at")),
                getNullableInteger(rs, "peak_players_online"),
                getNullableDouble(rs, "average_players_online"),
                first,
                latest,
                first == null || latest == null ? null : latest - first
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Integer getNullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private enum OnlineBucket {
        HOUR("hour"),
        DAY("day");

        private final String sqlValue;

        OnlineBucket(String sqlValue) {
            this.sqlValue = sqlValue;
        }

        String sqlValue() {
            return sqlValue;
        }

        static OnlineBucket from(String value) {
            if (value == null || value.isBlank()) {
                return HOUR;
            }
            try {
                return OnlineBucket.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported bucket: " + value + ". Supported values: hour, day", ex);
            }
        }
    }

    private enum OnlineRankingMetric {
        PEAK("peak", "peak_players_online"),
        AVERAGE("average", "average_players_online"),
        GROWTH("growth", "latest_players_online - first_players_online"),
        LATEST("latest", "latest_players_online");

        private final String apiValue;
        private final String sqlExpression;

        OnlineRankingMetric(String apiValue, String sqlExpression) {
            this.apiValue = apiValue;
            this.sqlExpression = sqlExpression;
        }

        String apiValue() {
            return apiValue;
        }

        String sqlExpression() {
            return sqlExpression;
        }

        static OnlineRankingMetric from(String value) {
            if (value == null || value.isBlank()) {
                return PEAK;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            try {
                return OnlineRankingMetric.valueOf(normalized);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported metric: " + value + ". Supported values: peak, average, growth, latest", ex);
            }
        }
    }

    public record WorldOnlineBucketView(
            String world,
            Instant bucketStart,
            Integer samples,
            Double averagePlayersOnline,
            Integer minPlayersOnline,
            Integer maxPlayersOnline,
            Integer firstPlayersOnline,
            Integer lastPlayersOnline,
            Integer changePlayersOnline
    ) {}

    public record WorldOnlineSummaryView(
            String world,
            Integer samples,
            Instant firstScrapeAt,
            Instant lastScrapeAt,
            Integer minPlayersOnline,
            Integer peakPlayersOnline,
            Instant peakAt,
            Double averagePlayersOnline,
            Integer firstPlayersOnline,
            Integer latestPlayersOnline,
            Integer changePlayersOnline
    ) {
        static WorldOnlineSummaryView empty(String world) {
            return new WorldOnlineSummaryView(world, 0, null, null, null, null, null, null, null, null, null);
        }
    }

    public record WorldOnlineRankingView(
            String world,
            String metric,
            Double metricValue,
            Integer samples,
            Instant firstScrapeAt,
            Instant lastScrapeAt,
            Integer peakPlayersOnline,
            Double averagePlayersOnline,
            Integer firstPlayersOnline,
            Integer latestPlayersOnline,
            Integer changePlayersOnline
    ) {}
}
