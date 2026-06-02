package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class LegacyHighscoreReadModelService extends JdbcReadModelSupport {

    public LegacyHighscoreReadModelService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public List<ApiQueryService.HighscoreView> findCharacterHighscores(String characterName,
                                                                       StatCategory category,
                                                                       String world,
                                                                       Integer vocationFilterId,
                                                                       LocalDate from,
                                                                       LocalDate to,
                                                                       int limit) {
        var sql = new StringBuilder("""
                with resolved as (
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
                select
                    csr.id,
                    csr.rank,
                    active_name.name as character_name,
                    csr.character_id,
                    w.name as world,
                    csr.category,
                    csr.vocation_filter_id,
                    csr.date,
                    csr.value,
                    csr.scraped_at
                from character_statrecords csr
                join resolved r on r.character_id = csr.character_id
                join worlds w on w.id = csr.world_id
                left join character_names active_name on active_name.character_id = csr.character_id and active_name.active is true
                where 1 = 1
                """);
        var params = new MapSqlParameterSource("characterName", characterName)
                .addValue("limit", safeLimit(limit));
        appendHighscoreFilters(sql, params, category, world, vocationFilterId, from, to, null);
        sql.append(" order by csr.date desc, w.name, csr.category, csr.vocation_filter_id, csr.rank limit :limit");
        return jdbc.query(sql.toString(), prepareParams(params), this::mapHighscore);
    }

    public List<ApiQueryService.HighscoreView> findHighscores(String world,
                                                              StatCategory category,
                                                              Integer vocationFilterId,
                                                              LocalDate date,
                                                              int limit) {
        var sql = new StringBuilder("""
                select
                    csr.id,
                    csr.rank,
                    active_name.name as character_name,
                    csr.character_id,
                    w.name as world,
                    csr.category,
                    csr.vocation_filter_id,
                    csr.date,
                    csr.value,
                    csr.scraped_at
                from character_statrecords csr
                join worlds w on w.id = csr.world_id
                left join character_names active_name on active_name.character_id = csr.character_id and active_name.active is true
                where 1 = 1
                """);
        var params = new MapSqlParameterSource("limit", safeLimit(limit));
        appendHighscoreFilters(sql, params, category, world, vocationFilterId, null, null, date);
        sql.append(" order by csr.date desc, csr.rank asc limit :limit");
        return jdbc.query(sql.toString(), prepareParams(params), this::mapHighscore);
    }

    private void appendHighscoreFilters(StringBuilder sql,
                                        MapSqlParameterSource params,
                                        StatCategory category,
                                        String world,
                                        Integer vocationFilterId,
                                        LocalDate from,
                                        LocalDate to,
                                        LocalDate exactDate) {
        if (category != null) {
            sql.append(" and csr.category = :category");
            params.addValue("category", category.name());
        }
        if (world != null && !world.isBlank()) {
            sql.append(" and lower(w.name) = lower(:world)");
            params.addValue("world", world.trim());
        }
        if (vocationFilterId != null) {
            sql.append(" and csr.vocation_filter_id = :vocationFilterId");
            params.addValue("vocationFilterId", vocationFilterId);
        }
        if (from != null) {
            sql.append(" and csr.date >= :fromDate");
            params.addValue("fromDate", from);
        }
        if (to != null) {
            sql.append(" and csr.date <= :toDate");
            params.addValue("toDate", to);
        }
        if (exactDate != null) {
            sql.append(" and csr.date = :exactDate");
            params.addValue("exactDate", exactDate);
        }
    }

    private ApiQueryService.HighscoreView mapHighscore(ResultSet rs, int rowNum) throws SQLException {
        Long value = nullableLong(rs, "value");
        if (rs.wasNull()) {
            value = null;
        }
        return new ApiQueryService.HighscoreView(
                nullableLong(rs, "id"),
                getNullableInteger(rs, "rank"),
                rs.getString("character_name"),
                nullableLong(rs, "character_id"),
                rs.getString("world"),
                rs.getString("category"),
                getNullableInteger(rs, "vocation_filter_id"),
                rs.getObject("date", LocalDate.class),
                value,
                toInstant(rs.getTimestamp("scraped_at"))
        );
    }

    private static Long nullableLong(ResultSet rs, String columnLabel) throws SQLException {
        long value = rs.getLong(columnLabel);
        return rs.wasNull() ? null : value;
    }

}
