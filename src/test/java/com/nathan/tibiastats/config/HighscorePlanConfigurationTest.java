package com.nathan.tibiastats.config;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class HighscorePlanConfigurationTest {
    @Test
    void applicationDevDefinesSafeDistributedHighscorePlans() {
        HighscoreScrapeProperties properties = bindApplicationDevHighscoreProperties();

        assertThat(properties.getPlans()).hasSize(18);

        HighscoreScrapeProperties.Plan dailyExp = properties.getPlans().get("daily-exp");
        assertThat(dailyExp).isNotNull();
        assertThat(dailyExp.isEnabled()).isTrue();
        assertThat(dailyExp.getCron()).isEqualTo("0 0 7 * * *");
        assertThat(dailyExp.getZone()).isEqualTo("America/Sao_Paulo");
        assertThat(dailyExp.categoryList()).containsExactly(StatCategory.EXPERIENCE);
        assertThat(dailyExp.vocationFilterIds()).containsExactly(0, 1, 2, 3, 4, 5, 6);
        assertThat(dailyExp.getPageWindowSize()).isEqualTo(1);
        assertThat(dailyExp.getRequestMaxAttempts()).isEqualTo(1);
        assertThat(dailyExp.getForbiddenInitialCooldownMs()).isEqualTo(259_200_000L);
        assertThat(dailyExp.getForbiddenMaxCooldownMs()).isEqualTo(1_209_600_000L);
        assertThat(dailyExp.getRequestBudgetMaxRequests()).isEqualTo(150_000);
        assertThat(dailyExp.getRequestBudgetWindowMs()).isGreaterThanOrEqualTo(600_000L);
        assertThat(dailyExp.isAbortRunOnForbidden()).isTrue();
        assertThat(dailyExp.isRunOnStartup()).isFalse();

        HighscoreScrapeProperties.Plan manualBackfill = properties.getPlans().get("manual-backfill-all-highscores");
        assertThat(manualBackfill).isNotNull();
        assertThat(manualBackfill.isEnabled()).isFalse();
        assertThat(manualBackfill.getParallelism()).isEqualTo(1);
        assertThat(manualBackfill.getRequestParallelism()).isEqualTo(1);
        assertThat(manualBackfill.getRequestMinIntervalMs()).isGreaterThanOrEqualTo(2_000);

        properties.getPlans().forEach((name, plan) -> {
            if (!plan.isEnabled()) {
                return;
            }
            assertThat(plan.getPageWindowSize()).as(name + " must not use page-level parallelism").isEqualTo(1);
            assertThat(plan.getRequestMaxAttempts()).as(name + " must not keep retrying 403/429 responses").isEqualTo(1);
            assertThat(plan.getForbiddenInitialCooldownMs()).as(name + " must start 403/429 cooldown at 72h").isEqualTo(259_200_000L);
            assertThat(plan.getForbiddenMaxCooldownMs()).as(name + " must cap progressive 403/429 cooldown at 14d").isEqualTo(1_209_600_000L);
            assertThat(plan.getRequestBudgetMaxRequests()).as(name + " must never exceed 150k requests per budget window").isLessThanOrEqualTo(150_000);
            assertThat(plan.getRequestBudgetWindowMs()).as(name + " must use at least a 10 minute budget window").isGreaterThanOrEqualTo(600_000L);
            assertThat(plan.isAbortRunOnForbidden()).as(name + " must abort on 403/429").isTrue();
            assertThat(plan.isRunOnStartup()).as(name + " should not run automatically on every app boot").isFalse();
        });
    }


    @Test
    void planClampsUnsafeRequestBudgetConfiguration() {
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setRequestBudgetMaxRequests(999_999);
        plan.setRequestBudgetWindowMs(1_000L);

        assertThat(plan.getRequestBudgetMaxRequests()).isEqualTo(150_000);
        assertThat(plan.getRequestBudgetWindowMs()).isEqualTo(600_000L);
    }

    private HighscoreScrapeProperties bindApplicationDevHighscoreProperties() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-dev.yml"));
        Properties yamlProperties = yaml.getObject();
        assertThat(yamlProperties).as("application-dev.yml must be loadable").isNotNull();

        Map<String, Object> source = new LinkedHashMap<>();
        yamlProperties.forEach((key, value) -> source.put(String.valueOf(key), value));

        return new Binder(new MapConfigurationPropertySource(source))
                .bind("tibiastats.scrape.highscores", HighscoreScrapeProperties.class)
                .orElseThrow(() -> new AssertionError("Could not bind tibiastats.scrape.highscores from application-dev.yml"));
    }
}
