package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import com.nathan.tibiastats.domain.model.HighscoreStatRow;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HighscoreAndThrottleBatch59CoverageTest {
    @Test
    void runCoordinatorRestoresInterruptFlagWhenInterruptedWaitingForWorkerResult() {
        HighscoreScopePlanner planner = mock(HighscoreScopePlanner.class);
        HighscoreHttpBackoffCoordinator backoff = mock(HighscoreHttpBackoffCoordinator.class);
        HighscoreScopeWorker worker = mock(HighscoreScopeWorker.class);
        HighscoreRunCoordinator coordinator = new HighscoreRunCoordinator(planner, backoff, worker);
        HighscoreScrapeProperties.Plan plan = plan();
        List<HighscoreScope> scopes = List.of(scope("Antica"));
        when(planner.selectScopes(plan)).thenReturn(selection(List.of(world(1, "Antica")), scopes));
        when(worker.run(
                anyInt(),
                anyList(),
                any(AtomicInteger.class),
                any(AtomicInteger.class),
                anyMap(),
                any(ExecutorService.class),
                any(Semaphore.class),
                eq("daily"),
                same(plan),
                any(AtomicBoolean.class)
        )).thenAnswer(invocation -> {
            try {
                Thread.sleep(25L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return new HighscoreScopeWorkerResult(1, 0, 0, 1, 10);
        });

        ScrapeJobResult result;
        try {
            Thread.currentThread().interrupt();

            result = coordinator.run(Instant.now(), "daily", plan);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }

        assertThat(result.itemsProcessed()).isOne();
        assertThat(result.itemsUpdated()).isZero();
        assertThat(result.itemsFailed()).isOne();
        verify(backoff, never()).resetAfterSuccessfulRun(any(), anyInt(), anyInt());
    }

    @Test
    void scopeScraperCanSeeRowsButRemainEmptyWhenStorageWritesZero() throws Exception {
        HighscorePageFetcher fetcher = mock(HighscorePageFetcher.class);
        HighscoreCharacterResolver resolver = mock(HighscoreCharacterResolver.class);
        HighscoreScopeStateService states = mock(HighscoreScopeStateService.class);
        HighscoreStatStorageService storage = mock(HighscoreStatStorageService.class);
        HighscoreScrapeProperties.Plan plan = plan();
        plan.setMaxPages(2);
        plan.setPageWindowSize(2);
        HighscoreScope scope = scope("Antica");
        AtomicBoolean rateLimited = new AtomicBoolean(false);

        when(fetcher.fetchPage(same(scope), eq(1), any(Semaphore.class), same(plan), same(rateLimited)))
                .thenReturn(new HighscorePageFetcher.PageResult(1, List.of(
                        new HighscorePageFetcher.PageRow("  ", 10L, 1),
                        new HighscorePageFetcher.PageRow("Stored Hero", 20L, 2)
                )));
        when(fetcher.fetchPage(same(scope), eq(2), any(Semaphore.class), same(plan), same(rateLimited)))
                .thenReturn(new HighscorePageFetcher.PageResult(2, List.of()));
        when(resolver.normalizeCharacterName("  ")).thenReturn("");
        when(resolver.normalizeCharacterName("Stored Hero")).thenReturn("Stored Hero");
        when(resolver.resolveCharacterId(eq("Stored Hero"), anyMap())).thenReturn(50L);
        when(storage.upsertBatch(anyList())).thenReturn(0);

        HighscoreScopeScraper scraper = new HighscoreScopeScraper(
                fetcher,
                resolver,
                states,
                new HighscoreFetchRetryPolicy(),
                storage
        );

        HighscoreScopeScrapeResult result;
        try (ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            result = scraper.scrape(scope, Map.of(), executor, new Semaphore(1), "daily", plan, rateLimited);
        }

        assertThat(result.status()).isEqualTo("EMPTY");
        assertThat(result.pages()).isEqualTo(1);
        assertThat(result.rows()).isZero();
        ArgumentCaptor<List<HighscoreStatRow>> rows = ArgumentCaptor.forClass(List.class);
        verify(storage).upsertBatch(rows.capture());
        assertThat(rows.getValue()).singleElement().satisfies(row -> {
            assertThat(row.characterId()).isEqualTo(50L);
            assertThat(row.value()).isEqualTo(20L);
            assertThat(row.rank()).isEqualTo(2);
        });
        verify(states).markFinished(eq(scope), eq("EMPTY"), eq(1), eq(0), any(Long.class), isNull());
    }

    @Test
    void requestThrottlePrunesExpiredRequestStartsWithoutWaitingForFullBudgetWindow() throws Exception {
        HighscoreRequestThrottle throttle = new HighscoreRequestThrottle();
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setRequestMinIntervalMs(0);
        plan.setPageDelayMs(0);
        plan.setRequestJitterMs(0);
        plan.setRequestBudgetMaxRequests(1);
        plan.setRequestBudgetWindowMs(600_000L);
        plan.setCooldownLogIntervalMs(1);
        requestBudget(throttle).add(System.currentTimeMillis() - 600_001L);

        throttle.awaitBeforeRequest(plan);

        assertThat(requestBudget(throttle)).hasSize(1);
        assertThat(requestBudget(throttle).getFirst()).isGreaterThan(System.currentTimeMillis() - 10_000L);
    }

    @SuppressWarnings("unchecked")
    private static ArrayDeque<Long> requestBudget(HighscoreRequestThrottle throttle) throws Exception {
        Field field = HighscoreRequestThrottle.class.getDeclaredField("recentHighscoreRequestStarts");
        field.setAccessible(true);
        return (ArrayDeque<Long>) field.get(throttle);
    }

    private static HighscoreScrapeProperties.Plan plan() {
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setParallelism(1);
        plan.setRequestParallelism(1);
        plan.setPageWindowSize(1);
        plan.setMaxPages(1);
        plan.setRequestMaxAttempts(1);
        plan.setProgressLogIntervalScopes(1);
        plan.setScopesPerRun(0);
        return plan;
    }

    private static HighscoreScopePlanner.HighscoreScopeSelection selection(List<World> worlds, List<HighscoreScope> scopes) {
        return new HighscoreScopePlanner.HighscoreScopeSelection(
                worlds,
                List.of(StatCategory.EXPERIENCE),
                List.of(0),
                scopes
        );
    }

    private static HighscoreScope scope(String worldName) {
        return new HighscoreScope(1, worldName, StatCategory.EXPERIENCE, 0);
    }

    private static World world(Integer id, String name) {
        World world = new World(name, "Open PvP", "EU");
        world.setId(id);
        return world;
    }
}
