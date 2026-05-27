package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.application.service.HighscoreScope;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class HighscoreScrapeStateRepository {
    private final JdbcTemplate jdbc;

    public HighscoreScrapeStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registerScopes(List<World> worlds, List<StatCategory> categories, List<Integer> vocationFilterIds) {
        String sql = """
            insert into highscore_scrape_scopes
                (world_id, world_name, category, vocation_filter_id, created_at, updated_at)
            values (?, ?, ?, ?, now(), now())
            on conflict (world_id, category, vocation_filter_id)
            do update set
                world_name = excluded.world_name,
                updated_at = now()
            """;

        List<Object[]> args = new ArrayList<>();
        for (World world : worlds) {
            for (StatCategory category : categories) {
                for (Integer vocationFilterId : vocationFilterIds) {
                    args.add(new Object[]{world.getId(), world.getName(), category.name(), vocationFilterId});
                }
            }
        }
        if (!args.isEmpty()) {
            jdbc.batchUpdate(sql, args);
        }
    }

    public List<HighscoreScope> findNextScopes(
            List<World> worlds,
            List<StatCategory> categories,
            List<Integer> vocationFilterIds,
            int limit
    ) {
        Set<Integer> allowedWorldIds = new HashSet<>();
        for (World world : worlds) {
            allowedWorldIds.add(world.getId());
        }
        Set<String> allowedCategories = new HashSet<>();
        for (StatCategory category : categories) {
            allowedCategories.add(category.name());
        }
        Set<Integer> allowedVocations = new HashSet<>(vocationFilterIds);

        List<HighscoreScope> all = jdbc.query("""
            select world_id, world_name, category, vocation_filter_id
            from highscore_scrape_scopes
            order by
                last_scraped_at asc nulls first,
                last_finished_at asc nulls first,
                world_name asc,
                category asc,
                vocation_filter_id asc
            """, this::mapScope);

        List<HighscoreScope> filtered = all.stream()
                .filter(scope -> allowedWorldIds.contains(scope.worldId()))
                .filter(scope -> allowedCategories.contains(scope.category().name()))
                .filter(scope -> allowedVocations.contains(scope.vocationFilterId()))
                .toList();

        if (limit <= 0) {
            return filtered;
        }

        return filtered.stream()
                .limit(limit)
                .toList();
    }

    public void markStarted(HighscoreScope scope) {
        jdbc.update("""
            update highscore_scrape_scopes
            set
                last_started_at = now(),
                last_status = 'RUNNING',
                last_error = null,
                updated_at = now()
            where world_id = ? and category = ? and vocation_filter_id = ?
            """, scope.worldId(), scope.category().name(), scope.vocationFilterId());
    }

    public void markFinished(HighscoreScope scope, String status, int pageCount, int rowCount, long durationMs, String error) {
        jdbc.update("""
            update highscore_scrape_scopes
            set
                last_finished_at = now(),
                last_scraped_at = now(),
                last_status = ?,
                last_page_count = ?,
                last_row_count = ?,
                last_duration_ms = ?,
                last_error = ?,
                updated_at = now()
            where world_id = ? and category = ? and vocation_filter_id = ?
            """, truncate(status, 50), pageCount, rowCount, durationMs, truncate(error, 4000),
                scope.worldId(), scope.category().name(), scope.vocationFilterId());
    }

    private HighscoreScope mapScope(ResultSet rs, int rowNum) throws SQLException {
        return new HighscoreScope(
                rs.getInt("world_id"),
                rs.getString("world_name"),
                StatCategory.valueOf(rs.getString("category")),
                rs.getInt("vocation_filter_id")
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
