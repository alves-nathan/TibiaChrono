package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.LocalDate;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class HighscoreRecordReadModelService extends HighscoreApiJdbcSupport {

    public HighscoreRecordReadModelService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public List<HighscoreApiQueryService.CurrentHighscoreView> findCurrent(String world,
                                                                           StatCategory category,
                                                                           Integer vocationFilterId,
                                                                           int limit) {
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }
        if (category == StatCategory.EXPERIENCE) {
            throw new IllegalArgumentException("Use /api/highscores/exp/* endpoints for EXPERIENCE");
        }

        var params = new MapSqlParameterSource()
                .addValue("world", normalizeRequired(world, "world"))
                .addValue("category", categoryCode(category))
                .addValue("vocationFilterId", vocationFilterId == null ? 0 : vocationFilterId)
                .addValue("limit", safeLimit(limit));

        return jdbc.query("""
                select
                    h.id,
                    h.rank,
                    active_name.name as character_name,
                    h.character_id,
                    w.name as world,
                    h.category,
                    h.vocation_filter_id,
                    h.value,
                    h.first_seen_date,
                    h.last_seen_date,
                    h.last_changed_date,
                    h.scraped_at
                from highscore_current_records h
                join worlds w on w.id = h.world_id
                left join character_names active_name
                  on active_name.character_id = h.character_id
                 and active_name.active is true
                where lower(w.name) = lower(:world)
                  and h.category = :category
                  and h.vocation_filter_id = :vocationFilterId
                order by h.rank asc
                limit :limit
                """, params, this::mapCurrentHighscore);
    }

    public List<HighscoreApiQueryService.PeriodHighscoreView> findHistory(String world,
                                                                          StatCategory category,
                                                                          String characterName,
                                                                          Integer vocationFilterId,
                                                                          LocalDate from,
                                                                          LocalDate to,
                                                                          int limit) {
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }
        if (category == StatCategory.EXPERIENCE) {
            throw new IllegalArgumentException("Use /api/highscores/exp/daily for EXPERIENCE history");
        }

        var sql = new StringBuilder("""
                select
                    p.id,
                    p.rank,
                    active_name.name as character_name,
                    p.character_id,
                    w.name as world,
                    p.category,
                    p.vocation_filter_id,
                    p.value,
                    p.valid_from,
                    p.valid_until,
                    p.created_at
                from highscore_record_periods p
                join worlds w on w.id = p.world_id
                left join character_names active_name
                  on active_name.character_id = p.character_id
                 and active_name.active is true
                where lower(w.name) = lower(:world)
                  and p.category = :category
                  and p.vocation_filter_id = :vocationFilterId
                """);
        var params = new MapSqlParameterSource()
                .addValue("world", normalizeRequired(world, "world"))
                .addValue("category", categoryCode(category))
                .addValue("vocationFilterId", vocationFilterId == null ? 0 : vocationFilterId)
                .addValue("fromDate", from)
                .addValue("toDate", to)
                .addValue("characterName", characterName == null ? null : characterName.trim())
                .addValue("limit", safeLimit(limit));

        if (from != null) {
            sql.append(" and coalesce(p.valid_until, date '9999-12-31') >= :fromDate\n");
        }
        if (to != null) {
            sql.append(" and p.valid_from <= :toDate\n");
        }
        if (characterName != null && !characterName.isBlank()) {
            sql.append("""
                    and p.character_id = (
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
                    """);
        }
        sql.append(" order by p.valid_from desc, p.rank asc limit :limit");
        return jdbc.query(sql.toString(), params, this::mapPeriodHighscore);
    }
}
