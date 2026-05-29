package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.HighscoreHttpBackoffState;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.HighscoreScrapeStateRepositoryPort;
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
public class HighscoreScrapeStateRepository implements HighscoreScrapeStateRepositoryPort {
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


    public HighscoreHttpBackoffState getHttpBackoffState() {
        ensureHttpBackoffRow();
        return jdbc.queryForObject("""
            select cooldown_until,
                   consecutive_failures,
                   current_cooldown_ms,
                   last_status,
                   last_reason,
                   last_failure_at,
                   last_success_at
              from highscore_http_backoff_state
             where id = 1
            """, this::mapHttpBackoffState);
    }

    public HighscoreHttpBackoffState activateHttpBackoff(long initialCooldownMs, long maxCooldownMs, double multiplier, String reason) {
        ensureHttpBackoffRow();
        HighscoreHttpBackoffState current = getHttpBackoffState();
        Instant now = Instant.now();
        if (current != null && current.isActive(now)) {
            return current;
        }

        long normalizedInitial = Math.max(0L, initialCooldownMs);
        long normalizedMax = Math.max(normalizedInitial, maxCooldownMs);
        double normalizedMultiplier = multiplier < 1.0D ? 1.0D : multiplier;
        long previousCooldown = current == null ? 0L : Math.max(0L, current.currentCooldownMs());
        long nextCooldown = previousCooldown <= 0L
                ? normalizedInitial
                : Math.min(normalizedMax, Math.max(normalizedInitial, Math.round(previousCooldown * normalizedMultiplier)));
        Instant cooldownUntil = now.plusMillis(nextCooldown);
        int consecutiveFailures = current == null ? 1 : current.consecutiveFailures() + 1;

        jdbc.update("""
            update highscore_http_backoff_state
               set cooldown_until = ?,
                   consecutive_failures = ?,
                   current_cooldown_ms = ?,
                   last_status = 'FORBIDDEN',
                   last_reason = ?,
                   last_failure_at = ?,
                   updated_at = now()
             where id = 1
            """,
                java.sql.Timestamp.from(cooldownUntil),
                consecutiveFailures,
                nextCooldown,
                truncate(reason, 4000),
                java.sql.Timestamp.from(now)
        );

        return getHttpBackoffState();
    }

    public void resetHttpBackoffAfterSuccess() {
        ensureHttpBackoffRow();
        jdbc.update("""
            update highscore_http_backoff_state
               set cooldown_until = null,
                   consecutive_failures = 0,
                   current_cooldown_ms = 0,
                   last_status = 'OK',
                   last_reason = null,
                   last_success_at = now(),
                   updated_at = now()
             where id = 1
            """);
    }

    private void ensureHttpBackoffRow() {
        jdbc.update("""
            insert into highscore_http_backoff_state (id, updated_at)
            values (1, now())
            on conflict (id) do nothing
            """);
    }

    private HighscoreHttpBackoffState mapHttpBackoffState(ResultSet rs, int rowNum) throws SQLException {
        return new HighscoreHttpBackoffState(
                toInstant(rs, "cooldown_until"),
                rs.getInt("consecutive_failures"),
                rs.getLong("current_cooldown_ms"),
                rs.getString("last_status"),
                rs.getString("last_reason"),
                toInstant(rs, "last_failure_at"),
                toInstant(rs, "last_success_at")
        );
    }

    private Instant toInstant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
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
