package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.HighscoreHttpBackoffState;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HighscoreBackoffStatusMapperTest {
    private final HighscoreBackoffStatusMapper mapper = new HighscoreBackoffStatusMapper();

    @Test
    void toBackoffStatusReturnsInactiveStatusWhenStateIsNull() {
        AdminScraperService.HighscoreBackoffStatus status = mapper.toBackoffStatus(null);

        assertThat(status.active()).isFalse();
        assertThat(status.cooldownUntil()).isNull();
        assertThat(status.remainingMs()).isZero();
        assertThat(status.consecutiveFailures()).isZero();
        assertThat(status.currentCooldownMs()).isZero();
        assertThat(status.lastStatus()).isNull();
        assertThat(status.lastReason()).isNull();
        assertThat(status.lastFailureAt()).isNull();
        assertThat(status.lastSuccessAt()).isNull();
    }

    @Test
    void toBackoffStatusMapsActiveBackoffState() {
        Instant now = Instant.now();
        Instant cooldownUntil = now.plusSeconds(60);
        Instant lastFailureAt = now.minusSeconds(10);
        HighscoreHttpBackoffState state = new HighscoreHttpBackoffState(
                cooldownUntil,
                3,
                120_000L,
                "RATE_LIMITED",
                "HTTP 403",
                lastFailureAt,
                null
        );

        AdminScraperService.HighscoreBackoffStatus status = mapper.toBackoffStatus(state);

        assertThat(status.active()).isTrue();
        assertThat(status.cooldownUntil()).isEqualTo(cooldownUntil);
        assertThat(status.remainingMs()).isPositive();
        assertThat(status.consecutiveFailures()).isEqualTo(3);
        assertThat(status.currentCooldownMs()).isEqualTo(120_000L);
        assertThat(status.lastStatus()).isEqualTo("RATE_LIMITED");
        assertThat(status.lastReason()).isEqualTo("HTTP 403");
        assertThat(status.lastFailureAt()).isEqualTo(lastFailureAt);
        assertThat(status.lastSuccessAt()).isNull();
    }

    @Test
    void toBackoffStatusMapsExpiredBackoffAsInactiveWithNoRemainingTime() {
        Instant lastSuccessAt = Instant.now().minusSeconds(5);
        HighscoreHttpBackoffState state = new HighscoreHttpBackoffState(
                Instant.now().minusSeconds(1),
                0,
                0L,
                "OK",
                null,
                null,
                lastSuccessAt
        );

        AdminScraperService.HighscoreBackoffStatus status = mapper.toBackoffStatus(state);

        assertThat(status.active()).isFalse();
        assertThat(status.remainingMs()).isZero();
        assertThat(status.lastStatus()).isEqualTo("OK");
        assertThat(status.lastSuccessAt()).isEqualTo(lastSuccessAt);
    }
}
