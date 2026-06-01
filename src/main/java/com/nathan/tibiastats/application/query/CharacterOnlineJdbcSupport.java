package com.nathan.tibiastats.application.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

abstract class CharacterOnlineJdbcSupport extends JdbcReadModelSupport {

    protected CharacterOnlineJdbcSupport(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    protected MapSqlParameterSource baseCharacterOnlineParams(String characterName) {
        return new MapSqlParameterSource("characterName", characterName);
    }

    protected void appendCharacterOnlineFilters(StringBuilder sql,
                                                MapSqlParameterSource params,
                                                String world,
                                                Instant from,
                                                Instant to) {
        if (world != null && !world.isBlank()) {
            sql.append(" and lower(w.name) = lower(:onlineWorld)");
            params.addValue("onlineWorld", world.trim());
        }
        if (from != null) {
            sql.append(" and s.scrape_time >= :onlineFrom");
            params.addValue("onlineFrom", from);
        }
        if (to != null) {
            sql.append(" and s.scrape_time <= :onlineTo");
            params.addValue("onlineTo", to);
        }
    }

    protected ApiQueryService.CharacterOnlinePointView mapCharacterOnlinePoint(ResultSet rs, int rowNum) throws SQLException {
        return new ApiQueryService.CharacterOnlinePointView(
                rs.getLong("character_id"),
                rs.getString("character_name"),
                rs.getLong("scrape_id"),
                rs.getString("world"),
                toInstant(rs.getTimestamp("scrape_time")),
                getNullableInteger(rs, "players_online")
        );
    }

    protected ApiQueryService.CharacterOnlineSessionView mapCharacterOnlineSession(ResultSet rs, int rowNum) throws SQLException {
        return new ApiQueryService.CharacterOnlineSessionView(
                rs.getLong("character_id"),
                rs.getString("character_name"),
                rs.getString("world"),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("ended_at")),
                getNullableLong(rs, "observed_minutes"),
                getNullableInteger(rs, "samples")
        );
    }

    protected ApiQueryService.CharacterOnlineWorldSummaryView mapCharacterOnlineWorldSummary(ResultSet rs, int rowNum) throws SQLException {
        return new ApiQueryService.CharacterOnlineWorldSummaryView(
                rs.getLong("character_id"),
                rs.getString("character_name"),
                rs.getString("world"),
                getNullableInteger(rs, "appearances"),
                getNullableInteger(rs, "sessions"),
                getNullableLong(rs, "observed_minutes"),
                toInstant(rs.getTimestamp("first_seen_at")),
                toInstant(rs.getTimestamp("last_seen_at"))
        );
    }
}
