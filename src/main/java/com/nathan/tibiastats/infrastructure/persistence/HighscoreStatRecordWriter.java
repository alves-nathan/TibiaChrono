package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.HighscoreStatRow;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.port.HighscoreStatRecordRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Repository
public class HighscoreStatRecordWriter implements HighscoreStatRecordRepositoryPort {
    private final HighscoreExperienceStatRecordWriter experienceWriter;
    private final HighscoreCompactStatRecordWriter compactWriter;

    public HighscoreStatRecordWriter(HighscoreExperienceStatRecordWriter experienceWriter,
                                     HighscoreCompactStatRecordWriter compactWriter) {
        this.experienceWriter = experienceWriter;
        this.compactWriter = compactWriter;
    }

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
    @Override
    @Transactional
    public int upsertBatch(List<HighscoreStatRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        List<HighscoreStatRow> experienceRows = new ArrayList<>();
        List<HighscoreStatRow> compactRows = new ArrayList<>();
        for (HighscoreStatRow row : rows) {
            if (row.category() == StatCategory.EXPERIENCE) {
                experienceRows.add(row);
            } else {
                compactRows.add(row);
            }
        }

        return experienceWriter.upsert(experienceRows) + compactWriter.upsert(compactRows);
    }
}
