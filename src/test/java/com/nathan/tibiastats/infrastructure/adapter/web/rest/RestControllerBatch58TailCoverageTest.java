package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.query.GuildQueryService;
import com.nathan.tibiastats.application.query.HighscoreApiQueryService;
import com.nathan.tibiastats.application.service.AdminScraperService;
import com.nathan.tibiastats.application.service.GuildScrapeService;
import com.nathan.tibiastats.domain.model.GuildMembershipEventType;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestControllerBatch58TailCoverageTest {
    @Test
    void adminControllerCoversStatusBackoffResetAndRemainingAcceptedTriggers() {
        AdminScraperService adminScrapers = mock(AdminScraperService.class);
        AdminScraperController controller = new AdminScraperController(adminScrapers);
        AdminScraperService.HighscoreBackoffStatus backoff = new AdminScraperService.HighscoreBackoffStatus(
                false,
                null,
                0,
                0,
                0,
                "SUCCESS",
                null,
                null,
                Instant.parse("2026-06-05T12:00:00Z")
        );
        AdminScraperService.ScraperStatusResponse status =
                new AdminScraperService.ScraperStatusResponse(List.of(), List.of(), backoff);
        AdminScraperService.ManualRunResponse worlds =
                new AdminScraperService.ManualRunResponse("worlds", null, true, "accepted", Instant.parse("2026-06-05T12:00:00Z"));
        AdminScraperService.ManualRunResponse guilds =
                new AdminScraperService.ManualRunResponse("guilds", null, true, "accepted", Instant.parse("2026-06-05T12:00:01Z"));
        AdminScraperService.ManualRunResponse highscore =
                new AdminScraperService.ManualRunResponse("highscores", "daily", true, "accepted", Instant.parse("2026-06-05T12:00:02Z"));

        when(adminScrapers.status()).thenReturn(status);
        when(adminScrapers.highscoreBackoffStatus()).thenReturn(backoff);
        when(adminScrapers.resetHighscoreBackoff()).thenReturn(backoff);
        when(adminScrapers.triggerWorlds()).thenReturn(worlds);
        when(adminScrapers.triggerGuilds()).thenReturn(guilds);
        when(adminScrapers.triggerHighscorePlan("daily")).thenReturn(highscore);

        assertThat(controller.status()).isSameAs(status);
        assertThat(controller.highscoreBackoff()).isSameAs(backoff);
        assertThat(controller.resetHighscoreBackoff()).isSameAs(backoff);
        assertAccepted(controller.runWorlds(), worlds);
        assertAccepted(controller.runGuilds(), guilds);
        assertAccepted(controller.runHighscorePlan("daily"), highscore);
    }

    @Test
    void adminControllerMapsRemainingAcceptedTriggerErrors() {
        AdminScraperService adminScrapers = mock(AdminScraperService.class);
        AdminScraperController controller = new AdminScraperController(adminScrapers);

        when(adminScrapers.triggerWorlds()).thenThrow(new IllegalStateException("already running"));
        when(adminScrapers.triggerHighscorePlan("missing")).thenThrow(new IllegalArgumentException("missing plan"));

        assertStatus(HttpStatus.CONFLICT, controller::runWorlds);
        assertStatus(HttpStatus.BAD_REQUEST, () -> controller.runHighscorePlan("missing"));
    }

    @Test
    void highscoreControllerCoversSuccessBranchesAndBadRequestMappings() {
        HighscoreApiQueryService highscores = mock(HighscoreApiQueryService.class);
        HighscoreController controller = new HighscoreController(highscores);
        LocalDate day = LocalDate.parse("2026-06-05");

        when(highscores.findExperienceRanks("Antica", day, 0, 10)).thenReturn(List.of());
        when(highscores.findCurrent("Antica", StatCategory.MAGIC_LEVEL, 4, 10)).thenReturn(List.of());
        when(highscores.findExperienceDaily("Antica", day, 0, 10)).thenReturn(List.of());
        when(highscores.findExperienceGains("Antica", day.minusDays(1), day, 0, 10)).thenReturn(List.of());
        when(highscores.findHistory("Antica", StatCategory.MAGIC_LEVEL, "Knight", 4, day.minusDays(2), day, 10)).thenReturn(List.of());

        assertThat(controller.getHighscores("Antica", StatCategory.EXPERIENCE, 0, day, 10)).isEmpty();
        assertThat(controller.getHighscores("Antica", StatCategory.MAGIC_LEVEL, 4, day, 10)).isEmpty();
        assertThat(controller.getExperienceDaily("Antica", day, 0, 10)).isEmpty();
        assertThat(controller.getExperienceGains("Antica", day.minusDays(1), day, 0, 10)).isEmpty();
        assertThat(controller.getHistory("Antica", StatCategory.MAGIC_LEVEL, "Knight", 4, day.minusDays(2), day, 10)).isEmpty();

        HighscoreApiQueryService failing = mock(HighscoreApiQueryService.class);
        HighscoreController failingController = new HighscoreController(failing);
        when(failing.findExperienceRanks("Broken", day, 0, 10)).thenThrow(new IllegalArgumentException("bad exp ranks"));
        when(failing.findExperienceDaily("Broken", day, 0, 10)).thenThrow(new IllegalArgumentException("bad exp daily"));
        when(failing.findExperienceGains("Broken", day.minusDays(1), day, 0, 10)).thenThrow(new IllegalArgumentException("bad gains"));
        when(failing.findCurrent("Broken", StatCategory.MAGIC_LEVEL, 4, 10)).thenThrow(new IllegalArgumentException("bad current"));
        when(failing.findHistory("Broken", StatCategory.MAGIC_LEVEL, null, 4, null, null, 10)).thenThrow(new IllegalArgumentException("bad history"));

        assertStatus(HttpStatus.BAD_REQUEST, () -> failingController.getHighscores("Broken", StatCategory.EXPERIENCE, 0, day, 10));
        assertStatus(HttpStatus.BAD_REQUEST, () -> failingController.getExperienceDaily("Broken", day, 0, 10));
        assertStatus(HttpStatus.BAD_REQUEST, () -> failingController.getExperienceGains("Broken", day.minusDays(1), day, 0, 10));
        assertStatus(HttpStatus.BAD_REQUEST, () -> failingController.getCurrent("Broken", StatCategory.MAGIC_LEVEL, 4, 10));
        assertStatus(HttpStatus.BAD_REQUEST, () -> failingController.getHistory("Broken", StatCategory.MAGIC_LEVEL, null, 4, null, null, 10));
    }

    @Test
    void guildControllerMapsEventLookupErrorsToNotFound() {
        GuildQueryService guilds = mock(GuildQueryService.class);
        GuildController controller = new GuildController(guilds, mock(GuildScrapeService.class));
        Instant from = Instant.parse("2026-06-05T10:00:00Z");
        Instant to = Instant.parse("2026-06-05T12:00:00Z");

        when(guilds.findEvents("Missing", "Knight", GuildMembershipEventType.LEFT, from, to, 25))
                .thenThrow(new IllegalArgumentException("missing guild"));

        assertStatus(HttpStatus.NOT_FOUND, () -> controller.getGuildEvents("Missing", GuildMembershipEventType.LEFT, "Knight", from, to, 25));
    }

    private static void assertAccepted(ResponseEntity<AdminScraperService.ManualRunResponse> response,
                                       AdminScraperService.ManualRunResponse expectedBody) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isSameAs(expectedBody);
    }

    private static void assertStatus(HttpStatus status, ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(status));
    }
}
