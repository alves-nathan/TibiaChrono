package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.application.service.CharacterDetailsService;
import com.nathan.tibiastats.application.service.GuildScrapeService;
import com.nathan.tibiastats.application.service.HighscoreService;
import com.nathan.tibiastats.application.service.ScrapeJobResult;
import com.nathan.tibiastats.application.service.ScrapeJobService;
import com.nathan.tibiastats.application.service.ScrapeService;
import com.nathan.tibiastats.config.AppProperties;
import com.nathan.tibiastats.config.GuildScrapeProperties;
import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SchedulerCoverageTest {
    @Test
    void worldSchedulerFinishesSuccessfulJob() {
        ScrapeService scrapeService = mock(ScrapeService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        ScrapeJobResult result = ScrapeJobResult.of(2, 1, 1, 0);
        when(scrapeJobService.start(ScrapeJobService.WORLD_SCRAPER)).thenReturn(10L);
        when(scrapeService.updateAllWorlds()).thenReturn(result);
        WorldScrapeScheduler scheduler = new WorldScrapeScheduler(scrapeService, new AppProperties(), scrapeJobService);

        scheduler.run();

        verify(scrapeJobService).start(ScrapeJobService.WORLD_SCRAPER);
        verify(scrapeService).updateAllWorlds();
        verify(scrapeJobService).finishSuccess(10L, result);
    }

    @Test
    void worldSchedulerMarksJobAsFailedAndRethrowsWhenServiceFails() {
        ScrapeService scrapeService = mock(ScrapeService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        IllegalStateException failure = new IllegalStateException("world scrape failed");
        when(scrapeJobService.start(ScrapeJobService.WORLD_SCRAPER)).thenReturn(11L);
        when(scrapeService.updateAllWorlds()).thenThrow(failure);
        WorldScrapeScheduler scheduler = new WorldScrapeScheduler(scrapeService, new AppProperties(), scrapeJobService);

        assertThatThrownBy(scheduler::run).isSameAs(failure);

        verify(scrapeJobService).finishFailure(11L, ScrapeJobResult.empty(), failure);
    }

    @Test
    void characterDetailsSchedulerSkipsWhenDisabled() {
        CharacterDetailsService characterDetailsService = mock(CharacterDetailsService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        AppProperties properties = new AppProperties();
        properties.getCharacterDetails().setEnabled(false);
        CharacterDetailsScrapeScheduler scheduler = new CharacterDetailsScrapeScheduler(
                characterDetailsService,
                properties,
                scrapeJobService
        );

        scheduler.run();

        verifyNoInteractions(characterDetailsService, scrapeJobService);
    }

    @Test
    void characterDetailsSchedulerFinishesSuccessfulBatch() {
        CharacterDetailsService characterDetailsService = mock(CharacterDetailsService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        AppProperties properties = new AppProperties();
        properties.getCharacterDetails().setEnabled(true);
        properties.getCharacterDetails().setBatchSize(0);
        ScrapeJobResult result = ScrapeJobResult.of(3, 0, 2, 1);
        when(scrapeJobService.start(ScrapeJobService.CHARACTER_DETAILS_SCRAPER)).thenReturn(20L);
        when(characterDetailsService.updateMissingDetailsBatch()).thenReturn(result);
        CharacterDetailsScrapeScheduler scheduler = new CharacterDetailsScrapeScheduler(
                characterDetailsService,
                properties,
                scrapeJobService
        );

        scheduler.run();

        verify(characterDetailsService).updateMissingDetailsBatch();
        verify(scrapeJobService).finishSuccess(20L, result);
    }

    @Test
    void characterDetailsSchedulerMarksJobAsFailedAndRethrowsWhenBatchFails() {
        CharacterDetailsService characterDetailsService = mock(CharacterDetailsService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        AppProperties properties = new AppProperties();
        IllegalStateException failure = new IllegalStateException("details failed");
        when(scrapeJobService.start(ScrapeJobService.CHARACTER_DETAILS_SCRAPER)).thenReturn(21L);
        when(characterDetailsService.updateMissingDetailsBatch()).thenThrow(failure);
        CharacterDetailsScrapeScheduler scheduler = new CharacterDetailsScrapeScheduler(
                characterDetailsService,
                properties,
                scrapeJobService
        );

        assertThatThrownBy(scheduler::run).isSameAs(failure);

        verify(scrapeJobService).finishFailure(21L, ScrapeJobResult.empty(), failure);
    }

    @Test
    void guildSchedulerSkipsWhenDisabled() {
        GuildScrapeService guildScrapeService = mock(GuildScrapeService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        GuildScrapeProperties properties = new GuildScrapeProperties();
        properties.setEnabled(false);
        GuildScrapeScheduler scheduler = new GuildScrapeScheduler(guildScrapeService, properties, scrapeJobService);

        scheduler.run();

        verifyNoInteractions(guildScrapeService, scrapeJobService);
    }

    @Test
    void guildSchedulerFinishesSuccessfulJob() {
        GuildScrapeService guildScrapeService = mock(GuildScrapeService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        GuildScrapeProperties properties = new GuildScrapeProperties();
        ScrapeJobResult result = ScrapeJobResult.of(4, 1, 2, 0);
        when(scrapeJobService.start(ScrapeJobService.GUILD_SCRAPER)).thenReturn(30L);
        when(guildScrapeService.updateKnownGuilds()).thenReturn(result);
        GuildScrapeScheduler scheduler = new GuildScrapeScheduler(guildScrapeService, properties, scrapeJobService);

        scheduler.run();

        verify(guildScrapeService).updateKnownGuilds();
        verify(scrapeJobService).finishSuccess(30L, result);
    }

    @Test
    void guildSchedulerMarksJobAsFailedAndRethrowsWhenServiceFails() {
        GuildScrapeService guildScrapeService = mock(GuildScrapeService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        GuildScrapeProperties properties = new GuildScrapeProperties();
        IllegalStateException failure = new IllegalStateException("guild failed");
        when(scrapeJobService.start(ScrapeJobService.GUILD_SCRAPER)).thenReturn(31L);
        when(guildScrapeService.updateKnownGuilds()).thenThrow(failure);
        GuildScrapeScheduler scheduler = new GuildScrapeScheduler(guildScrapeService, properties, scrapeJobService);

        assertThatThrownBy(scheduler::run).isSameAs(failure);

        verify(scrapeJobService).finishFailure(31L, ScrapeJobResult.empty(), failure);
    }

    @Test
    void highscoreSchedulerRegistersOnlyEnabledPlansAndRunsRegisteredTrigger() {
        HighscoreService highscoreService = mock(HighscoreService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        properties.setEnabled(true);
        HighscoreScrapeProperties.Plan enabledPlan = new HighscoreScrapeProperties.Plan();
        enabledPlan.setEnabled(true);
        enabledPlan.setCron("0 0 1 * * *");
        enabledPlan.setZone("UTC");
        HighscoreScrapeProperties.Plan disabledPlan = new HighscoreScrapeProperties.Plan();
        disabledPlan.setEnabled(false);
        Map<String, HighscoreScrapeProperties.Plan> plans = new LinkedHashMap<>();
        plans.put("enabled-plan", enabledPlan);
        plans.put("disabled-plan", disabledPlan);
        properties.setPlans(plans);
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        HighscoreScrapeScheduler scheduler = new HighscoreScrapeScheduler(highscoreService, properties, scrapeJobService);
        ScrapeJobResult result = ScrapeJobResult.of(5, 0, 5, 0);
        when(scrapeJobService.start(ScrapeJobService.HIGHSCORE_SCRAPER)).thenReturn(40L);
        when(highscoreService.updateHighscores("enabled-plan", enabledPlan)).thenReturn(result);

        scheduler.configureTasks(registrar);
        assertThat(registrar.getTriggerTaskList()).hasSize(1);
        registrar.getTriggerTaskList().get(0).getRunnable().run();

        verify(highscoreService).updateHighscores("enabled-plan", enabledPlan);
        verify(highscoreService, never()).updateHighscores(eq("disabled-plan"), any());
        verify(scrapeJobService).finishSuccess(40L, result);
    }

    @Test
    void highscoreSchedulerDoesNotRegisterTasksWhenDisabled() {
        HighscoreService highscoreService = mock(HighscoreService.class);
        ScrapeJobService scrapeJobService = mock(ScrapeJobService.class);
        HighscoreScrapeProperties properties = new HighscoreScrapeProperties();
        properties.setEnabled(false);
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        HighscoreScrapeScheduler scheduler = new HighscoreScrapeScheduler(highscoreService, properties, scrapeJobService);

        scheduler.configureTasks(registrar);
        scheduler.logConfiguration();

        assertThat(registrar.getTriggerTaskList()).isEmpty();
        verifyNoInteractions(highscoreService, scrapeJobService);
    }
}
