package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.query.GuildQueryService;
import com.nathan.tibiastats.application.query.GuildQueryViews;
import com.nathan.tibiastats.application.query.HighscoreApiQueryService;
import com.nathan.tibiastats.application.service.AdminScraperService;
import com.nathan.tibiastats.application.service.GuildScrapeService;
import com.nathan.tibiastats.domain.model.GuildMembershipEventType;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestControllerDelegationTest {
    @Test
    void adminScraperControllerDelegatesStatusBackoffResetAndAcceptedManualRuns() {
        AdminScraperService adminScrapers = mock(AdminScraperService.class);
        AdminScraperController controller = new AdminScraperController(adminScrapers);
        AdminScraperService.ScraperStatusResponse status = new AdminScraperService.ScraperStatusResponse(
                List.of(),
                List.of(),
                null
        );
        AdminScraperService.HighscoreBackoffStatus backoff = new AdminScraperService.HighscoreBackoffStatus(
                false,
                null,
                0L,
                0,
                0L,
                null,
                null,
                null,
                null
        );
        AdminScraperService.ManualRunResponse accepted = new AdminScraperService.ManualRunResponse(
                "worlds",
                null,
                true,
                "accepted",
                Instant.parse("2026-06-02T12:00:00Z")
        );
        when(adminScrapers.status()).thenReturn(status);
        when(adminScrapers.highscoreBackoffStatus()).thenReturn(backoff);
        when(adminScrapers.resetHighscoreBackoff()).thenReturn(backoff);
        when(adminScrapers.triggerWorlds()).thenReturn(accepted);

        assertThat(controller.status()).isSameAs(status);
        assertThat(controller.highscoreBackoff()).isSameAs(backoff);
        assertThat(controller.resetHighscoreBackoff()).isSameAs(backoff);
        ResponseEntity<AdminScraperService.ManualRunResponse> response = controller.runWorlds();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isSameAs(accepted);
    }

    @Test
    void adminScraperControllerMapsManualRunValidationAndConflictErrors() {
        AdminScraperService adminScrapers = mock(AdminScraperService.class);
        AdminScraperController controller = new AdminScraperController(adminScrapers);
        when(adminScrapers.triggerHighscorePlan("missing"))
                .thenThrow(new IllegalArgumentException("unknown plan"));
        when(adminScrapers.triggerGuilds()).thenThrow(new IllegalStateException("already running"));

        assertThatThrownBy(() -> controller.runHighscorePlan("missing"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(controller::runGuilds)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void highscoreControllerDelegatesBackwardCompatibleAndDedicatedEndpoints() {
        HighscoreApiQueryService highscores = mock(HighscoreApiQueryService.class);
        HighscoreController controller = new HighscoreController(highscores);
        LocalDate date = LocalDate.parse("2026-06-02");
        LocalDate startDate = LocalDate.parse("2026-06-01");
        LocalDate endDate = LocalDate.parse("2026-06-02");
        List<HighscoreApiQueryService.ExperienceDailyView> ranks = new ArrayList<>();
        List<HighscoreApiQueryService.ExperienceDailyView> daily = new ArrayList<>();
        List<HighscoreApiQueryService.ExperienceGainView> gains = new ArrayList<>();
        List<HighscoreApiQueryService.CurrentHighscoreView> current = new ArrayList<>();
        List<HighscoreApiQueryService.PeriodHighscoreView> history = new ArrayList<>();
        when(highscores.findExperienceRanks("Antica", date, 0, 25)).thenReturn(ranks);
        when(highscores.findExperienceDaily("Antica", date, 0, 25)).thenReturn(daily);
        when(highscores.findExperienceGains("Antica", startDate, endDate, 0, 25)).thenReturn(gains);
        when(highscores.findCurrent("Antica", StatCategory.MAGIC_LEVEL, 4, 10)).thenReturn(current);
        when(highscores.findHistory("Antica", StatCategory.MAGIC_LEVEL, "Knight", 4, startDate, endDate, 10))
                .thenReturn(history);

        assertThat(controller.getHighscores("Antica", StatCategory.EXPERIENCE, 0, date, 25)).isSameAs(ranks);
        assertThat(controller.getExperienceRanks("Antica", date, 0, 25)).isSameAs(ranks);
        assertThat(controller.getExperienceDaily("Antica", date, 0, 25)).isSameAs(daily);
        assertThat(controller.getExperienceGains("Antica", startDate, endDate, 0, 25)).isSameAs(gains);
        assertThat(controller.getHighscores("Antica", StatCategory.MAGIC_LEVEL, 4, null, 10)).isSameAs(current);
        assertThat(controller.getCurrent("Antica", StatCategory.MAGIC_LEVEL, 4, 10)).isSameAs(current);
        assertThat(controller.getHistory("Antica", StatCategory.MAGIC_LEVEL, "Knight", 4, startDate, endDate, 10))
                .isSameAs(history);
    }

    @Test
    void highscoreControllerMapsQueryValidationErrorsToBadRequest() {
        HighscoreApiQueryService highscores = mock(HighscoreApiQueryService.class);
        HighscoreController controller = new HighscoreController(highscores);
        when(highscores.findCurrent("Unknown", StatCategory.SWORD_FIGHTING, 0, 10))
                .thenThrow(new IllegalArgumentException("unknown world"));

        assertThatThrownBy(() -> controller.getCurrent("Unknown", StatCategory.SWORD_FIGHTING, 0, 10))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void guildControllerDelegatesQueriesAndScrapeEndpoints() {
        GuildQueryService guilds = mock(GuildQueryService.class);
        GuildScrapeService scraper = mock(GuildScrapeService.class);
        GuildController controller = new GuildController(guilds, scraper);
        Instant now = Instant.parse("2026-06-02T12:00:00Z");
        GuildQueryViews.GuildView guild = new GuildQueryViews.GuildView(
                1L,
                "Raw Raw",
                "Antica",
                "desc",
                null,
                null,
                LocalDate.parse("2024-01-01"),
                true,
                now,
                now
        );
        GuildQueryViews.GuildMemberView member = new GuildQueryViews.GuildMemberView(
                2L,
                1L,
                "Raw Raw",
                3L,
                "Knight",
                "Leader",
                "Boss",
                "Elite Knight",
                400,
                now,
                LocalDate.parse("2025-01-01"),
                now,
                now,
                null,
                true
        );
        GuildQueryViews.GuildMembershipEventView event = new GuildQueryViews.GuildMembershipEventView(
                4L,
                GuildMembershipEventType.JOINED.name(),
                3L,
                "Knight",
                null,
                null,
                1L,
                "Raw Raw",
                now,
                "joined guild"
        );
        GuildScrapeService.GuildListResult listResult = new GuildScrapeService.GuildListResult(2, 1, 1);
        GuildScrapeService.GuildDetailResult detailResult = new GuildScrapeService.GuildDetailResult(
                "Raw Raw",
                10,
                2,
                3,
                1,
                0
        );
        when(guilds.findGuilds("Antica", true)).thenReturn(List.of(guild));
        when(guilds.findGuild("Raw Raw")).thenReturn(guild);
        when(guilds.findMembers("Raw Raw", true)).thenReturn(List.of(member));
        when(guilds.findEvents("Raw Raw", "Knight", GuildMembershipEventType.JOINED, now.minusSeconds(60), now, 20))
                .thenReturn(List.of(event));
        when(scraper.updateGuildListForWorld("Antica")).thenReturn(listResult);
        when(scraper.updateGuildDetail("Raw Raw")).thenReturn(detailResult);

        assertThat(controller.listGuilds("Antica", true)).containsExactly(guild);
        assertThat(controller.getGuild("Raw Raw")).isSameAs(guild);
        assertThat(controller.getGuildMembers("Raw Raw", true)).containsExactly(member);
        assertThat(controller.getGuildEvents(
                "Raw Raw",
                GuildMembershipEventType.JOINED,
                "Knight",
                now.minusSeconds(60),
                now,
                20
        )).containsExactly(event);
        assertThat(controller.scrapeGuildList("Antica")).isSameAs(listResult);
        assertThat(controller.scrapeGuildDetail("Raw Raw")).isSameAs(detailResult);
        verify(scraper).updateGuildListForWorld("Antica");
        verify(scraper).updateGuildDetail("Raw Raw");
    }

    @Test
    void guildControllerMapsMissingGuildQueriesToNotFound() {
        GuildQueryService guilds = mock(GuildQueryService.class);
        GuildController controller = new GuildController(guilds, mock(GuildScrapeService.class));
        when(guilds.findGuild("Missing")).thenThrow(new IllegalArgumentException("not found"));
        when(guilds.findMembers("Missing", true)).thenThrow(new IllegalArgumentException("not found"));

        assertThatThrownBy(() -> controller.getGuild("Missing"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> controller.getGuildMembers("Missing", true))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
