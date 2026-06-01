package com.nathan.tibiastats.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "tibiastats.scrape.highscores")
public class HighscoreScrapeProperties extends HighscorePlanSettings {
    private Map<String, Plan> plans = new LinkedHashMap<>();

    public Map<String, Plan> getPlans() {
        return plans;
    }

    public void setPlans(Map<String, Plan> plans) {
        this.plans = plans == null ? new LinkedHashMap<>() : new LinkedHashMap<>(plans);
    }

    /**
     * Legacy single-plan view. Used when the new highscores.plans map is not configured.
     */
    public Plan toLegacyPlan() {
        Plan plan = new Plan();
        plan.setEnabled(isEnabled());
        plan.setCron(getCron());
        plan.setZone(getZone());
        plan.setRunOnStartup(isRunOnStartup());
        plan.setStartupDelayMs(getStartupDelayMs());
        plan.setCategories(getCategories());
        plan.setVocations(getVocations());
        plan.setMaxPages(getMaxPages());
        plan.setPageDelayMs(getPageDelayMs());
        plan.setWorldLimit(getWorldLimit());
        plan.setScopesPerRun(getScopesPerRun());
        plan.setParallelism(getParallelism());
        plan.setPageWindowSize(getPageWindowSize());
        plan.setRequestParallelism(getRequestParallelism());
        plan.setRequestMaxAttempts(getRequestMaxAttempts());
        plan.setRetryBaseDelayMs(getRetryBaseDelayMs());
        plan.setRetryMaxDelayMs(getRetryMaxDelayMs());
        plan.setForbiddenCooldownMs(getForbiddenCooldownMs());
        plan.setForbiddenInitialCooldownMs(getForbiddenInitialCooldownMs());
        plan.setForbiddenMaxCooldownMs(getForbiddenMaxCooldownMs());
        plan.setForbiddenCooldownMultiplier(getForbiddenCooldownMultiplier());
        plan.setRequestJitterMs(getRequestJitterMs());
        plan.setRequestMinIntervalMs(getRequestMinIntervalMs());
        plan.setRequestBudgetMaxRequests(getRequestBudgetMaxRequests());
        plan.setRequestBudgetWindowMs(getRequestBudgetWindowMs());
        plan.setCooldownLogIntervalMs(getCooldownLogIntervalMs());
        plan.setProgressLogIntervalScopes(getProgressLogIntervalScopes());
        plan.setAbortRunOnForbidden(isAbortRunOnForbidden());
        return plan;
    }

    public Map<String, Plan> effectivePlans() {
        if (plans == null || plans.isEmpty()) {
            Map<String, Plan> legacy = new LinkedHashMap<>();
            legacy.put("default", toLegacyPlan());
            return legacy;
        }
        return plans;
    }

    public static class Plan extends HighscorePlanSettings {
    }
}
