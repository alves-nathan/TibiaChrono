package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
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
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HighscoreRunCoordinatorTailCoverageTest {
    @Test
    void runReturnsEmptyWhenThereAreNoWorldsOrNoEligibleScopes() {
        HighscoreScopePlanner planner = mock(HighscoreScopePlanner.class);
        HighscoreHttpBackoffCoordinator backoff = mock(HighscoreHttpBackoffCoordinator.class);
        HighscoreScopeWorker worker = mock(HighscoreScopeWorker.class);
        HighscoreRunCoordinator coordinator = new HighscoreRunCoordinator(planner, backoff, worker);
        HighscoreScrapeProperties.Plan plan = plan();

        when(planner.selectScopes(plan)).thenReturn(selection(List.of(), List.of()));

        assertThat(coordinator.run(Instant.now(), "daily", plan)).isEqualTo(ScrapeJobResult.empty());

        World antica = world(1, "Antica");
        when(planner.selectScopes(plan)).thenReturn(selection(List.of(antica), List.of()));

        assertThat(coordinator.run(Instant.now(), "daily", plan)).isEqualTo(ScrapeJobResult.empty());
        verifyNoInteractions(worker, backoff);
    }

    @Test
    void runAggregatesWorkerResultsAndResetsBackoffAfterSuccessfulNonRateLimitedRun() {
        HighscoreScopePlanner planner = mock(HighscoreScopePlanner.class);
        HighscoreHttpBackoffCoordinator backoff = mock(HighscoreHttpBackoffCoordinator.class);
        HighscoreScopeWorker worker = mock(HighscoreScopeWorker.class);
        HighscoreRunCoordinator coordinator = new HighscoreRunCoordinator(planner, backoff, worker);
        HighscoreScrapeProperties.Plan plan = plan();
        List<HighscoreScope> scopes = List.of(scope("Antica"), scope("Bona"));
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
        )).thenReturn(new HighscoreScopeWorkerResult(1, 1, 0, 2, 5));

        ScrapeJobResult result = coordinator.run(Instant.now(), "daily", plan);

        assertThat(result.itemsProcessed()).isEqualTo(2);
        assertThat(result.itemsCreated()).isZero();
        assertThat(result.itemsUpdated()).isEqualTo(5);
        assertThat(result.itemsFailed()).isZero();
        verify(backoff).resetAfterSuccessfulRun("daily", 1, 1);
    }

    @Test
    void runDoesNotResetBackoffWhenWorkerMarksTheRunAsRateLimited() {
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
            AtomicBoolean rateLimited = invocation.getArgument(9);
            rateLimited.set(true);
            return new HighscoreScopeWorkerResult(0, 0, 1, 0, 0);
        });

        ScrapeJobResult result = coordinator.run(Instant.now(), "daily", plan);

        assertThat(result.itemsProcessed()).isOne();
        assertThat(result.itemsFailed()).isOne();
        verify(backoff, never()).resetAfterSuccessfulRun(any(), anyInt(), anyInt());
    }

    @Test
    void runCountsUnexpectedWorkerFailuresAsFailedScopes() {
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
        )).thenThrow(new IllegalStateException("worker failed"));

        ScrapeJobResult result = coordinator.run(Instant.now(), "daily", plan);

        assertThat(result.itemsProcessed()).isOne();
        assertThat(result.itemsUpdated()).isZero();
        assertThat(result.itemsFailed()).isOne();
        verify(backoff, never()).resetAfterSuccessfulRun(any(), anyInt(), anyInt());
    }

    private static HighscoreScrapeProperties.Plan plan() {
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setParallelism(1);
        plan.setRequestParallelism(1);
        plan.setPageWindowSize(1);
        plan.setMaxPages(1);
        plan.setRequestMaxAttempts(1);
        plan.setProgressLogIntervalScopes(1);
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
