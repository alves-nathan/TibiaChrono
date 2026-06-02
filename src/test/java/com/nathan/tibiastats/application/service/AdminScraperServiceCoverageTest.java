package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.HighscoreHttpBackoffState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminScraperServiceCoverageTest {
    @Test
    void statusAndBackoffOperationsDelegateToCollaborators() {
        AdminScraperStatusService statusService = mock(AdminScraperStatusService.class);
        ManualScraperRunCoordinator manualRunCoordinator = mock(ManualScraperRunCoordinator.class);
        HighscoreService highscoreService = mock(HighscoreService.class);
        HighscoreBackoffStatusMapper mapper = mock(HighscoreBackoffStatusMapper.class);
        AdminScraperService service = new AdminScraperService(statusService, manualRunCoordinator, highscoreService, mapper);
        AdminScraperService.HighscoreBackoffStatus mapped = backoffStatus(true);
        AdminScraperService.ScraperStatusResponse status = new AdminScraperService.ScraperStatusResponse(
                List.of(),
                List.of(),
                mapped
        );
        HighscoreHttpBackoffState current = new HighscoreHttpBackoffState(
                Instant.now().plusSeconds(60),
                1,
                60_000L,
                "RATE_LIMITED",
                "HTTP 403",
                Instant.now(),
                null
        );
        HighscoreHttpBackoffState reset = new HighscoreHttpBackoffState(null, 0, 0L, "OK", null, null, Instant.now());
        AdminScraperService.HighscoreBackoffStatus resetMapped = backoffStatus(false);
        when(statusService.status()).thenReturn(status);
        when(highscoreService.getHttpBackoffState()).thenReturn(current);
        when(highscoreService.resetHttpBackoffManually()).thenReturn(reset);
        when(mapper.toBackoffStatus(current)).thenReturn(mapped);
        when(mapper.toBackoffStatus(reset)).thenReturn(resetMapped);

        assertThat(service.status()).isSameAs(status);
        assertThat(service.highscoreBackoffStatus()).isSameAs(mapped);
        assertThat(service.resetHighscoreBackoff()).isSameAs(resetMapped);

        verify(statusService).status();
        verify(highscoreService).getHttpBackoffState();
        verify(highscoreService).resetHttpBackoffManually();
    }

    @Test
    void manualTriggerOperationsDelegateToManualRunCoordinator() {
        AdminScraperStatusService statusService = mock(AdminScraperStatusService.class);
        ManualScraperRunCoordinator manualRunCoordinator = mock(ManualScraperRunCoordinator.class);
        HighscoreService highscoreService = mock(HighscoreService.class);
        HighscoreBackoffStatusMapper mapper = mock(HighscoreBackoffStatusMapper.class);
        AdminScraperService service = new AdminScraperService(statusService, manualRunCoordinator, highscoreService, mapper);
        AdminScraperService.ManualRunResponse worlds = response("worlds", null);
        AdminScraperService.ManualRunResponse characterDetails = response("character-details", null);
        AdminScraperService.ManualRunResponse guilds = response("guilds", null);
        AdminScraperService.ManualRunResponse highscores = response("highscores:daily", "daily");
        when(manualRunCoordinator.triggerWorlds()).thenReturn(worlds);
        when(manualRunCoordinator.triggerCharacterDetails()).thenReturn(characterDetails);
        when(manualRunCoordinator.triggerGuilds()).thenReturn(guilds);
        when(manualRunCoordinator.triggerHighscorePlan("daily")).thenReturn(highscores);

        assertThat(service.triggerWorlds()).isSameAs(worlds);
        assertThat(service.triggerCharacterDetails()).isSameAs(characterDetails);
        assertThat(service.triggerGuilds()).isSameAs(guilds);
        assertThat(service.triggerHighscorePlan("daily")).isSameAs(highscores);
    }

    private AdminScraperService.ManualRunResponse response(String scraper, String planName) {
        return new AdminScraperService.ManualRunResponse(scraper, planName, true, "accepted", Instant.now());
    }

    private AdminScraperService.HighscoreBackoffStatus backoffStatus(boolean active) {
        return new AdminScraperService.HighscoreBackoffStatus(
                active,
                active ? Instant.now().plusSeconds(60) : null,
                active ? 60_000L : 0L,
                active ? 1 : 0,
                active ? 60_000L : 0L,
                active ? "RATE_LIMITED" : "OK",
                active ? "HTTP 403" : null,
                active ? Instant.now() : null,
                active ? null : Instant.now()
        );
    }
}
