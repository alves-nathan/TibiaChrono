package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.HighscoreScope;
import com.nathan.tibiastats.domain.port.HighscoreScrapeStateRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class HighscoreScopeStateService {
    private final HighscoreScrapeStateRepositoryPort stateRepository;

    public HighscoreScopeStateService(HighscoreScrapeStateRepositoryPort stateRepository) {
        this.stateRepository = stateRepository;
    }

    public void markStarted(HighscoreScope scope) {
        stateRepository.markStarted(scope);
    }

    public void markFinished(HighscoreScope scope, String status, int pageCount, int rowCount, long durationMs, String error) {
        stateRepository.markFinished(scope, status, pageCount, rowCount, durationMs, error);
    }
}
