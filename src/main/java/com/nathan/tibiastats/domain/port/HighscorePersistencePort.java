package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.StatCategory;

import java.time.Instant;
import java.time.LocalDate;

public interface HighscorePersistencePort {
    void upsertDailyStat(Long characterId,
                         Integer worldId,
                         StatCategory category,
                         int vocationFilterId,
                         LocalDate date,
                         long value,
                         int rank,
                         Instant scrapedAt);
}
