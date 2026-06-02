package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import org.junit.jupiter.api.Test;

class HighscoreRequestThrottleTest {
    @Test
    void awaitBeforeRequestReturnsImmediatelyWhenAllThrottlesAreDisabled() {
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setRequestMinIntervalMs(0);
        plan.setPageDelayMs(0);
        plan.setRequestJitterMs(0);
        plan.setRequestBudgetMaxRequests(0);
        plan.setRequestBudgetWindowMs(0);

        new HighscoreRequestThrottle().awaitBeforeRequest(plan);
    }
}
