package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HighscoreFetchRetryPolicyTest {
    private final HighscoreFetchRetryPolicy policy = new HighscoreFetchRetryPolicy();

    @Test
    void retryDelayUsesExponentialBackoffCappedByMaxDelay() {
        HighscoreScrapeProperties.Plan plan = plan(100, 500, 0, 0);

        assertThat(policy.retryDelayMs(plan, 1, new RuntimeException("HTTP 500"))).isEqualTo(100L);
        assertThat(policy.retryDelayMs(plan, 2, new RuntimeException("HTTP 500"))).isEqualTo(200L);
        assertThat(policy.retryDelayMs(plan, 4, new RuntimeException("HTTP 500"))).isEqualTo(500L);
        assertThat(policy.retryDelayMs(plan, 99, new RuntimeException("HTTP 500"))).isEqualTo(500L);
    }

    @Test
    void retryDelayUsesForbiddenCooldownForRateLimitFailures() {
        HighscoreScrapeProperties.Plan plan = plan(100, 1_000, 900, 0);

        assertThat(policy.retryDelayMs(plan, 1, new RuntimeException("HTTP 403 from Tibia highscores")))
                .isEqualTo(900L);
        assertThat(policy.retryDelayMs(plan, 1, new RuntimeException("HTTP 429 too many requests")))
                .isEqualTo(900L);
    }

    @Test
    void retryDelayReturnsZeroWhenBaseDelayIsZeroAndFailureIsNotRateLimited() {
        HighscoreScrapeProperties.Plan plan = plan(0, 0, 900, 0);

        assertThat(policy.retryDelayMs(plan, 1, new RuntimeException("HTTP 500"))).isZero();
    }

    @Test
    void classifiesTransientAndPermanentFetchFailuresFromRootCauseMessage() {
        assertThat(policy.isTransientHighscoreFetchFailure(new RuntimeException("HTTP 503"))).isTrue();
        assertThat(policy.isTransientHighscoreFetchFailure(new RuntimeException("timed out"))).isTrue();
        assertThat(policy.isTransientHighscoreFetchFailure(new RuntimeException("connection reset"))).isTrue();
        assertThat(policy.isTransientHighscoreFetchFailure(new RuntimeException("invalid Tibia layout"))).isFalse();

        RuntimeException nested = new RuntimeException("wrapper", new IllegalStateException("HTTP 502 bad gateway"));
        assertThat(policy.rootMessage(nested)).isEqualTo("HTTP 502 bad gateway");
        assertThat(policy.isTransientHighscoreFetchFailure(nested)).isTrue();
    }

    @Test
    void detectsForbiddenOrRateLimitedFailuresOnly() {
        assertThat(policy.isForbiddenOrRateLimited(new RuntimeException("HTTP 403"))).isTrue();
        assertThat(policy.isForbiddenOrRateLimited(new RuntimeException("HTTP 429"))).isTrue();
        assertThat(policy.isForbiddenOrRateLimited(new RuntimeException("HTTP 500"))).isFalse();
    }

    @Test
    void rootMessageFallsBackToThrowableToStringWhenMessageIsNull() {
        RuntimeException noMessage = new RuntimeException((String) null);

        assertThat(policy.rootMessage(noMessage)).contains(RuntimeException.class.getName());
    }

    @Test
    void zeroRetrySleepReturnsImmediately() {
        HighscoreScrapeProperties.Plan plan = plan(100, 500, 900, 0);
        HighscoreScope scope = new HighscoreScope(1, "Antica", StatCategory.EXPERIENCE, 0);

        policy.sleepWithRetryHeartbeat(plan, 0, scope, 1, 1, 3);
    }

    private HighscoreScrapeProperties.Plan plan(int baseDelayMs, int maxDelayMs, long forbiddenInitialCooldownMs, int jitterMs) {
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setRetryBaseDelayMs(baseDelayMs);
        plan.setRetryMaxDelayMs(maxDelayMs);
        plan.setForbiddenInitialCooldownMs(forbiddenInitialCooldownMs);
        plan.setRequestJitterMs(jitterMs);
        plan.setCooldownLogIntervalMs(1_000);
        return plan;
    }
}
