package com.nathan.tibiastats.config;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HighscorePlanSettingsUnitTest {
    @Test
    void clampsUnsafeNumericSettingsToSafeRuntimeValues() {
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setStartupDelayMs(-1);
        plan.setMaxPages(0);
        plan.setPageDelayMs(-1);
        plan.setWorldLimit(-1);
        plan.setScopesPerRun(-1);
        plan.setParallelism(0);
        plan.setPageWindowSize(0);
        plan.setRequestParallelism(0);
        plan.setRequestMaxAttempts(0);
        plan.setRetryBaseDelayMs(-1);
        plan.setRetryMaxDelayMs(10);
        plan.setForbiddenInitialCooldownMs(-1);
        plan.setForbiddenMaxCooldownMs(10);
        plan.setForbiddenCooldownMultiplier(0.5D);
        plan.setRequestJitterMs(-1);
        plan.setRequestMinIntervalMs(-1);
        plan.setRequestBudgetMaxRequests(-1);
        plan.setRequestBudgetWindowMs(-1);
        plan.setCooldownLogIntervalMs(10);
        plan.setProgressLogIntervalScopes(0);

        assertThat(plan.getStartupDelayMs()).isZero();
        assertThat(plan.getMaxPages()).isOne();
        assertThat(plan.getPageDelayMs()).isZero();
        assertThat(plan.getWorldLimit()).isZero();
        assertThat(plan.getScopesPerRun()).isZero();
        assertThat(plan.isAllScopesPerRun()).isTrue();
        assertThat(plan.getParallelism()).isOne();
        assertThat(plan.getPageWindowSize()).isOne();
        assertThat(plan.getRequestParallelism()).isOne();
        assertThat(plan.getRequestMaxAttempts()).isOne();
        assertThat(plan.getRetryBaseDelayMs()).isZero();
        assertThat(plan.getRetryMaxDelayMs()).isEqualTo(10);
        assertThat(plan.getForbiddenInitialCooldownMs()).isZero();
        assertThat(plan.getForbiddenMaxCooldownMs()).isEqualTo(10);
        assertThat(plan.getForbiddenCooldownMultiplier()).isEqualTo(1.0D);
        assertThat(plan.getRequestJitterMs()).isZero();
        assertThat(plan.getRequestMinIntervalMs()).isZero();
        assertThat(plan.getRequestBudgetMaxRequests()).isEqualTo(150_000);
        assertThat(plan.getRequestBudgetWindowMs()).isEqualTo(600_000L);
        assertThat(plan.getCooldownLogIntervalMs()).isEqualTo(1_000);
        assertThat(plan.getProgressLogIntervalScopes()).isOne();
    }

    @Test
    void parsesCategoriesAndVocationsWithFallbacksAndDeduplication() {
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setCategories(" experience,magic_level,invalid ");
        plan.setVocations(" 0,1,1,bad,6 ");

        assertThat(plan.categoryList())
                .containsExactly(StatCategory.EXPERIENCE, StatCategory.MAGIC_LEVEL);
        assertThat(plan.vocationFilterIds())
                .containsExactly(0, 1, 6);

        plan.setCategories(" , invalid ");
        plan.setVocations(" , bad ");

        assertThat(plan.categoryList()).containsExactly(StatCategory.EXPERIENCE);
        assertThat(plan.vocationFilterIds()).containsExactly(0);
    }

    @Test
    void normalizesBlankZoneAndBuildsSummaryFromClampedValues() {
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setZone(" ");
        plan.setMaxPages(0);
        plan.setWorldLimit(-1);
        plan.setScopesPerRun(-1);
        plan.setAbortRunOnForbidden(false);

        assertThat(plan.getZone()).isEqualTo("America/Sao_Paulo");
        assertThat(plan.summary())
                .contains("zone=America/Sao_Paulo")
                .contains("maxPages=1")
                .contains("worldLimit=0")
                .contains("scopesPerRun=0")
                .contains("abortRunOnForbidden=false");
    }

    @Test
    void legacyPlanCopiesAllRelevantSettingsAndEffectivePlansFallbackToDefault() {
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        properties.setEnabled(false);
        properties.setCron("0 15 8 * * *");
        properties.setZone("UTC");
        properties.setRunOnStartup(true);
        properties.setStartupDelayMs(123L);
        properties.setCategories("EXPERIENCE,MAGIC_LEVEL");
        properties.setVocations("0,1");
        properties.setMaxPages(7);
        properties.setParallelism(2);
        properties.setRequestParallelism(1);
        properties.setAbortRunOnForbidden(false);

        HighscoreScrapeProperties.Plan legacy = properties.toLegacyPlan();

        assertThat(legacy.isEnabled()).isFalse();
        assertThat(legacy.getCron()).isEqualTo("0 15 8 * * *");
        assertThat(legacy.getZone()).isEqualTo("UTC");
        assertThat(legacy.isRunOnStartup()).isTrue();
        assertThat(legacy.getStartupDelayMs()).isEqualTo(123L);
        assertThat(legacy.categoryList())
                .containsExactly(StatCategory.EXPERIENCE, StatCategory.MAGIC_LEVEL);
        assertThat(legacy.vocationFilterIds()).containsExactly(0, 1);
        assertThat(legacy.getMaxPages()).isEqualTo(7);
        assertThat(legacy.getParallelism()).isEqualTo(2);
        assertThat(legacy.getRequestParallelism()).isOne();
        assertThat(legacy.isAbortRunOnForbidden()).isFalse();

        assertThat(properties.effectivePlans()).containsOnlyKeys("default");
        assertThat(properties.effectivePlans().get("default").getCron()).isEqualTo("0 15 8 * * *");
    }

    @Test
    void effectivePlansUsesConfiguredPlansAndDefensivelyCopiesInputMap() {
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        HighscoreScrapeProperties.Plan named = new HighscoreScrapeProperties.Plan();
        named.setCron("0 0 9 * * *");

        Map<String, HighscoreScrapeProperties.Plan> input = new LinkedHashMap<>();
        input.put("named", named);
        properties.setPlans(input);
        input.clear();

        assertThat(properties.effectivePlans()).containsOnlyKeys("named");
        assertThat(properties.effectivePlans().get("named").getCron()).isEqualTo("0 0 9 * * *");

        properties.setPlans(null);
        assertThat(properties.getPlans()).isEmpty();
        assertThat(properties.effectivePlans()).containsOnlyKeys("default");
    }
}
