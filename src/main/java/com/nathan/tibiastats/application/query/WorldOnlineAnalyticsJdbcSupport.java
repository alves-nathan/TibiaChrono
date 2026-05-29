package com.nathan.tibiastats.application.query;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

abstract class WorldOnlineAnalyticsJdbcSupport extends JdbcReadModelSupport {
    protected static final int DEFAULT_LIMIT = 50;
    protected static final int MAX_LIMIT = 500;
    private static final int MAX_COMPARE_WORLDS = 20;

    protected WorldOnlineAnalyticsJdbcSupport(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    protected String requireWorld(String world) {
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

    protected List<String> parseWorlds(String worlds) {
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

    protected void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before or equal to to");
        }
    }

    protected void appendRange(StringBuilder sql, MapSqlParameterSource params, Instant from, Instant to) {
        if (from != null) {
            sql.append(" and s.scrape_time >= :from\n");
            params.addValue("from", Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" and s.scrape_time <= :to\n");
            params.addValue("to", Timestamp.from(to));
        }
    }

    protected void appendJoinRange(StringBuilder sql, MapSqlParameterSource params, Instant from, Instant to) {
        appendRange(sql, params, from, to);
        sql.append('\n');
    }

    protected int safeRankingLimit(Integer requested) {
        return safeLimit(requested, DEFAULT_LIMIT, MAX_LIMIT);
    }

    protected WorldOnlineAnalyticsService.WorldOnlineBucketView mapBucket(ResultSet rs, int rowNum) throws SQLException {
        Integer first = getNullableInteger(rs, "first_players_online");
        Integer last = getNullableInteger(rs, "last_players_online");
        return new WorldOnlineAnalyticsService.WorldOnlineBucketView(
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

    protected WorldOnlineAnalyticsService.WorldOnlineSummaryView mapSummary(ResultSet rs, int rowNum) throws SQLException {
        Integer first = getNullableInteger(rs, "first_players_online");
        Integer latest = getNullableInteger(rs, "latest_players_online");
        return new WorldOnlineAnalyticsService.WorldOnlineSummaryView(
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

    protected WorldOnlineAnalyticsService.WorldOnlineRankingView mapRanking(ResultSet rs, int rowNum) throws SQLException {
        Integer first = getNullableInteger(rs, "first_players_online");
        Integer latest = getNullableInteger(rs, "latest_players_online");
        return new WorldOnlineAnalyticsService.WorldOnlineRankingView(
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

    protected enum OnlineBucket {
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

    protected enum OnlineRankingMetric {
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
}
