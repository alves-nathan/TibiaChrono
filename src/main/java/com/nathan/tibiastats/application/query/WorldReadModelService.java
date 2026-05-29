package com.nathan.tibiastats.application.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ReadModelService
@ReadModelComponent
public class WorldReadModelService extends JdbcReadModelSupport {

    public WorldReadModelService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public List<ApiQueryService.WorldView> findWorlds() {
        return jdbc.query("""
                select
                    w.id,
                    w.name,
                    w.pvp_type,
                    w.location,
                    w.online_record,
                    w.creation_date,
                    w.transfer_type,
                    w.game_world_type,
                    latest.players_online,
                    latest.scrape_time as last_scraped_at
                from worlds w
                left join lateral (
                    select s.players_online, s.scrape_time
                    from scrapes s
                    where s.world_id = w.id
                    order by s.scrape_time desc
                    limit 1
                ) latest on true
                order by w.name
                """, new MapSqlParameterSource(), this::mapWorld);
    }

    public Optional<ApiQueryService.WorldView> findWorld(String name) {
        var params = new MapSqlParameterSource("name", name);
        var result = jdbc.query("""
                select
                    w.id,
                    w.name,
                    w.pvp_type,
                    w.location,
                    w.online_record,
                    w.creation_date,
                    w.transfer_type,
                    w.game_world_type,
                    latest.players_online,
                    latest.scrape_time as last_scraped_at
                from worlds w
                left join lateral (
                    select s.players_online, s.scrape_time
                    from scrapes s
                    where s.world_id = w.id
                    order by s.scrape_time desc
                    limit 1
                ) latest on true
                where lower(w.name) = lower(:name)
                limit 1
                """, prepareParams(params), this::mapWorld);
        return result.stream().findFirst();
    }

    private ApiQueryService.WorldView mapWorld(ResultSet rs, int rowNum) throws SQLException {
        return new ApiQueryService.WorldView(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("pvp_type"),
                rs.getString("location"),
                rs.getString("online_record"),
                rs.getObject("creation_date", LocalDate.class),
                rs.getString("transfer_type"),
                rs.getString("game_world_type"),
                getNullableInteger(rs, "players_online"),
                toInstant(rs.getTimestamp("last_scraped_at"))
        );
    }
}
