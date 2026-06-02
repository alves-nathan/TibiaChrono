package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreHttpBackoffState;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.HighscoreScrapeStateRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HighscoreHttpBackoffCoordinatorTest {
    @Test
    void isActiveReturnsFalseWhenRepositoryHasNoBackoffOrExpiredBackoff() {
        FakeHighscoreScrapeStateRepository repository = new FakeHighscoreScrapeStateRepository();
        HighscoreHttpBackoffCoordinator coordinator = new HighscoreHttpBackoffCoordinator(repository);

        assertThat(coordinator.isActive("daily-exp")).isFalse();

        repository.state = new HighscoreHttpBackoffState(
                Instant.now().minusSeconds(1),
                1,
                1_000L,
                "RATE_LIMITED",
                "HTTP 403",
                Instant.now().minusSeconds(10),
                null
        );

        assertThat(coordinator.isActive("daily-exp")).isFalse();
    }

    @Test
    void isActiveReturnsTrueWhenRepositoryBackoffIsStillActive() {
        FakeHighscoreScrapeStateRepository repository = new FakeHighscoreScrapeStateRepository();
        repository.state = new HighscoreHttpBackoffState(
                Instant.now().plusSeconds(60),
                2,
                60_000L,
                "RATE_LIMITED",
                "HTTP 429",
                Instant.now(),
                null
        );

        HighscoreHttpBackoffCoordinator coordinator = new HighscoreHttpBackoffCoordinator(repository);

        assertThat(coordinator.isActive("daily-exp")).isTrue();
    }

    @Test
    void activateDoesNothingWhenForbiddenCooldownIsDisabled() {
        FakeHighscoreScrapeStateRepository repository = new FakeHighscoreScrapeStateRepository();
        HighscoreHttpBackoffCoordinator coordinator = new HighscoreHttpBackoffCoordinator(repository);
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setForbiddenInitialCooldownMs(0);

        coordinator.activate(plan, "HTTP 403");

        assertThat(repository.activateCalls).isZero();
        assertThat(repository.state).isNull();
    }

    @Test
    void activateDelegatesToRepositoryWhenNoActiveBackoffExists() {
        FakeHighscoreScrapeStateRepository repository = new FakeHighscoreScrapeStateRepository();
        HighscoreHttpBackoffCoordinator coordinator = new HighscoreHttpBackoffCoordinator(repository);
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setForbiddenInitialCooldownMs(5_000L);
        plan.setForbiddenMaxCooldownMs(20_000L);
        plan.setForbiddenCooldownMultiplier(3.0D);

        coordinator.activate(plan, "HTTP 403");

        assertThat(repository.activateCalls).isOne();
        assertThat(repository.lastInitialCooldownMs).isEqualTo(5_000L);
        assertThat(repository.lastMaxCooldownMs).isEqualTo(20_000L);
        assertThat(repository.lastMultiplier).isEqualTo(3.0D);
        assertThat(repository.lastReason).isEqualTo("HTTP 403");
        assertThat(repository.state).isNotNull();
        assertThat(repository.state.isActive(Instant.now())).isTrue();
    }

    @Test
    void activateDoesNotCreateNewBackoffWhenOneIsAlreadyActive() {
        FakeHighscoreScrapeStateRepository repository = new FakeHighscoreScrapeStateRepository();
        repository.state = new HighscoreHttpBackoffState(
                Instant.now().plusSeconds(60),
                1,
                60_000L,
                "RATE_LIMITED",
                "HTTP 403",
                Instant.now(),
                null
        );
        HighscoreHttpBackoffCoordinator coordinator = new HighscoreHttpBackoffCoordinator(repository);
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setForbiddenInitialCooldownMs(5_000L);

        coordinator.activate(plan, "HTTP 429");

        assertThat(repository.activateCalls).isZero();
        assertThat(repository.state.lastReason()).isEqualTo("HTTP 403");
    }

    @Test
    void resetAfterSuccessfulRunClearsPersistedAndInMemoryBackoffWhenNeeded() {
        FakeHighscoreScrapeStateRepository repository = new FakeHighscoreScrapeStateRepository();
        repository.state = new HighscoreHttpBackoffState(
                Instant.now().plusSeconds(60),
                1,
                60_000L,
                "RATE_LIMITED",
                "HTTP 403",
                Instant.now(),
                null
        );
        HighscoreHttpBackoffCoordinator coordinator = new HighscoreHttpBackoffCoordinator(repository);

        coordinator.resetAfterSuccessfulRun("daily-exp", 1, 0);

        assertThat(repository.resetCalls).isOne();
        assertThat(repository.state.consecutiveFailures()).isZero();
        assertThat(repository.state.cooldownUntil()).isNull();
    }

    @Test
    void resetAfterSuccessfulRunDoesNothingForCleanState() {
        FakeHighscoreScrapeStateRepository repository = new FakeHighscoreScrapeStateRepository();
        repository.state = new HighscoreHttpBackoffState(null, 0, 0L, "OK", null, null, Instant.now());
        HighscoreHttpBackoffCoordinator coordinator = new HighscoreHttpBackoffCoordinator(repository);

        coordinator.resetAfterSuccessfulRun("daily-exp", 1, 0);

        assertThat(repository.resetCalls).isZero();
    }

    @Test
    void resetManuallyClearsRepositoryStateAndReturnsFreshState() {
        FakeHighscoreScrapeStateRepository repository = new FakeHighscoreScrapeStateRepository();
        repository.state = new HighscoreHttpBackoffState(
                Instant.now().plusSeconds(60),
                3,
                60_000L,
                "RATE_LIMITED",
                "HTTP 403",
                Instant.now(),
                null
        );
        HighscoreHttpBackoffCoordinator coordinator = new HighscoreHttpBackoffCoordinator(repository);

        HighscoreHttpBackoffState state = coordinator.resetManually();

        assertThat(repository.resetCalls).isOne();
        assertThat(state.consecutiveFailures()).isZero();
        assertThat(state.cooldownUntil()).isNull();
    }

    private static final class FakeHighscoreScrapeStateRepository implements HighscoreScrapeStateRepositoryPort {
        private HighscoreHttpBackoffState state;
        private int activateCalls;
        private int resetCalls;
        private long lastInitialCooldownMs;
        private long lastMaxCooldownMs;
        private double lastMultiplier;
        private String lastReason;

        @Override
        public void registerScopes(List<World> worlds, List<StatCategory> categories, List<Integer> vocationFilterIds) {
        }

        @Override
        public List<HighscoreScope> findNextScopes(
                List<World> worlds,
                List<StatCategory> categories,
                List<Integer> vocationFilterIds,
                int limit
        ) {
            return List.of();
        }

        @Override
        public void markStarted(HighscoreScope scope) {
        }

        @Override
        public void markFinished(HighscoreScope scope, String status, int pageCount, int rowCount, long durationMs, String error) {
        }

        @Override
        public HighscoreHttpBackoffState getHttpBackoffState() {
            return state;
        }

        @Override
        public HighscoreHttpBackoffState activateHttpBackoff(long initialCooldownMs, long maxCooldownMs, double multiplier, String reason) {
            activateCalls++;
            lastInitialCooldownMs = initialCooldownMs;
            lastMaxCooldownMs = maxCooldownMs;
            lastMultiplier = multiplier;
            lastReason = reason;
            state = new HighscoreHttpBackoffState(
                    Instant.now().plusMillis(initialCooldownMs),
                    1,
                    initialCooldownMs,
                    "RATE_LIMITED",
                    reason,
                    Instant.now(),
                    null
            );
            return state;
        }

        @Override
        public void resetHttpBackoffAfterSuccess() {
            resetCalls++;
            state = new HighscoreHttpBackoffState(null, 0, 0L, "OK", null, null, Instant.now());
        }
    }
}
