#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${1:-$(pwd)}"
cd "$PROJECT_ROOT"

echo "Applying highscore compact storage changes..."

mkdir -p "$(dirname "src/main/java/com/nathan/tibiastats/infrastructure/persistence/HighscoreStatRecordWriter.java")"
cat > 'src/main/java/com/nathan/tibiastats/infrastructure/persistence/HighscoreStatRecordWriter.java' <<'EOF_SRC_MAIN_JAVA_COM_NATHAN_TIBIASTATS_INFRASTRUCTURE_PERSISTENCE_HIGHSCORESTATRECORDWRITER_JAVA'
package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class HighscoreStatRecordWriter {
    private final JdbcTemplate jdbc;

    public HighscoreStatRecordWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record HighscoreStatRow(
            Long characterId,
            Integer worldId,
            StatCategory category,
            int vocationFilterId,
            LocalDate date,
            long value,
            int rank,
            Instant scrapedAt
    ) {}

    private record CurrentHighscoreRow(int rank, long value) {}

    /**
     * Stores highscore data using the production storage model:
     *
     * <ul>
     *     <li>EXPERIENCE is stored as daily snapshots, deduplicated by character/date/world.</li>
     *     <li>EXPERIENCE ranks are stored separately by vocation filter.</li>
     *     <li>All other categories are stored as current state + compact historical periods.</li>
     * </ul>
     *
     * The legacy character_statrecords table is intentionally no longer written by the highscore scraper.
     */
    @Transactional
    public int upsertBatch(List<HighscoreStatRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        List<HighscoreStatRow> expRows = new ArrayList<>();
        List<HighscoreStatRow> compactRows = new ArrayList<>();
        for (HighscoreStatRow row : rows) {
            if (row.category() == StatCategory.EXPERIENCE) {
                expRows.add(row);
            } else {
                compactRows.add(row);
            }
        }

        int total = 0;
        total += upsertExperienceRows(expRows);
        total += upsertCompactRows(compactRows);
        return total;
    }

    private int upsertExperienceRows(List<HighscoreStatRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }

        String expDailySql = """
            insert into highscore_exp_daily
                (date, character_id, world_id, experience, first_seen_filter, scraped_at)
            values (?, ?, ?, ?, ?, ?)
            on conflict (date, character_id, world_id)
            do update set
                experience = excluded.experience,
                scraped_at = greatest(highscore_exp_daily.scraped_at, excluded.scraped_at),
                first_seen_filter = coalesce(highscore_exp_daily.first_seen_filter, excluded.first_seen_filter)
            """;

        String expRankSql = """
            insert into highscore_exp_rank_daily
                (date, character_id, world_id, vocation_filter_id, rank, scraped_at)
            values (?, ?, ?, ?, ?, ?)
            on conflict (date, character_id, world_id, vocation_filter_id)
            do update set
                rank = excluded.rank,
                scraped_at = greatest(highscore_exp_rank_daily.scraped_at, excluded.scraped_at)
            """;

        int[][] dailyAffected = jdbc.batchUpdate(expDailySql, rows, Math.min(rows.size(), 500), (ps, row) -> {
            ps.setDate(1, Date.valueOf(row.date()));
            ps.setLong(2, row.characterId());
            ps.setInt(3, row.worldId());
            ps.setLong(4, row.value());
            ps.setInt(5, row.vocationFilterId());
            ps.setTimestamp(6, Timestamp.from(row.scrapedAt()));
        });

        jdbc.batchUpdate(expRankSql, rows, Math.min(rows.size(), 500), (ps, row) -> {
            ps.setDate(1, Date.valueOf(row.date()));
            ps.setLong(2, row.characterId());
            ps.setInt(3, row.worldId());
            ps.setInt(4, row.vocationFilterId());
            ps.setInt(5, row.rank());
            ps.setTimestamp(6, Timestamp.from(row.scrapedAt()));
        });

        return countAffected(dailyAffected);
    }

    private int upsertCompactRows(List<HighscoreStatRow> rows) {
        int total = 0;
        for (HighscoreStatRow row : rows) {
            total += upsertCompactRow(row);
        }
        return total;
    }

    private int upsertCompactRow(HighscoreStatRow row) {
        short categoryCode = categoryCode(row.category());

        int inserted = jdbc.update("""
            insert into highscore_current_records
                (character_id, world_id, category, vocation_filter_id, rank, value,
                 first_seen_date, last_seen_date, last_changed_date, scraped_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (character_id, world_id, category, vocation_filter_id) do nothing
            """,
                row.characterId(),
                row.worldId(),
                categoryCode,
                row.vocationFilterId(),
                row.rank(),
                row.value(),
                Date.valueOf(row.date()),
                Date.valueOf(row.date()),
                Date.valueOf(row.date()),
                Timestamp.from(row.scrapedAt())
        );

        if (inserted > 0) {
            insertPeriod(row, categoryCode);
            return 1;
        }

        CurrentHighscoreRow current = findCurrentForUpdate(row, categoryCode);
        if (current == null) {
            // Defensive fallback in case the row disappeared between insert and lookup.
            insertPeriod(row, categoryCode);
            return 1;
        }

        if (current.rank() == row.rank() && current.value() == row.value()) {
            jdbc.update("""
                update highscore_current_records
                set last_seen_date = greatest(last_seen_date, ?),
                    scraped_at = greatest(scraped_at, ?),
                    updated_at = now()
                where character_id = ?
                  and world_id = ?
                  and category = ?
                  and vocation_filter_id = ?
                """,
                    Date.valueOf(row.date()),
                    Timestamp.from(row.scrapedAt()),
                    row.characterId(),
                    row.worldId(),
                    categoryCode,
                    row.vocationFilterId()
            );
            return 1;
        }

        LocalDate openValidFrom = findOpenPeriodValidFrom(row, categoryCode);
        if (openValidFrom != null && !openValidFrom.isBefore(row.date())) {
            // Same-day correction/rerun: keep one period for the day and update it instead of creating
            // a zero-length period. The historical model is date-based, not timestamp-based.
            updateOpenPeriod(row, categoryCode);
        } else {
            closeOpenPeriod(row, categoryCode);
            insertPeriod(row, categoryCode);
        }
        jdbc.update("""
            update highscore_current_records
            set rank = ?,
                value = ?,
                last_seen_date = greatest(last_seen_date, ?),
                last_changed_date = ?,
                scraped_at = greatest(scraped_at, ?),
                updated_at = now()
            where character_id = ?
              and world_id = ?
              and category = ?
              and vocation_filter_id = ?
            """,
                row.rank(),
                row.value(),
                Date.valueOf(row.date()),
                Date.valueOf(row.date()),
                Timestamp.from(row.scrapedAt()),
                row.characterId(),
                row.worldId(),
                categoryCode,
                row.vocationFilterId()
        );
        return 1;
    }

    private CurrentHighscoreRow findCurrentForUpdate(HighscoreStatRow row, short categoryCode) {
        try {
            return jdbc.queryForObject("""
                select rank, value
                from highscore_current_records
                where character_id = ?
                  and world_id = ?
                  and category = ?
                  and vocation_filter_id = ?
                for update
                """,
                    (rs, ignored) -> new CurrentHighscoreRow(rs.getInt("rank"), rs.getLong("value")),
                    row.characterId(),
                    row.worldId(),
                    categoryCode,
                    row.vocationFilterId()
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private LocalDate findOpenPeriodValidFrom(HighscoreStatRow row, short categoryCode) {
        try {
            return jdbc.queryForObject("""
                select valid_from
                from highscore_record_periods
                where character_id = ?
                  and world_id = ?
                  and category = ?
                  and vocation_filter_id = ?
                  and valid_until is null
                """,
                    (rs, ignored) -> rs.getDate("valid_from").toLocalDate(),
                    row.characterId(),
                    row.worldId(),
                    categoryCode,
                    row.vocationFilterId()
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private void updateOpenPeriod(HighscoreStatRow row, short categoryCode) {
        jdbc.update("""
            update highscore_record_periods
            set rank = ?,
                value = ?
            where character_id = ?
              and world_id = ?
              and category = ?
              and vocation_filter_id = ?
              and valid_until is null
            """,
                row.rank(),
                row.value(),
                row.characterId(),
                row.worldId(),
                categoryCode,
                row.vocationFilterId()
        );
    }

    private void closeOpenPeriod(HighscoreStatRow row, short categoryCode) {
        jdbc.update("""
            update highscore_record_periods
            set valid_until = ?
            where character_id = ?
              and world_id = ?
              and category = ?
              and vocation_filter_id = ?
              and valid_until is null
            """,
                Date.valueOf(row.date()),
                row.characterId(),
                row.worldId(),
                categoryCode,
                row.vocationFilterId()
        );
    }

    private void insertPeriod(HighscoreStatRow row, short categoryCode) {
        jdbc.update("""
            insert into highscore_record_periods
                (character_id, world_id, category, vocation_filter_id, rank, value, valid_from, valid_until, created_at)
            values (?, ?, ?, ?, ?, ?, ?, null, ?)
            on conflict do nothing
            """,
                row.characterId(),
                row.worldId(),
                categoryCode,
                row.vocationFilterId(),
                row.rank(),
                row.value(),
                Date.valueOf(row.date()),
                Timestamp.from(row.scrapedAt())
        );
    }

    private int countAffected(int[][] affected) {
        int total = 0;
        for (int[] batch : affected) {
            for (int count : batch) {
                if (count > 0) {
                    total += count;
                }
            }
        }
        return total;
    }

    private short categoryCode(StatCategory category) {
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
}
EOF_SRC_MAIN_JAVA_COM_NATHAN_TIBIASTATS_INFRASTRUCTURE_PERSISTENCE_HIGHSCORESTATRECORDWRITER_JAVA

mkdir -p "$(dirname "src/main/resources/db/migration/V5__highscore_compact_storage.sql")"
cat > 'src/main/resources/db/migration/V5__highscore_compact_storage.sql' <<'EOF_SRC_MAIN_RESOURCES_DB_MIGRATION_V5__HIGHSCORE_COMPACT_STORAGE_SQL'
-- Production highscore storage model.
--
-- EXP is intentionally stored as a daily snapshot because EXP deltas are the core analytics use case.
-- The same character may appear in the overall EXP ranking and in its vocation ranking on the same day,
-- so highscore_exp_daily is deduplicated by (date, character_id, world_id).
--
-- Non-EXP categories are stored as current state + periods to avoid repeating identical snapshots forever.

CREATE TABLE IF NOT EXISTS highscore_exp_daily (
    date DATE NOT NULL,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    world_id INTEGER NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    level INTEGER,
    experience BIGINT NOT NULL,
    vocation_id SMALLINT,
    guild_id BIGINT REFERENCES guilds(id) ON DELETE SET NULL,
    first_seen_filter SMALLINT,
    scraped_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (date, character_id, world_id)
);

CREATE INDEX IF NOT EXISTS idx_highscore_exp_daily_world_date_exp
ON highscore_exp_daily(world_id, date, experience DESC);

CREATE INDEX IF NOT EXISTS idx_highscore_exp_daily_character_world_date
ON highscore_exp_daily(character_id, world_id, date);

CREATE INDEX IF NOT EXISTS idx_highscore_exp_daily_guild_date_exp
ON highscore_exp_daily(guild_id, date, experience DESC)
WHERE guild_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS highscore_exp_rank_daily (
    date DATE NOT NULL,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    world_id INTEGER NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    vocation_filter_id SMALLINT NOT NULL DEFAULT 0,
    rank INTEGER NOT NULL,
    scraped_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (date, character_id, world_id, vocation_filter_id)
);

CREATE INDEX IF NOT EXISTS idx_highscore_exp_rank_daily_scope_rank
ON highscore_exp_rank_daily(world_id, vocation_filter_id, date, rank);

CREATE TABLE IF NOT EXISTS highscore_current_records (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    world_id INTEGER NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    category SMALLINT NOT NULL,
    vocation_filter_id SMALLINT NOT NULL DEFAULT 0,
    rank INTEGER NOT NULL,
    value BIGINT NOT NULL,
    first_seen_date DATE NOT NULL,
    last_seen_date DATE NOT NULL,
    last_changed_date DATE NOT NULL,
    scraped_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_highscore_current_records_scope UNIQUE(character_id, world_id, category, vocation_filter_id),
    CONSTRAINT chk_highscore_current_records_category CHECK (category BETWEEN 1 AND 17)
);

CREATE INDEX IF NOT EXISTS idx_highscore_current_records_scope_rank
ON highscore_current_records(world_id, category, vocation_filter_id, rank);

CREATE INDEX IF NOT EXISTS idx_highscore_current_records_character_scope
ON highscore_current_records(character_id, world_id, category, vocation_filter_id);

CREATE TABLE IF NOT EXISTS highscore_record_periods (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    world_id INTEGER NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    category SMALLINT NOT NULL,
    vocation_filter_id SMALLINT NOT NULL DEFAULT 0,
    rank INTEGER NOT NULL,
    value BIGINT NOT NULL,
    valid_from DATE NOT NULL,
    valid_until DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_highscore_record_periods_category CHECK (category BETWEEN 1 AND 17),
    CONSTRAINT chk_highscore_record_periods_valid_range CHECK (valid_until IS NULL OR valid_until > valid_from)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_highscore_record_periods_open
ON highscore_record_periods(character_id, world_id, category, vocation_filter_id)
WHERE valid_until IS NULL;

CREATE INDEX IF NOT EXISTS idx_highscore_record_periods_character_date
ON highscore_record_periods(character_id, world_id, category, vocation_filter_id, valid_from, valid_until);

CREATE INDEX IF NOT EXISTS idx_highscore_record_periods_scope_date_rank
ON highscore_record_periods(world_id, category, vocation_filter_id, valid_from, valid_until, rank);

-- Optional cleanup helper for future maintenance. It is intentionally not run automatically here:
-- character_statrecords remains as legacy/staging data until you decide to compact or purge it.
EOF_SRC_MAIN_RESOURCES_DB_MIGRATION_V5__HIGHSCORE_COMPACT_STORAGE_SQL

echo "Done. Run: make up-dev"
