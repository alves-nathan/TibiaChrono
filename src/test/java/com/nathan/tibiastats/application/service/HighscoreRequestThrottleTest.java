package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class HighscoreRequestThrottleTest {
    @Test
    void awaitBeforeRequestReturnsImmediatelyWhenPaceDelayAndBudgetPressureAreAbsent() {
        HighscoreScrapeProperties.Plan plan = basePlan();
        plan.setRequestMinIntervalMs(0);
        plan.setPageDelayMs(0);
        plan.setRequestJitterMs(0);
        plan.setRequestBudgetMaxRequests(150_000);
        plan.setRequestBudgetWindowMs(600_000L);

        new HighscoreRequestThrottle().awaitBeforeRequest(plan);
    }

    @Test
    void awaitBeforeRequestRecordsBudgetStartAndHonorsPositiveDelaySettings() throws Exception {
        HighscoreRequestThrottle throttle = new HighscoreRequestThrottle();
        HighscoreScrapeProperties.Plan plan = basePlan();
        plan.setRequestMinIntervalMs(1);
        plan.setPageDelayMs(1);
        plan.setRequestJitterMs(0);

        throttle.awaitBeforeRequest(plan);

        assertThat(requestStarts(throttle)).hasSize(1);
        assertThat(atomicField(throttle, "nextAllowedHttpRequestAtMs").get()).isPositive();
    }

    @Test
    void pruneHighscoreRequestBudgetDropsExpiredEntriesAndKeepsRecentEntries() throws Exception {
        HighscoreRequestThrottle throttle = new HighscoreRequestThrottle();
        ArrayDeque<Long> starts = requestStarts(throttle);
        starts.addLast(9_899L);
        starts.addLast(9_900L);
        starts.addLast(9_901L);

        invokePrivate(throttle, "pruneHighscoreRequestBudget", new Class<?>[]{long.class, long.class}, 10_000L, 100L);

        assertThat(starts).containsExactly(9_901L);
    }

    @Test
    void requestBudgetHeartbeatUpdatesOnlyAfterLogCooldownExpires() throws Exception {
        HighscoreRequestThrottle throttle = new HighscoreRequestThrottle();
        HighscoreScrapeProperties.Plan plan = basePlan();
        AtomicLong lastLog = atomicField(throttle, "lastRequestBudgetLogAtMs");
        long currentLogTime = System.currentTimeMillis();
        lastLog.set(currentLogTime);

        invokePrivate(
                throttle,
                "logRequestBudgetHeartbeat",
                new Class<?>[]{HighscoreScrapeProperties.Plan.class, long.class, int.class, long.class},
                plan,
                25L,
                10,
                600_000L
        );

        assertThat(lastLog.get()).isEqualTo(currentLogTime);

        lastLog.set(0L);
        invokePrivate(
                throttle,
                "logRequestBudgetHeartbeat",
                new Class<?>[]{HighscoreScrapeProperties.Plan.class, long.class, int.class, long.class},
                plan,
                25L,
                10,
                600_000L
        );

        assertThat(lastLog.get()).isPositive();
    }

    @Test
    void sleepMsRestoresInterruptFlagAndThrowsIllegalStateExceptionWhenInterrupted() throws Exception {
        HighscoreRequestThrottle throttle = new HighscoreRequestThrottle();
        Method sleepMs = HighscoreRequestThrottle.class.getDeclaredMethod("sleepMs", long.class);
        sleepMs.setAccessible(true);

        try {
            Thread.currentThread().interrupt();
            sleepMs.invoke(throttle, 1L);
            fail("Expected interrupted sleep to throw");
        } catch (InvocationTargetException ex) {
            assertThat(ex.getCause())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Interrupted while throttling highscore requests");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private HighscoreScrapeProperties.Plan basePlan() {
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setRequestMinIntervalMs(0);
        plan.setPageDelayMs(0);
        plan.setRequestJitterMs(0);
        plan.setRequestBudgetMaxRequests(10);
        plan.setRequestBudgetWindowMs(600_000L);
        plan.setCooldownLogIntervalMs(1_000);
        return plan;
    }

    @SuppressWarnings("unchecked")
    private ArrayDeque<Long> requestStarts(HighscoreRequestThrottle throttle) throws Exception {
        Field field = HighscoreRequestThrottle.class.getDeclaredField("recentHighscoreRequestStarts");
        field.setAccessible(true);
        return (ArrayDeque<Long>) field.get(throttle);
    }

    private AtomicLong atomicField(HighscoreRequestThrottle throttle, String fieldName) throws Exception {
        Field field = HighscoreRequestThrottle.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AtomicLong) field.get(throttle);
    }

    private Object invokePrivate(HighscoreRequestThrottle throttle, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = HighscoreRequestThrottle.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(throttle, args);
    }
}
