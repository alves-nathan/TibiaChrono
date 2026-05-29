package com.nathan.tibiastats.domain.model;

import java.time.Instant;
import java.time.LocalDate;

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
