package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.HighscoreStatRow;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

@Component
public class HighscoreCompactStatRecordWriter {
    private final JdbcTemplate jdbc;
    private final HighscoreStatCategoryCodeMapper categoryCodes;

    public HighscoreCompactStatRecordWriter(JdbcTemplate jdbc, HighscoreStatCategoryCodeMapper categoryCodes) {
        this.jdbc = jdbc;
        this.categoryCodes = categoryCodes;
    }

    int upsert(List<HighscoreStatRow> rows) {
        int total = 0;
        for (HighscoreStatRow row : rows) {
            total += upsertCompactRow(row);
        }
        return total;
    }

    private int upsertCompactRow(HighscoreStatRow row) {
        short categoryCode = categoryCodes.code(row.category());

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
            touchUnchangedCurrentRecord(row, categoryCode);
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
        updateChangedCurrentRecord(row, categoryCode);
        return 1;
    }

    private void touchUnchangedCurrentRecord(HighscoreStatRow row, short categoryCode) {
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
    }

    private void updateChangedCurrentRecord(HighscoreStatRow row, short categoryCode) {
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

    private record CurrentHighscoreRow(int rank, long value) {}
}
