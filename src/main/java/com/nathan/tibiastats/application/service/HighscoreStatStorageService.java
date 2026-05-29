package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.HighscoreStatRow;
import com.nathan.tibiastats.domain.port.HighscoreStatRecordRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HighscoreStatStorageService {
    private final HighscoreStatRecordRepositoryPort statRecordRepository;

    public HighscoreStatStorageService(HighscoreStatRecordRepositoryPort statRecordRepository) {
        this.statRecordRepository = statRecordRepository;
    }

    public int upsertBatch(List<HighscoreStatRow> rows) {
        return statRecordRepository.upsertBatch(rows);
    }
}
