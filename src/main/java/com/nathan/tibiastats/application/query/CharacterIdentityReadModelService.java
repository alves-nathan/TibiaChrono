package com.nathan.tibiastats.application.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@ReadModelService
@ReadModelComponent
public class CharacterIdentityReadModelService extends JdbcReadModelSupport {

    public CharacterIdentityReadModelService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public Optional<ApiQueryService.CharacterView> findCharacter(String name) {
        var params = new MapSqlParameterSource("name", name);
        var result = jdbc.query("""
                with resolved as (
                    select lookup.character_id
                    from character_names lookup
                    where lower(lookup.name) = lower(:name)
                      and (
                          lookup.active is true
                          or lookup.inactive_date >= now() - interval '6 months'
                      )
                    order by lookup.active desc, lookup.inactive_date desc nulls last
                    limit 1
                )
                select
                    c.id,
                    active_name.name as active_name,
                    c.level,
                    c.sex,
                    v.name as vocation,
                    v.promotion_name as vocation_promotion_name,
                    c.achievement_points,
                    c.residence,
                    c.last_login,
                    c.acc_status,
                    c.creation_date,
                    c.details_last_scraped_at,
                    c.details_last_scrape_status
                from resolved r
                join characters c on c.id = r.character_id
                left join character_names active_name on active_name.character_id = c.id and active_name.active is true
                left join vocations v on v.id = c.vocation_id
                limit 1
                """, prepareParams(params), this::mapCharacter);
        return result.stream().findFirst();
    }

    public List<ApiQueryService.CharacterNameView> findCharacterNames(String name) {
        var character = findCharacter(name);
        if (character.isEmpty()) {
            return List.of();
        }
        return findCharacterNames(character.get().id());
    }

    public List<ApiQueryService.CharacterNameView> findCharacterNames(Long characterId) {
        var params = new MapSqlParameterSource("characterId", characterId);
        return jdbc.query("""
                select id, character_id, name, active, inactive_date
                from character_names
                where character_id = :characterId
                order by active desc, inactive_date desc nulls first, name
                """, prepareParams(params), this::mapCharacterName);
    }

    private ApiQueryService.CharacterView mapCharacter(ResultSet rs, int rowNum) throws SQLException {
        return new ApiQueryService.CharacterView(
                rs.getLong("id"),
                rs.getString("active_name"),
                getNullableInteger(rs, "level"),
                rs.getString("sex"),
                rs.getString("vocation"),
                rs.getString("vocation_promotion_name"),
                getNullableInteger(rs, "achievement_points"),
                rs.getString("residence"),
                rs.getObject("last_login", OffsetDateTime.class),
                rs.getString("acc_status"),
                toInstant(rs.getTimestamp("creation_date")),
                toInstant(rs.getTimestamp("details_last_scraped_at")),
                rs.getString("details_last_scrape_status")
        );
    }

    private ApiQueryService.CharacterNameView mapCharacterName(ResultSet rs, int rowNum) throws SQLException {
        return new ApiQueryService.CharacterNameView(
                rs.getLong("id"),
                rs.getLong("character_id"),
                rs.getString("name"),
                rs.getBoolean("active"),
                toInstant(rs.getTimestamp("inactive_date"))
        );
    }
}
