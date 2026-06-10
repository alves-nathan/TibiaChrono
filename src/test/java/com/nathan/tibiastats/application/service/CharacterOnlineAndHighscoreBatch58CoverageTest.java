package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.application.query.ApiQueryService;
import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterOnlineAndHighscoreBatch58CoverageTest {
    @Test
    void characterOnlineActivityAppliesDefaultsCapsGapAndAggregatesSummary() {
        ApiQueryService queries = mock(ApiQueryService.class);
        CharacterOnlineActivityService service = new CharacterOnlineActivityService(queries);
        Instant from = Instant.parse("2026-06-05T10:00:00Z");
        Instant to = Instant.parse("2026-06-05T12:00:00Z");
        ApiQueryService.CharacterView character = new ApiQueryService.CharacterView(
                10L,
                "Knight One",
                300,
                "male",
                "Knight",
                "Elite Knight",
                100,
                "Thais",
                null,
                "Premium Account",
                null,
                null,
                "UPDATED"
        );
        ApiQueryService.CharacterOnlineWorldSummaryView antica = new ApiQueryService.CharacterOnlineWorldSummaryView(
                10L,
                "Knight One",
                "Antica",
                3,
                2,
                45L,
                from,
                null
        );
        ApiQueryService.CharacterOnlineWorldSummaryView secura = new ApiQueryService.CharacterOnlineWorldSummaryView(
                10L,
                "Knight One",
                "Secura",
                4,
                1,
                30L,
                null,
                to
        );

        when(queries.findCharacter("Knight One")).thenReturn(java.util.Optional.of(character));
        when(queries.findCharacterOnlineWorldSummaries("Knight One", "  ", from, to, 1440)).thenReturn(List.of(antica, secura));
        when(queries.findCharacterOnlineHistory(eq("Knight One"), eq("Antica"), any(Instant.class), any(Instant.class), eq(1000))).thenReturn(List.of());
        when(queries.findCharacterOnlineSessions(eq("Knight One"), eq("Antica"), any(Instant.class), any(Instant.class), eq(15), eq(100))).thenReturn(List.of());

        assertThat(service.history("Knight One", "Antica", null, null, -1)).isEmpty();
        assertThat(service.sessions("Knight One", "Antica", null, null, 0, null)).isEmpty();

        CharacterOnlineActivityService.CharacterOnlineActivitySummary summary =
                service.summary("Knight One", "  ", from, to, 9999);

        assertThat(summary.characterId()).isEqualTo(10L);
        assertThat(summary.characterName()).isEqualTo("Knight One");
        assertThat(summary.world()).isNull();
        assertThat(summary.maxGapMinutes()).isEqualTo(1440);
        assertThat(summary.appearances()).isEqualTo(7);
        assertThat(summary.sessions()).isEqualTo(3);
        assertThat(summary.observedMinutes()).isEqualTo(75L);
        assertThat(summary.firstSeenAt()).isEqualTo(from);
        assertThat(summary.lastSeenAt()).isEqualTo(to);
        assertThat(summary.worlds()).containsExactly(antica, secura);
    }

    @Test
    void highscoreRequestThrottleCoversDisabledBudgetAndInterruptedSleep() {
        HighscoreRequestThrottle throttle = new HighscoreRequestThrottle();
        HighscoreScrapeProperties.Plan disabledBudget = mock(HighscoreScrapeProperties.Plan.class);
        when(disabledBudget.getRequestMinIntervalMs()).thenReturn(0);
        when(disabledBudget.getPageDelayMs()).thenReturn(0);
        when(disabledBudget.getRequestJitterMs()).thenReturn(0);
        when(disabledBudget.getRequestBudgetMaxRequests()).thenReturn(0);
        when(disabledBudget.getRequestBudgetWindowMs()).thenReturn(0L);

        throttle.awaitBeforeRequest(disabledBudget);

        HighscoreScrapeProperties.Plan interrupted = mock(HighscoreScrapeProperties.Plan.class);
        when(interrupted.getRequestMinIntervalMs()).thenReturn(0);
        when(interrupted.getPageDelayMs()).thenReturn(1);
        when(interrupted.getRequestJitterMs()).thenReturn(0);

        try {
            Thread.currentThread().interrupt();

            assertThatThrownBy(() -> throttle.awaitBeforeRequest(interrupted))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Interrupted while throttling highscore requests");

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void highscoreScopeScraperTreatsCheckedExecutionExceptionAsFailedScope() throws Exception {
        HighscorePageFetcher pageFetcher = mock(HighscorePageFetcher.class);
        HighscoreScopeStateService state = mock(HighscoreScopeStateService.class);
        HighscoreScopeScraper scraper = new HighscoreScopeScraper(
                pageFetcher,
                mock(HighscoreCharacterResolver.class),
                state,
                new HighscoreFetchRetryPolicy(),
                mock(HighscoreStatStorageService.class)
        );
        ExecutorService executor = mock(ExecutorService.class);
        @SuppressWarnings("unchecked")
        Future<HighscorePageFetcher.PageResult> future = mock(Future.class);
        HighscoreScrapeProperties.Plan plan = new HighscoreScrapeProperties.Plan();
        plan.setMaxPages(1);
        plan.setPageWindowSize(1);
        HighscoreScope scope = new HighscoreScope(1, "Antica", StatCategory.EXPERIENCE, 0);
        when(executor.submit(any(java.util.concurrent.Callable.class))).thenReturn(future);
        when(future.get()).thenThrow(new ExecutionException(new IOException("checked failure")));

        HighscoreScopeScrapeResult result = scraper.scrape(
                scope,
                Map.of(),
                executor,
                new Semaphore(1),
                "daily",
                plan,
                new AtomicBoolean(false)
        );

        assertThat(result.status()).isEqualTo("FAILED");
        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(state).markFinished(eq(scope), status.capture(), eq(0), eq(0), any(Long.class), eq("checked failure"));
        assertThat(status.getValue()).isEqualTo("FAILED");
    }
}
