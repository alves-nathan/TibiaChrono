package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreHttpBackoffState;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.HighscoreScrapeStateRepositoryPort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class HighscoreHttpBackoffCoordinatorTailCoverageTest {
    @Test
    void getStateDelegatesToRepository() {
        FakeHighscoreScrapeStateRepository repository = new FakeHighscoreScrapeStateRepository();
        repository.state = new HighscoreHttpBackoffState(null, 0, 0L, "OK", null, null, Instant.now());
        HighscoreHttpBackoffCoordinator coordinator = new HighscoreHttpBackoffCoordinator(repository);

        assertThat(coordinator.getState()).isSameAs(repository.state);
    }

    @Test
    void resetAfterSuccessfulRunDoesNothingWhenRepositoryReturnsNullState() {
        FakeHighscoreScrapeStateRepository repository = new FakeHighscoreScrapeStateRepository();
        HighscoreHttpBackoffCoordinator coordinator = new HighscoreHttpBackoffCoordinator(repository);

        coordinator.resetAfterSuccessfulRun("daily", 1, 0);

        assertThat(repository.resetCalls).isZero();
    }

    @Test
    void awaitCooldownHonorsShortInMemoryCooldownAndHeartbeatInterval() throws Exception {
        FakeHighscoreScrapeStateRepository repository = new FakeHighscoreScrapeStateRepository();
        HighscoreHttpBackoffCoordinator coordinator = new HighscoreHttpBackoffCoordinator(repository);
        AtomicLong cooldownUntil = atomicField(coordinator, "globalHttpCooldownUntilMs");
        AtomicLong lastLog = atomicField(coordinator, "lastCooldownLogAtMs");
        cooldownUntil.set(System.currentTimeMillis() + 5L);
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setCooldownLogIntervalMs(0);

        coordinator.awaitCooldown(plan);

        assertThat(cooldownUntil.get()).isLessThanOrEqualTo(System.currentTimeMillis());

        plan.setCooldownLogIntervalMs(1);
        invokePrivate(
                coordinator,
                "logCooldownHeartbeat",
                new Class<?>[]{HighscoreScrapeProperties.Plan.class, long.class},
                plan,
                25L
        );
        assertThat(lastLog.get()).isPositive();
    }

    @Test
    void sleepMsReturnsForNonPositiveDelaysAndRestoresInterruptFlagWhenInterrupted() throws Exception {
        FakeHighscoreScrapeStateRepository repository = new FakeHighscoreScrapeStateRepository();
        HighscoreHttpBackoffCoordinator coordinator = new HighscoreHttpBackoffCoordinator(repository);

        invokePrivate(coordinator, "sleepMs", new Class<?>[]{long.class}, 0L);

        try {
            Thread.currentThread().interrupt();
            invokePrivate(coordinator, "sleepMs", new Class<?>[]{long.class}, 1L);
            fail("Expected interrupted sleep to throw");
        } catch (InvocationTargetException ex) {
            assertThat(ex.getCause())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Interrupted while waiting during highscore HTTP backoff");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private static AtomicLong atomicField(HighscoreHttpBackoffCoordinator coordinator, String fieldName) throws Exception {
        Field field = HighscoreHttpBackoffCoordinator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AtomicLong) field.get(coordinator);
    }

    private static Object invokePrivate(
            HighscoreHttpBackoffCoordinator coordinator,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args
    ) throws Exception {
        Method method = HighscoreHttpBackoffCoordinator.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(coordinator, args);
    }

    private static final class FakeHighscoreScrapeStateRepository implements HighscoreScrapeStateRepositoryPort {
        private HighscoreHttpBackoffState state;
        private int resetCalls;

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
            return state;
        }

        @Override
        public void resetHttpBackoffAfterSuccess() {
            resetCalls++;
        }
    }
}
