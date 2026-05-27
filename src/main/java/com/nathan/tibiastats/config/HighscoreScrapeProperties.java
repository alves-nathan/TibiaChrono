package com.nathan.tibiastats.config;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "tibiastats.scrape.highscores")
public class HighscoreScrapeProperties {
    private static final Logger log = LoggerFactory.getLogger(HighscoreScrapeProperties.class);

    private boolean enabled = true;
    private String cron = "0 0 7 * * *";
    private String zone = "America/Sao_Paulo";
    private boolean runOnStartup = false;
    private long startupDelayMs = 0;
    private String categories = "EXPERIENCE";
    private String vocations = "0";
    private int maxPages = 100;
    private int pageDelayMs = 0;
    private int worldLimit = 0;
    private int scopesPerRun = 0;
    private int parallelism = 4;
    private int pageWindowSize = 1;
    private int requestParallelism = 4;
    private int requestMaxAttempts = 1;
    private int retryBaseDelayMs = 5000;
    private int retryMaxDelayMs = 300000;
    private int forbiddenCooldownMs = 14400000;
    private int requestJitterMs = 300;
    private int requestMinIntervalMs = 750;
    private int cooldownLogIntervalMs = 30000;
    private int progressLogIntervalScopes = 10;
    private boolean abortRunOnForbidden = true;
    private Map<String, Plan> plans = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getZone() {
        return normalizedZone(zone);
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public boolean isRunOnStartup() {
        return runOnStartup;
    }

    public void setRunOnStartup(boolean runOnStartup) {
        this.runOnStartup = runOnStartup;
    }

    public long getStartupDelayMs() {
        return Math.max(0, startupDelayMs);
    }

    public void setStartupDelayMs(long startupDelayMs) {
        this.startupDelayMs = startupDelayMs;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }

    public String getVocations() {
        return vocations;
    }

    public void setVocations(String vocations) {
        this.vocations = vocations;
    }

    public int getMaxPages() {
        return Math.max(1, maxPages);
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public int getPageDelayMs() {
        return Math.max(0, pageDelayMs);
    }

    public void setPageDelayMs(int pageDelayMs) {
        this.pageDelayMs = pageDelayMs;
    }

    public int getWorldLimit() {
        return Math.max(0, worldLimit);
    }

    public void setWorldLimit(int worldLimit) {
        this.worldLimit = worldLimit;
    }

    /**
     * Maximum number of highscore scopes processed in one scheduled run.
     * A value of 0 means: process every configured scope in the same run.
     */
    public int getScopesPerRun() {
        return Math.max(0, scopesPerRun);
    }

    public boolean isAllScopesPerRun() {
        return getScopesPerRun() == 0;
    }

    public void setScopesPerRun(int scopesPerRun) {
        this.scopesPerRun = scopesPerRun;
    }

    public int getParallelism() {
        return Math.max(1, parallelism);
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    public int getPageWindowSize() {
        return Math.max(1, pageWindowSize);
    }

    public void setPageWindowSize(int pageWindowSize) {
        this.pageWindowSize = pageWindowSize;
    }

    public int getRequestParallelism() {
        return Math.max(1, requestParallelism);
    }

    public void setRequestParallelism(int requestParallelism) {
        this.requestParallelism = requestParallelism;
    }

    public int getRequestMaxAttempts() {
        return Math.max(1, requestMaxAttempts);
    }

    public void setRequestMaxAttempts(int requestMaxAttempts) {
        this.requestMaxAttempts = requestMaxAttempts;
    }

    public int getRetryBaseDelayMs() {
        return Math.max(0, retryBaseDelayMs);
    }

    public void setRetryBaseDelayMs(int retryBaseDelayMs) {
        this.retryBaseDelayMs = retryBaseDelayMs;
    }

    public int getRetryMaxDelayMs() {
        return Math.max(getRetryBaseDelayMs(), retryMaxDelayMs);
    }

    public void setRetryMaxDelayMs(int retryMaxDelayMs) {
        this.retryMaxDelayMs = retryMaxDelayMs;
    }

    public int getForbiddenCooldownMs() {
        return Math.max(0, forbiddenCooldownMs);
    }

    public void setForbiddenCooldownMs(int forbiddenCooldownMs) {
        this.forbiddenCooldownMs = forbiddenCooldownMs;
    }

    public int getRequestJitterMs() {
        return Math.max(0, requestJitterMs);
    }

    public void setRequestJitterMs(int requestJitterMs) {
        this.requestJitterMs = requestJitterMs;
    }

    public int getRequestMinIntervalMs() {
        return Math.max(0, requestMinIntervalMs);
    }

    public void setRequestMinIntervalMs(int requestMinIntervalMs) {
        this.requestMinIntervalMs = requestMinIntervalMs;
    }

    public int getCooldownLogIntervalMs() {
        return Math.max(1000, cooldownLogIntervalMs);
    }

    public void setCooldownLogIntervalMs(int cooldownLogIntervalMs) {
        this.cooldownLogIntervalMs = cooldownLogIntervalMs;
    }

    public int getProgressLogIntervalScopes() {
        return Math.max(1, progressLogIntervalScopes);
    }

    public void setProgressLogIntervalScopes(int progressLogIntervalScopes) {
        this.progressLogIntervalScopes = progressLogIntervalScopes;
    }

    public boolean isAbortRunOnForbidden() {
        return abortRunOnForbidden;
    }

    public void setAbortRunOnForbidden(boolean abortRunOnForbidden) {
        this.abortRunOnForbidden = abortRunOnForbidden;
    }

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
        plan.setEnabled(enabled);
        plan.setCron(cron);
        plan.setZone(zone);
        plan.setRunOnStartup(runOnStartup);
        plan.setStartupDelayMs(startupDelayMs);
        plan.setCategories(categories);
        plan.setVocations(vocations);
        plan.setMaxPages(maxPages);
        plan.setPageDelayMs(pageDelayMs);
        plan.setWorldLimit(worldLimit);
        plan.setScopesPerRun(scopesPerRun);
        plan.setParallelism(parallelism);
        plan.setPageWindowSize(pageWindowSize);
        plan.setRequestParallelism(requestParallelism);
        plan.setRequestMaxAttempts(requestMaxAttempts);
        plan.setRetryBaseDelayMs(retryBaseDelayMs);
        plan.setRetryMaxDelayMs(retryMaxDelayMs);
        plan.setForbiddenCooldownMs(forbiddenCooldownMs);
        plan.setRequestJitterMs(requestJitterMs);
        plan.setRequestMinIntervalMs(requestMinIntervalMs);
        plan.setCooldownLogIntervalMs(cooldownLogIntervalMs);
        plan.setProgressLogIntervalScopes(progressLogIntervalScopes);
        plan.setAbortRunOnForbidden(abortRunOnForbidden);
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

    public List<StatCategory> categoryList() {
        return parseCategories(categories);
    }

    public List<Integer> vocationFilterIds() {
        return parseVocations(vocations);
    }

    private static List<StatCategory> parseCategories(String value) {
        List<StatCategory> parsed = new ArrayList<>();
        for (String token : splitCsv(value)) {
            try {
                parsed.add(StatCategory.valueOf(token));
            } catch (IllegalArgumentException ex) {
                log.warn("[HIGHSCORE_SCRAPER] Ignoring invalid highscore category config: {}", token);
            }
        }
        return parsed.isEmpty() ? List.of(StatCategory.EXPERIENCE) : parsed;
    }

    private static List<Integer> parseVocations(String value) {
        Set<Integer> parsed = new LinkedHashSet<>();
        for (String token : splitCsv(value)) {
            try {
                parsed.add(Integer.parseInt(token));
            } catch (NumberFormatException ex) {
                log.warn("[HIGHSCORE_SCRAPER] Ignoring invalid highscore vocation config: {}", token);
            }
        }
        return parsed.isEmpty() ? List.of(0) : new ArrayList<>(parsed);
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String raw : value.split(",")) {
            String token = raw.trim().toUpperCase();
            if (!token.isBlank()) {
                out.add(token);
            }
        }
        return out;
    }

    private static String normalizedZone(String value) {
        return (value == null || value.isBlank()) ? "America/Sao_Paulo" : value;
    }

    public static class Plan {
        private boolean enabled = true;
        private String cron = "0 0 7 * * *";
        private String zone = "America/Sao_Paulo";
        private boolean runOnStartup = false;
        private long startupDelayMs = 0;
        private String categories = "EXPERIENCE";
        private String vocations = "0";
        private int maxPages = 100;
        private int pageDelayMs = 0;
        private int worldLimit = 0;
        private int scopesPerRun = 0;
        private int parallelism = 4;
        private int pageWindowSize = 1;
        private int requestParallelism = 4;
        private int requestMaxAttempts = 1;
        private int retryBaseDelayMs = 5000;
        private int retryMaxDelayMs = 300000;
        private int forbiddenCooldownMs = 14400000;
        private int requestJitterMs = 300;
        private int requestMinIntervalMs = 750;
        private int cooldownLogIntervalMs = 30000;
        private int progressLogIntervalScopes = 10;
        private boolean abortRunOnForbidden = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
        public String getZone() { return normalizedZone(zone); }
        public void setZone(String zone) { this.zone = zone; }
        public boolean isRunOnStartup() { return runOnStartup; }
        public void setRunOnStartup(boolean runOnStartup) { this.runOnStartup = runOnStartup; }
        public long getStartupDelayMs() { return Math.max(0, startupDelayMs); }
        public void setStartupDelayMs(long startupDelayMs) { this.startupDelayMs = startupDelayMs; }
        public String getCategories() { return categories; }
        public void setCategories(String categories) { this.categories = categories; }
        public String getVocations() { return vocations; }
        public void setVocations(String vocations) { this.vocations = vocations; }
        public int getMaxPages() { return Math.max(1, maxPages); }
        public void setMaxPages(int maxPages) { this.maxPages = maxPages; }
        public int getPageDelayMs() { return Math.max(0, pageDelayMs); }
        public void setPageDelayMs(int pageDelayMs) { this.pageDelayMs = pageDelayMs; }
        public int getWorldLimit() { return Math.max(0, worldLimit); }
        public void setWorldLimit(int worldLimit) { this.worldLimit = worldLimit; }
        public int getScopesPerRun() { return Math.max(0, scopesPerRun); }
        public boolean isAllScopesPerRun() { return getScopesPerRun() == 0; }
        public void setScopesPerRun(int scopesPerRun) { this.scopesPerRun = scopesPerRun; }
        public int getParallelism() { return Math.max(1, parallelism); }
        public void setParallelism(int parallelism) { this.parallelism = parallelism; }
        public int getPageWindowSize() { return Math.max(1, pageWindowSize); }
        public void setPageWindowSize(int pageWindowSize) { this.pageWindowSize = pageWindowSize; }
        public int getRequestParallelism() { return Math.max(1, requestParallelism); }
        public void setRequestParallelism(int requestParallelism) { this.requestParallelism = requestParallelism; }
        public int getRequestMaxAttempts() { return Math.max(1, requestMaxAttempts); }
        public void setRequestMaxAttempts(int requestMaxAttempts) { this.requestMaxAttempts = requestMaxAttempts; }
        public int getRetryBaseDelayMs() { return Math.max(0, retryBaseDelayMs); }
        public void setRetryBaseDelayMs(int retryBaseDelayMs) { this.retryBaseDelayMs = retryBaseDelayMs; }
        public int getRetryMaxDelayMs() { return Math.max(getRetryBaseDelayMs(), retryMaxDelayMs); }
        public void setRetryMaxDelayMs(int retryMaxDelayMs) { this.retryMaxDelayMs = retryMaxDelayMs; }
        public int getForbiddenCooldownMs() { return Math.max(0, forbiddenCooldownMs); }
        public void setForbiddenCooldownMs(int forbiddenCooldownMs) { this.forbiddenCooldownMs = forbiddenCooldownMs; }
        public int getRequestJitterMs() { return Math.max(0, requestJitterMs); }
        public void setRequestJitterMs(int requestJitterMs) { this.requestJitterMs = requestJitterMs; }
        public int getRequestMinIntervalMs() { return Math.max(0, requestMinIntervalMs); }
        public void setRequestMinIntervalMs(int requestMinIntervalMs) { this.requestMinIntervalMs = requestMinIntervalMs; }
        public int getCooldownLogIntervalMs() { return Math.max(1000, cooldownLogIntervalMs); }
        public void setCooldownLogIntervalMs(int cooldownLogIntervalMs) { this.cooldownLogIntervalMs = cooldownLogIntervalMs; }
        public int getProgressLogIntervalScopes() { return Math.max(1, progressLogIntervalScopes); }
        public void setProgressLogIntervalScopes(int progressLogIntervalScopes) { this.progressLogIntervalScopes = progressLogIntervalScopes; }
        public boolean isAbortRunOnForbidden() { return abortRunOnForbidden; }
        public void setAbortRunOnForbidden(boolean abortRunOnForbidden) { this.abortRunOnForbidden = abortRunOnForbidden; }

        public List<StatCategory> categoryList() { return parseCategories(categories); }
        public List<Integer> vocationFilterIds() { return parseVocations(vocations); }

        public String summary() {
            return "enabled=" + enabled
                    + ", cron=" + cron
                    + ", zone=" + getZone()
                    + ", runOnStartup=" + runOnStartup
                    + ", categories=" + categories
                    + ", vocations=" + vocations
                    + ", maxPages=" + getMaxPages()
                    + ", worldLimit=" + getWorldLimit()
                    + ", scopesPerRun=" + getScopesPerRun()
                    + ", parallelism=" + getParallelism()
                    + ", pageWindowSize=" + getPageWindowSize()
                    + ", requestParallelism=" + getRequestParallelism()
                    + ", requestMinIntervalMs=" + getRequestMinIntervalMs()
                    + ", requestMaxAttempts=" + getRequestMaxAttempts()
                    + ", abortRunOnForbidden=" + abortRunOnForbidden;
        }
    }
}
