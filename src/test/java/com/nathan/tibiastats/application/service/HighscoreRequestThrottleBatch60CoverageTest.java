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

class HighscoreRequestThrottleBatch60CoverageTest {
    @Test
    void awaitBeforeRequestExitsBudgetWaitImmediatelyWhenInterrupted() throws Exception {
        HighscoreRequestThrottle throttle = new HighscoreRequestThrottle();
        HighscoreScrapeProperties.Plan plan = basePlan();
        ArrayDeque<Long> starts = requestStarts(throttle);
        starts.addLast(System.currentTimeMillis());
        atomicField(throttle, "lastRequestBudgetLogAtMs").set(0L);

        try {
            Thread.currentThread().interrupt();

            assertThatThrownBy(() -> throttle.awaitBeforeRequest(plan))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Interrupted while throttling highscore requests");

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }

        assertThat(starts).hasSize(1);
    }

    @Test
    void throttleRequestWithJitterExecutesJitterBranchWithoutDependingOnRandomDelay() throws Throwable {
        HighscoreRequestThrottle throttle = new HighscoreRequestThrottle();
        HighscoreScrapeProperties.Plan plan = basePlan();
        plan.setRequestBudgetMaxRequests(0);
        plan.setPageDelayMs(0);
        plan.setRequestJitterMs(1);

        invokePrivateUnwrapped(
                throttle,
                "throttleRequestWithJitter",
                new Class<?>[]{HighscoreScrapeProperties.Plan.class},
                plan
        );
    }

    private static HighscoreScrapeProperties.Plan basePlan() {
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setRequestMinIntervalMs(0);
        plan.setPageDelayMs(0);
        plan.setRequestJitterMs(0);
        plan.setRequestBudgetMaxRequests(1);
        plan.setRequestBudgetWindowMs(600_000L);
        plan.setCooldownLogIntervalMs(1);
        return plan;
    }

    @SuppressWarnings("unchecked")
    private static ArrayDeque<Long> requestStarts(HighscoreRequestThrottle throttle) throws Exception {
        Field field = HighscoreRequestThrottle.class.getDeclaredField("recentHighscoreRequestStarts");
        field.setAccessible(true);
        return (ArrayDeque<Long>) field.get(throttle);
    }

    private static AtomicLong atomicField(HighscoreRequestThrottle throttle, String fieldName) throws Exception {
        Field field = HighscoreRequestThrottle.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AtomicLong) field.get(throttle);
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
