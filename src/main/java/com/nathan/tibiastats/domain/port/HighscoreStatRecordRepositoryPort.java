package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.HighscoreStatRow;

import java.util.List;

public interface HighscoreStatRecordRepositoryPort {
    int upsertBatch(List<HighscoreStatRow> rows);
}
