package com.nathan.tibiastats.domain.model;

import java.time.Duration;
import java.time.Instant;

public record HighscoreHttpBackoffState(
        Instant cooldownUntil,
        int consecutiveFailures,
        long currentCooldownMs,
        String lastStatus,
        String lastReason,
        Instant lastFailureAt,
        Instant lastSuccessAt
) {
    public boolean isActive(Instant now) {
        return cooldownUntil != null && cooldownUntil.isAfter(now);
    }

    public long remainingMs(Instant now) {
        if (!isActive(now)) {
            return 0L;
        }
        return Math.max(0L, Duration.between(now, cooldownUntil).toMillis());
    }
}
