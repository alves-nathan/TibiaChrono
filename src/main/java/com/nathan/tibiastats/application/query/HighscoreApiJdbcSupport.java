package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

abstract class HighscoreApiJdbcSupport extends JdbcReadModelSupport {

    protected HighscoreApiJdbcSupport(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    protected HighscoreApiQueryService.ExperienceDailyView mapExperienceDaily(ResultSet rs, int rowNum) throws SQLException {
        return new HighscoreApiQueryService.ExperienceDailyView(
                rs.getObject("date", LocalDate.class),
                getNullableInteger(rs, "rank"),
                rs.getString("character_name"),
                rs.getLong("character_id"),
                rs.getString("world"),
                getNullableInteger(rs, "vocation_filter_id"),
                rs.getLong("experience"),
                getNullableInteger(rs, "level"),
                getNullableInteger(rs, "first_seen_filter"),
                toInstant(rs.getTimestamp("scraped_at"))
        );
    }

    protected HighscoreApiQueryService.ExperienceGainView mapExperienceGain(ResultSet rs, int rowNum) throws SQLException {
        return new HighscoreApiQueryService.ExperienceGainView(
                rs.getString("character_name"),
                rs.getLong("character_id"),
                rs.getString("world"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                rs.getLong("start_experience"),
                rs.getLong("end_experience"),
                rs.getLong("gain"),
                getNullableInteger(rs, "start_rank"),
                getNullableInteger(rs, "end_rank"),
                getNullableInteger(rs, "vocation_filter_id")
        );
    }

    protected HighscoreApiQueryService.CurrentHighscoreView mapCurrentHighscore(ResultSet rs, int rowNum) throws SQLException {
        int categoryCode = rs.getInt("category");
        return new HighscoreApiQueryService.CurrentHighscoreView(
                rs.getLong("id"),
                rs.getInt("rank"),
                rs.getString("character_name"),
                rs.getLong("character_id"),
                rs.getString("world"),
                categoryName(categoryCode),
                categoryCode,
                getNullableInteger(rs, "vocation_filter_id"),
                rs.getLong("value"),
                rs.getObject("first_seen_date", LocalDate.class),
                rs.getObject("last_seen_date", LocalDate.class),
                rs.getObject("last_changed_date", LocalDate.class),
                toInstant(rs.getTimestamp("scraped_at"))
        );
    }

    protected HighscoreApiQueryService.PeriodHighscoreView mapPeriodHighscore(ResultSet rs, int rowNum) throws SQLException {
        int categoryCode = rs.getInt("category");
        return new HighscoreApiQueryService.PeriodHighscoreView(
                rs.getLong("id"),
                rs.getInt("rank"),
                rs.getString("character_name"),
                rs.getLong("character_id"),
                rs.getString("world"),
                categoryName(categoryCode),
                categoryCode,
                getNullableInteger(rs, "vocation_filter_id"),
                rs.getLong("value"),
                rs.getObject("valid_from", LocalDate.class),
                rs.getObject("valid_until", LocalDate.class),
                toInstant(rs.getTimestamp("created_at"))
        );
    }

    protected String normalizeRequired(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    protected short categoryCode(StatCategory category) {
        return switch (category) {
            case ACHIEVEMENTS -> 1;
            case AXE_FIGHTING -> 2;
            case BOSS_POINTS -> 15;
            case BOUNTY_POINTS_EARNED -> 16;
            case CHARM_POINTS -> 3;
            case CLUB_FIGHTING -> 4;
            case DISTANCE_FIGHTING -> 5;
            case DROME_SCORE -> 14;
            case EXPERIENCE -> 6;
            case FISHING -> 7;
            case FIST_FIGHTING -> 8;
            case GOSHNARS_TAINT -> 9;
            case LOYALTY_POINTS -> 10;
            case MAGIC_LEVEL -> 11;
            case SHIELDING -> 12;
            case SWORD_FIGHTING -> 13;
            case WEEKLY_TASKS_COMPLETED -> 17;
        };
    }

    private String categoryName(int code) {
        return switch (code) {
            case 1 -> StatCategory.ACHIEVEMENTS.name();
            case 2 -> StatCategory.AXE_FIGHTING.name();
            case 3 -> StatCategory.CHARM_POINTS.name();
            case 4 -> StatCategory.CLUB_FIGHTING.name();
            case 5 -> StatCategory.DISTANCE_FIGHTING.name();
            case 6 -> StatCategory.EXPERIENCE.name();
            case 7 -> StatCategory.FISHING.name();
            case 8 -> StatCategory.FIST_FIGHTING.name();
            case 9 -> StatCategory.GOSHNARS_TAINT.name();
            case 10 -> StatCategory.LOYALTY_POINTS.name();
            case 11 -> StatCategory.MAGIC_LEVEL.name();
            case 12 -> StatCategory.SHIELDING.name();
            case 13 -> StatCategory.SWORD_FIGHTING.name();
            case 14 -> StatCategory.DROME_SCORE.name();
            case 15 -> StatCategory.BOSS_POINTS.name();
            case 16 -> StatCategory.BOUNTY_POINTS_EARNED.name();
            case 17 -> StatCategory.WEEKLY_TASKS_COMPLETED.name();
            default -> "UNKNOWN";
        };
    }
}
