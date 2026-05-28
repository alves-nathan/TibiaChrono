package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.HighscoreService;
import com.nathan.tibiastats.application.service.ScrapeJobResult;
import com.nathan.tibiastats.application.service.ScrapeJobService;
import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Map;

@Component
public class HighscoreScrapeScheduler implements SchedulingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(HighscoreScrapeScheduler.class);

    private final HighscoreService service;
    private final HighscoreScrapeProperties properties;
    private final ScrapeJobService scrapeJobService;

    public HighscoreScrapeScheduler(HighscoreService service,
                                    HighscoreScrapeProperties properties,
                                    ScrapeJobService scrapeJobService) {
        this.service = service;
        this.properties = properties;
        this.scrapeJobService = scrapeJobService;
    }

    @PostConstruct
    public void logConfiguration() {
        if (!properties.isEnabled()) {
            log.info("[HIGHSCORE_SCRAPER] Highscore scheduling disabled: enabled=false");
            return;
        }

        Map<String, HighscoreScrapeProperties.Plan> plans = properties.effectivePlans();
        log.info("[HIGHSCORE_SCRAPER] Scheduler configured with {} plan(s)", plans.size());
        plans.forEach((name, plan) -> log.info(
                "[HIGHSCORE_SCRAPER] Plan configured: name={}, {}",
                name,
                plan.summary()
        ));
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        if (!properties.isEnabled()) {
            return;
        }

        properties.effectivePlans().forEach((planName, plan) -> {
            if (!plan.isEnabled()) {
                log.info("[HIGHSCORE_SCRAPER] Plan disabled. Not registering cron task: name={}", planName);
                return;
            }

            taskRegistrar.addTriggerTask(
                    () -> runPlan(planName, plan, "cron"),
                    new CronTrigger(plan.getCron(), ZoneId.of(plan.getZone()))
            );
            log.info(
                    "[HIGHSCORE_SCRAPER] Registered cron task: plan={}, cron={}, zone={}",
                    planName,
                    plan.getCron(),
                    plan.getZone()
            );
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runStartupPlans() {
        if (!properties.isEnabled()) {
            return;
        }

        properties.effectivePlans().forEach((planName, plan) -> {
            if (!plan.isEnabled() || !plan.isRunOnStartup()) {
                return;
            }

            Thread.startVirtualThread(() -> {
                try {
                    long startupDelayMs = plan.getStartupDelayMs();
                    if (startupDelayMs > 0) {
                        log.info("[HIGHSCORE_SCRAPER] Startup run scheduled: plan={}, delayMs={}", planName, startupDelayMs);
                        Thread.sleep(startupDelayMs);
                    }
                    runPlan(planName, plan, "startup");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log.warn("[HIGHSCORE_SCRAPER] Startup run interrupted: plan={}", planName);
                } catch (Exception ex) {
                    log.error("[HIGHSCORE_SCRAPER] Startup run failed: plan={}", planName, ex);
                }
            });
        });
    }

    private void runPlan(String planName, HighscoreScrapeProperties.Plan plan, String trigger) {
        if (!properties.isEnabled()) {
            log.info("[HIGHSCORE_SCRAPER] Skipping plan because highscores.enabled=false: plan={}", planName);
            return;
        }
        if (!plan.isEnabled()) {
            log.info("[HIGHSCORE_SCRAPER] Skipping disabled plan: plan={}", planName);
            return;
        }

        Long jobId = scrapeJobService.start(ScrapeJobService.HIGHSCORE_SCRAPER);
        log.info("[HIGHSCORE_SCRAPER] Plan tick started: plan={}, trigger={}, jobId={}", planName, trigger, jobId);
        try {
            ScrapeJobResult result = service.updateHighscores(planName, plan);
            scrapeJobService.finishSuccess(jobId, result);
        } catch (Exception ex) {
            scrapeJobService.finishFailure(jobId, ScrapeJobResult.empty(), ex);
            throw ex;
        }
    }
}
