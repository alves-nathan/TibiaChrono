package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import com.nathan.tibiastats.domain.model.HighscoreStatRow;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HighscoreScopeScraperTest {
    @Test
    void scrapeStoresNormalizedRowsAndStopsWhenWindowFindsEmptyPage() throws Exception {
        HighscorePageFetcher fetcher = mock(HighscorePageFetcher.class);
        HighscoreCharacterResolver resolver = mock(HighscoreCharacterResolver.class);
        HighscoreScopeStateService states = mock(HighscoreScopeStateService.class);
        HighscoreStatStorageService storage = mock(HighscoreStatStorageService.class);
        HighscoreScrapeProperties.Plan plan = plan(3, 2);
        HighscoreScope scope = scope();
        AtomicBoolean rateLimited = new AtomicBoolean(false);
        when(fetcher.fetchPage(same(scope), eq(1), any(Semaphore.class), same(plan), same(rateLimited)))
                .thenReturn(new HighscorePageFetcher.PageResult(1, List.of(
                        new HighscorePageFetcher.PageRow(" Knight One (traded) ", 100L, 1),
                        new HighscorePageFetcher.PageRow("   ", 50L, 2)
                )));
        when(fetcher.fetchPage(same(scope), eq(2), any(Semaphore.class), same(plan), same(rateLimited)))
                .thenReturn(new HighscorePageFetcher.PageResult(2, List.of()));
        when(resolver.normalizeCharacterName(" Knight One (traded) ")).thenReturn("Knight One");
        when(resolver.normalizeCharacterName("   ")).thenReturn("");
        when(resolver.resolveCharacterId(eq("Knight One"), anyMap())).thenReturn(10L);
        when(storage.upsertBatch(any())).thenReturn(1);
        HighscoreScopeScraper scraper = new HighscoreScopeScraper(fetcher, resolver, states, new HighscoreFetchRetryPolicy(), storage);

        HighscoreScopeScrapeResult result;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            result = scraper.scrape(scope, new java.util.concurrent.ConcurrentHashMap<>(), executor, new Semaphore(2), "daily", plan, rateLimited);
        }

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.pages()).isEqualTo(1);
        assertThat(result.rows()).isEqualTo(1);
        ArgumentCaptor<List<HighscoreStatRow>> rows = ArgumentCaptor.forClass(List.class);
        verify(storage).upsertBatch(rows.capture());
        assertThat(rows.getValue()).hasSize(1);
        assertThat(rows.getValue().getFirst().characterId()).isEqualTo(10L);
        assertThat(rows.getValue().getFirst().worldId()).isEqualTo(1);
        verify(states).markStarted(scope);
        verify(states).markFinished(eq(scope), eq("SUCCESS"), eq(1), eq(1), org.mockito.ArgumentMatchers.anyLong(), isNull());
    }

    @Test
    void scrapeMarksEmptyWhenFirstPageHasNoRows() throws Exception {
        HighscorePageFetcher fetcher = mock(HighscorePageFetcher.class);
        HighscoreCharacterResolver resolver = mock(HighscoreCharacterResolver.class);
        HighscoreScopeStateService states = mock(HighscoreScopeStateService.class);
        HighscoreStatStorageService storage = mock(HighscoreStatStorageService.class);
        HighscoreScrapeProperties.Plan plan = plan(1, 1);
        HighscoreScope scope = scope();
        AtomicBoolean rateLimited = new AtomicBoolean(false);
        when(fetcher.fetchPage(same(scope), eq(1), any(Semaphore.class), same(plan), same(rateLimited)))
                .thenReturn(new HighscorePageFetcher.PageResult(1, List.of()));
        HighscoreScopeScraper scraper = new HighscoreScopeScraper(fetcher, resolver, states, new HighscoreFetchRetryPolicy(), storage);

        HighscoreScopeScrapeResult result;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            result = scraper.scrape(scope, Map.of(), executor, new Semaphore(1), "daily", plan, rateLimited);
        }

        assertThat(result.status()).isEqualTo("EMPTY");
        assertThat(result.pages()).isZero();
        assertThat(result.rows()).isZero();
        verify(storage, never()).upsertBatch(any());
        verify(states).markFinished(eq(scope), eq("EMPTY"), eq(0), eq(0), org.mockito.ArgumentMatchers.anyLong(), isNull());
    }

    @Test
    void scrapeMarksRateLimitedAndPropagatesFlagWhenPageFetcherIsRateLimited() throws Exception {
        HighscorePageFetcher fetcher = mock(HighscorePageFetcher.class);
        HighscoreScopeStateService states = mock(HighscoreScopeStateService.class);
        HighscoreScrapeProperties.Plan plan = plan(1, 1);
        HighscoreScope scope = scope();
        AtomicBoolean rateLimited = new AtomicBoolean(false);
        when(fetcher.fetchPage(same(scope), eq(1), any(Semaphore.class), same(plan), same(rateLimited)))
                .thenThrow(new RateLimitedHighscoreException("HTTP 403 from Tibia highscores"));
        HighscoreScopeScraper scraper = new HighscoreScopeScraper(
                fetcher,
                mock(HighscoreCharacterResolver.class),
                states,
                new HighscoreFetchRetryPolicy(),
                mock(HighscoreStatStorageService.class)
        );

        HighscoreScopeScrapeResult result;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            result = scraper.scrape(scope, Map.of(), executor, new Semaphore(1), "daily", plan, rateLimited);
        }

        assertThat(result.status()).isEqualTo("RATE_LIMITED");
        assertThat(rateLimited.get()).isTrue();
        verify(states).markFinished(eq(scope), eq("RATE_LIMITED"), eq(0), eq(0), org.mockito.ArgumentMatchers.anyLong(), eq("HTTP 403 from Tibia highscores"));
    }

    @Test
    void scrapeMarksFailedWhenPageFetcherThrowsRuntimeFailure() throws Exception {
        HighscorePageFetcher fetcher = mock(HighscorePageFetcher.class);
        HighscoreScopeStateService states = mock(HighscoreScopeStateService.class);
        HighscoreScrapeProperties.Plan plan = plan(1, 1);
        HighscoreScope scope = scope();
        AtomicBoolean rateLimited = new AtomicBoolean(false);
        when(fetcher.fetchPage(same(scope), eq(1), any(Semaphore.class), same(plan), same(rateLimited)))
                .thenThrow(new IllegalStateException("parser failed"));
        HighscoreScopeScraper scraper = new HighscoreScopeScraper(
                fetcher,
                mock(HighscoreCharacterResolver.class),
                states,
                new HighscoreFetchRetryPolicy(),
                mock(HighscoreStatStorageService.class)
        );

        HighscoreScopeScrapeResult result;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            result = scraper.scrape(scope, Map.of(), executor, new Semaphore(1), "daily", plan, rateLimited);
        }

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(rateLimited.get()).isFalse();
        verify(states).markFinished(eq(scope), eq("FAILED"), eq(0), eq(0), org.mockito.ArgumentMatchers.anyLong(), eq("parser failed"));
    }

    private static HighscoreScrapeProperties.Plan plan(int maxPages, int pageWindowSize) {
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setMaxPages(maxPages);
        plan.setPageWindowSize(pageWindowSize);
        return plan;
    }

    private static HighscoreScope scope() {
        return new HighscoreScope(1, "Antica", StatCategory.EXPERIENCE, 0);
    }
}
