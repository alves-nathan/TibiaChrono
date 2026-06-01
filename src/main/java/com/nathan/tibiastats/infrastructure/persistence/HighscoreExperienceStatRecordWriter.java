package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.HighscoreStatRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

@Component
public class HighscoreExperienceStatRecordWriter {
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;

    public HighscoreExperienceStatRecordWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    int upsert(List<HighscoreStatRow> rows) {
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

        int[][] dailyAffected = jdbc.batchUpdate(expDailySql, rows, Math.min(rows.size(), BATCH_SIZE), (ps, row) -> {
            ps.setDate(1, Date.valueOf(row.date()));
            ps.setLong(2, row.characterId());
            ps.setInt(3, row.worldId());
            ps.setLong(4, row.value());
            ps.setInt(5, row.vocationFilterId());
            ps.setTimestamp(6, Timestamp.from(row.scrapedAt()));
        });

        jdbc.batchUpdate(expRankSql, rows, Math.min(rows.size(), BATCH_SIZE), (ps, row) -> {
            ps.setDate(1, Date.valueOf(row.date()));
            ps.setLong(2, row.characterId());
            ps.setInt(3, row.worldId());
            ps.setInt(4, row.vocationFilterId());
            ps.setInt(5, row.rank());
            ps.setTimestamp(6, Timestamp.from(row.scrapedAt()));
        });

        return countAffected(dailyAffected);
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
}
