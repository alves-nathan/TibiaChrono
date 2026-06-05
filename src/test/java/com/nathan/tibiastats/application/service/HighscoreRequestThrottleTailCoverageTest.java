package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HighscoreRequestThrottleTailCoverageTest {
    @Test
    void awaitBeforeRequestAppliesPaceJitterAndRecordsRequestBudget() throws Exception {
        HighscoreRequestThrottle throttle = new HighscoreRequestThrottle();
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setRequestMinIntervalMs(1);
        plan.setPageDelayMs(1);
        plan.setRequestJitterMs(0);
        plan.setRequestBudgetMaxRequests(2);
        plan.setRequestBudgetWindowMs(600_000L);

        throttle.awaitBeforeRequest(plan);

        assertThat(atomicField(throttle, "nextAllowedHttpRequestAtMs").get()).isPositive();
        assertThat(requestBudget(throttle)).hasSize(1);
    }

    @Test
    void pruneHighscoreRequestBudgetRemovesExpiredEntriesAndStopsAtRecentEntry() throws Exception {
        HighscoreRequestThrottle throttle = new HighscoreRequestThrottle();
        ArrayDeque<Long> budget = requestBudget(throttle);
        long now = System.currentTimeMillis();
        budget.addLast(now - 2_000L);
        budget.addLast(now - 1_500L);
        budget.addLast(now - 100L);

        invokePrivate(throttle, "pruneHighscoreRequestBudget", new Class<?>[]{long.class, long.class}, now, 1_000L);

        assertThat(budget).containsExactly(now - 100L);
    }

    @Test
    void logRequestBudgetHeartbeatUpdatesLastLogWhenIntervalHasElapsed() throws Exception {
        HighscoreRequestThrottle throttle = new HighscoreRequestThrottle();
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setCooldownLogIntervalMs(1_000);
        AtomicLong lastLog = atomicField(throttle, "lastRequestBudgetLogAtMs");
        lastLog.set(System.currentTimeMillis() - 5_000L);

        invokePrivate(throttle, "logRequestBudgetHeartbeat", new Class<?>[]{
                HighscoreScrapeProperties.Plan.class,
                long.class,
                int.class,
                long.class
        }, plan, -10L, 1, 600_000L);

        assertThat(lastLog.get()).isGreaterThan(System.currentTimeMillis() - 1_000L);
    }

    @Test
    void sleepMsReturnsForNonPositiveDelayAndRestoresInterruptFlag() throws Throwable {
        HighscoreRequestThrottle throttle = new HighscoreRequestThrottle();

        invokePrivateUnwrapped(throttle, "sleepMs", new Class<?>[]{long.class}, 0L);

        try {
            Thread.currentThread().interrupt();

            assertThatThrownBy(() -> invokePrivateUnwrapped(
                    throttle,
                    "sleepMs",
                    new Class<?>[]{long.class},
                    1L
            ))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Interrupted while throttling highscore requests");

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private static AtomicLong atomicField(HighscoreRequestThrottle throttle, String fieldName) throws Exception {
        Field field = HighscoreRequestThrottle.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AtomicLong) field.get(throttle);
    }

    @SuppressWarnings("unchecked")
    private static ArrayDeque<Long> requestBudget(HighscoreRequestThrottle throttle) throws Exception {
        Field field = HighscoreRequestThrottle.class.getDeclaredField("recentHighscoreRequestStarts");
        field.setAccessible(true);
        return (ArrayDeque<Long>) field.get(throttle);
    }

    private static Object invokePrivate(
            HighscoreRequestThrottle throttle,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args
    ) throws Exception {
        Method method = HighscoreRequestThrottle.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(throttle, args);
    }

    private static Object invokePrivateUnwrapped(
            HighscoreRequestThrottle throttle,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args
    ) throws Throwable {
        try {
            return invokePrivate(throttle, methodName, parameterTypes, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }
}
