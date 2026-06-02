package com.nathan.tibiastats.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HighscoreDomainSupportTest {
    @Test
    void scopeLabelUsesWorldCategoryAndVocationFilter() {
        HighscoreScope scope = new HighscoreScope(42, "Antica", StatCategory.EXPERIENCE, 0);

        assertThat(scope.label()).isEqualTo("Antica/EXPERIENCE/vocation=0");
    }

    @Test
    void backoffStateDetectsActiveCooldownAndRemainingTime() {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        HighscoreHttpBackoffState active = new HighscoreHttpBackoffState(
                now.plusSeconds(30),
                2,
                30_000L,
                "RATE_LIMITED",
                "HTTP 403",
                now.minusSeconds(1),
                null
        );

        assertThat(active.isActive(now)).isTrue();
        assertThat(active.remainingMs(now)).isEqualTo(30_000L);
    }

    @Test
    void backoffStateTreatsMissingOrExpiredCooldownAsInactive() {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        HighscoreHttpBackoffState missingCooldown = new HighscoreHttpBackoffState(
                null,
                0,
                0L,
                "OK",
                null,
                null,
                now
        );
        HighscoreHttpBackoffState expired = new HighscoreHttpBackoffState(
                now.minusMillis(1),
                1,
                1_000L,
                "RATE_LIMITED",
                "HTTP 429",
                now.minusSeconds(10),
                null
        );

        assertThat(missingCooldown.isActive(now)).isFalse();
        assertThat(missingCooldown.remainingMs(now)).isZero();
        assertThat(expired.isActive(now)).isFalse();
        assertThat(expired.remainingMs(now)).isZero();
    }
}
