package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.HighscoreScrapeStateRepositoryPort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApplicationServiceBatch57TailCoverageTest {
    private static final Instant NOW = Instant.parse("2026-06-05T12:00:00Z");

    @Test
    void characterNameParserCoversBlankInputNullNormalizationCurrentNameFilteringAndCaseInsensitiveComparison() {
        CharacterNameParser parser = new CharacterNameParser();

        assertThat(parser.parseFormerNames(" ", "Current")).isEmpty();
        assertThat(parser.normalizeFormerNames(null, "Current")).isEmpty();
        assertThat(parser.normalizeName(null)).isEmpty();
        assertThat(parser.parseFormerNames(" Former One , current, ,Other Name ", " Current "))
                .containsExactly("Former One", "Other Name");
        assertThat(parser.sameName("Knight  One", " knight one ")).isTrue();
        assertThat(parser.isBlank(null)).isTrue();
    }

    @Test
    void highscoreScopePlannerReturnsEmptySelectionWithoutTouchingStateWhenNoWorldsExist() {
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        HighscoreScrapeStateRepositoryPort state = mock(HighscoreScrapeStateRepositoryPort.class);
        HighscoreScopePlanner planner = new HighscoreScopePlanner(worlds, state);
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();

        when(worlds.findAll()).thenReturn(List.of());

        var selection = planner.selectScopes(plan);

        assertThat(selection.hasWorlds()).isFalse();
        assertThat(selection.hasScopes()).isFalse();
        assertThat(selection.worldCount()).isZero();
        assertThat(selection.categoryCount()).isEqualTo(1);
        assertThat(selection.vocationCount()).isEqualTo(1);
        assertThat(selection.scopes()).isEmpty();
        verifyNoInteractions(state);
    }

    @Test
    void highscoreScopePlannerSortsLimitsRegistersAndExposesSelectionCounts() {
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        HighscoreScrapeStateRepositoryPort state = mock(HighscoreScrapeStateRepositoryPort.class);
        HighscoreScopePlanner planner = new HighscoreScopePlanner(worlds, state);
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setWorldLimit(1);
        plan.setScopesPerRun(7);
        plan.setCategories("EXPERIENCE");
        plan.setVocations("0,4");

        World secura = new World("Secura", "Open PvP", "EU");
        secura.setId(2);
        World antica = new World("Antica", "Open PvP", "EU");
        antica.setId(1);
        HighscoreScope scope = new HighscoreScope(1, "Antica", StatCategory.EXPERIENCE, 0);

        when(worlds.findAll()).thenReturn(List.of(secura, antica));
        when(state.findNextScopes(anyList(), anyList(), anyList(), eq(7))).thenReturn(List.of(scope));

        var selection = planner.selectScopes(plan);

        assertThat(selection.hasWorlds()).isTrue();
        assertThat(selection.hasScopes()).isTrue();
        assertThat(selection.worldCount()).isEqualTo(1);
        assertThat(selection.categoryCount()).isEqualTo(1);
        assertThat(selection.vocationCount()).isEqualTo(2);
        assertThat(selection.worlds()).extracting(World::getName).containsExactly("Antica");
        assertThat(selection.scopes()).containsExactly(scope);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<World>> registeredWorlds = ArgumentCaptor.forClass(List.class);
        verify(state).registerScopes(registeredWorlds.capture(), anyList(), anyList());
        assertThat(registeredWorlds.getValue()).extracting(World::getName).containsExactly("Antica");
    }

    @Test
    void manualCoordinatorRunsGuildScraperAndRecordsAcceptedJob() throws Exception {
        GuildScrapeService guildScrapeService = mock(GuildScrapeService.class);
        ScrapeJobService scrapeJobs = mock(ScrapeJobService.class);
        ManualScraperRunCoordinator coordinator = new ManualScraperRunCoordinator(
                mock(ScrapeService.class),
                mock(CharacterDetailsService.class),
                guildScrapeService,
                mock(HighscoreService.class),
                scrapeJobs,
                new HighscoreScrapeProperties()
        );
        CountDownLatch workerCalled = new CountDownLatch(1);
        when(scrapeJobs.start(ScrapeJobService.GUILD_SCRAPER)).thenReturn(700L);
        when(guildScrapeService.updateKnownGuilds()).thenAnswer(invocation -> {
            workerCalled.countDown();
            return ScrapeJobResult.of(4, 1, 2, 0);
        });

        AdminScraperService.ManualRunResponse response = coordinator.triggerGuilds();

        assertThat(response.scraper()).isEqualTo("guilds");
        assertThat(response.accepted()).isTrue();
        assertThat(workerCalled.await(2, TimeUnit.SECONDS)).isTrue();
        waitUntil(() -> !coordinator.isManualRunActive("guilds"));

        ArgumentCaptor<ScrapeJobResult> result = ArgumentCaptor.forClass(ScrapeJobResult.class);
        verify(scrapeJobs).finishSuccess(eq(700L), result.capture());
        assertThat(result.getValue()).isEqualTo(ScrapeJobResult.of(4, 1, 2, 0));
    }

    @Test
    void analyticsHistoryMapsFoundWorldAndThrowsForMissingWorld() {
        WorldRepositoryPort worlds = mock(WorldRepositoryPort.class);
        AnalyticsService analytics = new AnalyticsService(worlds);
        World antica = new World("Antica", "Open PvP", "EU");
        Scrape scrape = new Scrape(antica, NOW, 321, null);

        when(worlds.findByName("Antica")).thenReturn(Optional.of(antica));
        when(worlds.findScrapesByWorldAndRange(antica, NOW.minusSeconds(60), NOW)).thenReturn(List.of(scrape));
        when(worlds.findByName("Missing")).thenReturn(Optional.empty());

        assertThat(analytics.getWorldOnlineHistory("Antica", NOW.minusSeconds(60), NOW))
                .singleElement()
                .satisfies(point -> {
                    assertThat(point.timestamp()).isEqualTo(NOW);
                    assertThat(point.playersOnline()).isEqualTo(321);
                });

        assertThatThrownBy(() -> analytics.getWorldOnlineHistory("Missing", null, null))
                .isInstanceOf(NoSuchElementException.class);
    }

    private static void waitUntil(BooleanSupplierWithException condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    @FunctionalInterface
    private interface BooleanSupplierWithException {
        boolean getAsBoolean() throws Exception;
    }
}
