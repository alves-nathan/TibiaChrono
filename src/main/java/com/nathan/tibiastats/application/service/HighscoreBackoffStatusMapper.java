package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.infrastructure.persistence.HighscoreScrapeStateRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class HighscoreBackoffStatusMapper {
    public AdminScraperService.HighscoreBackoffStatus toBackoffStatus(
            HighscoreScrapeStateRepository.HighscoreHttpBackoffState state
    ) {
        Instant now = Instant.now();
        if (state == null) {
            return new AdminScraperService.HighscoreBackoffStatus(false, null, 0, 0, 0, null, null, null, null);
        }
        return new AdminScraperService.HighscoreBackoffStatus(
                state.isActive(now),
                state.cooldownUntil(),
                state.remainingMs(now),
                state.consecutiveFailures(),
                state.currentCooldownMs(),
                state.lastStatus(),
                state.lastReason(),
                state.lastFailureAt(),
                state.lastSuccessAt()
        );
    }
}
