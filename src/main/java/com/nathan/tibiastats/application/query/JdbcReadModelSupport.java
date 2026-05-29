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

abstract class JdbcReadModelSupport {
    protected final NamedParameterJdbcTemplate jdbc;

    protected JdbcReadModelSupport(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
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

    protected int safeLimit(int requested) {
        return safeLimit(requested, 100, 1000);
    }

    protected int safeLimit(Integer requested, int defaultLimit, int maxLimit) {
        return requested == null ? defaultLimit : safeLimit(requested, defaultLimit, maxLimit);
    }

    protected int safeLimit(int requested, int defaultLimit, int maxLimit) {
        if (requested <= 0) {
            return defaultLimit;
        }
        return Math.min(requested, maxLimit);
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

    protected Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    protected Boolean getNullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
