package com.nathan.tibiastats.application.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

abstract class CharacterTimelineJdbcSupport {
    protected final NamedParameterJdbcTemplate jdbc;

    protected CharacterTimelineJdbcSupport(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    protected MapSqlParameterSource baseParams(String characterName, Instant from, Instant to, Integer limit, int defaultLimit) {
        return new MapSqlParameterSource()
                .addValue("characterName", normalizeRequired(characterName, "characterName"))
                .addValue("fromInstant", from == null ? null : Timestamp.from(from), Types.TIMESTAMP)
                .addValue("toInstant", to == null ? null : Timestamp.from(to), Types.TIMESTAMP)
                .addValue("limit", safeLimit(limit == null ? defaultLimit : limit, defaultLimit));
    }

    protected MapSqlParameterSource prepareParams(MapSqlParameterSource params) {
        if (params == null) {
            return new MapSqlParameterSource();
        }
        for (var entry : new ArrayList<>(params.getValues().entrySet())) {
            if (entry.getValue() instanceof Instant instant) {
                params.addValue(entry.getKey(), Timestamp.from(instant), Types.TIMESTAMP);
            }
        }
        return params;
    }

    protected CharacterTimelineService.CharacterTimelineEvent mapTimelineEvent(ResultSet rs, int rowNum) throws SQLException {
        return new CharacterTimelineService.CharacterTimelineEvent(
                rs.getLong("character_id"),
                rs.getString("character_name"),
                rs.getString("event_type"),
                toInstant(rs.getTimestamp("occurred_at")),
                rs.getString("world"),
                rs.getString("guild_name"),
                rs.getString("category"),
                getNullableInteger(rs, "rank"),
                getNullableLong(rs, "value"),
                rs.getString("title"),
                rs.getString("description"),
                Map.of()
        );
    }

    protected int safeLimit(int requested, int defaultLimit) {
        if (requested <= 0) {
            return defaultLimit;
        }
        return Math.min(requested, 1000);
    }

    protected String normalizeRequired(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    protected Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    protected Integer getNullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    protected Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    protected Boolean getNullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
