package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.query.ApiQueryService;
import com.nathan.tibiastats.application.query.CharacterTimelineService;
import com.nathan.tibiastats.application.query.HighscoreApiQueryService;
import com.nathan.tibiastats.application.service.AdminScraperService;
import com.nathan.tibiastats.application.service.CharacterOnlineActivityService;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.when;

class RestControllerTailCoverageTest {
    private static final Instant FROM = Instant.parse("2026-06-05T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-05T12:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2026-06-05");

    @Test
    void characterControllerDelegatesTimelineQueriesAndMapsRangeAndMissingCharacterErrors() {
        ApiQueryService queries = mock(ApiQueryService.class);
        HighscoreApiQueryService highscores = mock(HighscoreApiQueryService.class);
        CharacterOnlineActivityService onlineActivity = mock(CharacterOnlineActivityService.class);
        CharacterTimelineService timeline = mock(CharacterTimelineService.class);
        CharacterController controller = new CharacterController(queries, highscores, onlineActivity, timeline);
        ApiQueryService.CharacterView character = character();
        List<ApiQueryService.CharacterNameView> names = List.of(new ApiQueryService.CharacterNameView(
                1L,
                10L,
                "Knight One",
                true,
                null
        ));
        List<CharacterTimelineService.CharacterDeathView> deaths = List.of(new CharacterTimelineService.CharacterDeathView(
                2L,
                10L,
                "Knight One",
                FROM,
                "a demon"
        ));
        when(queries.findCharacter("Knight One")).thenReturn(Optional.of(character));
        when(queries.findCharacter("Missing")).thenReturn(Optional.empty());
        when(queries.findCharacterNames("Knight One")).thenReturn(names);
        when(timeline.deaths("Knight One", FROM, TO, 25)).thenReturn(deaths);

        assertThat(controller.getCharacter("Knight One")).isSameAs(character);
        assertThat(controller.getCharacterNames("Knight One")).isSameAs(names);
        assertThat(controller.getCharacterDeaths("Knight One", FROM, TO, 25)).isSameAs(deaths);

        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> controller.getCharacterTimeline("Knight One", TO, FROM, 10, false, true, null)
        );
        assertStatus(
                HttpStatus.NOT_FOUND,
                () -> controller.getCharacterWorldHistory("Missing", FROM, TO, 10)
        );
    }

    @Test
    void characterControllerDelegatesOnlineActivityEndpointsAfterCharacterValidation() {
        ApiQueryService queries = mock(ApiQueryService.class);
        HighscoreApiQueryService highscores = mock(HighscoreApiQueryService.class);
        CharacterOnlineActivityService onlineActivity = mock(CharacterOnlineActivityService.class);
        CharacterTimelineService timeline = mock(CharacterTimelineService.class);
        CharacterController controller = new CharacterController(queries, highscores, onlineActivity, timeline);
        ApiQueryService.CharacterView character = character();
        List<ApiQueryService.CharacterOnlinePointView> history = List.of(new ApiQueryService.CharacterOnlinePointView(
                10L,
                "Knight One",
                20L,
                "Antica",
                FROM,
                100
        ));
        List<ApiQueryService.CharacterOnlineSessionView> sessions = List.of(new ApiQueryService.CharacterOnlineSessionView(
                10L,
                "Knight One",
                "Antica",
                FROM,
                TO,
                120L,
                5
        ));
        CharacterOnlineActivityService.CharacterOnlineActivitySummary summary =
                new CharacterOnlineActivityService.CharacterOnlineActivitySummary(
                        10L,
                        "Knight One",
                        "Antica",
                        FROM,
                        TO,
                        15,
                        5,
                        1,
                        120L,
                        FROM,
                        TO,
                        List.of()
                );
        when(queries.findCharacter("Knight One")).thenReturn(Optional.of(character));
        when(onlineActivity.history("Knight One", "Antica", FROM, TO, 50)).thenReturn(history);
        when(onlineActivity.sessions("Knight One", "Antica", FROM, TO, 15, 50)).thenReturn(sessions);
        when(onlineActivity.summary("Knight One", "Antica", FROM, TO, 15)).thenReturn(summary);

        assertThat(controller.getCharacterOnlineHistory("Knight One", "Antica", FROM, TO, 50)).isSameAs(history);
        assertThat(controller.getCharacterOnlineSessions("Knight One", "Antica", FROM, TO, 15, 50)).isSameAs(sessions);
        assertThat(controller.getCharacterActivitySummary("Knight One", "Antica", FROM, TO, 15)).isSameAs(summary);
    }

    @Test
    void characterControllerRoutesCharacterHighscoresAndMapsValidationErrors() {
        CharacterController controller = new CharacterController(
                mock(ApiQueryService.class),
                mock(HighscoreApiQueryService.class),
                mock(CharacterOnlineActivityService.class),
                mock(CharacterTimelineService.class)
        );
        HighscoreApiQueryService highscores = extractHighscores(controller);
        List<HighscoreApiQueryService.ExperienceDailyView> experience = new ArrayList<>();
        List<HighscoreApiQueryService.PeriodHighscoreView> history = new ArrayList<>();
        when(highscores.findCharacterExperienceDaily("Knight One", null, 0, null, null, 100)).thenReturn(experience);
        when(highscores.findHistory("Antica", StatCategory.MAGIC_LEVEL, "Knight One", 4, DAY.minusDays(1), DAY, 25))
                .thenReturn(history);
        when(highscores.findHistory("Broken", StatCategory.MAGIC_LEVEL, "Knight One", 4, null, null, 10))
                .thenThrow(new IllegalArgumentException("broken world"));

        assertThat(controller.getCharacterHighscores("Knight One", null, null, 0, null, null, 100))
                .isSameAs(experience);
        assertThat(controller.getCharacterHighscores(
                "Knight One",
                StatCategory.MAGIC_LEVEL,
                "Antica",
                4,
                DAY.minusDays(1),
                DAY,
                25
        )).isSameAs(history);

        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> controller.getCharacterHighscores("Knight One", StatCategory.MAGIC_LEVEL, " ", 0, null, null, 10)
        );
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> controller.getCharacterHighscores("Knight One", StatCategory.MAGIC_LEVEL, "Broken", 4, null, null, 10)
        );
    }

    @Test
    void highscoreControllerMapsValidationErrorsForEveryDedicatedEndpoint() {
        HighscoreApiQueryService highscores = mock(HighscoreApiQueryService.class);
        HighscoreController controller = new HighscoreController(highscores);
        when(highscores.findExperienceDaily(" ", DAY, 0, 10)).thenThrow(new IllegalArgumentException("world is required"));
        when(highscores.findExperienceRanks(" ", DAY, 0, 10)).thenThrow(new IllegalArgumentException("world is required"));
        when(highscores.findExperienceGains("Antica", DAY, DAY.minusDays(1), 0, 10))
                .thenThrow(new IllegalArgumentException("invalid date range"));
        when(highscores.findHistory("Antica", StatCategory.MAGIC_LEVEL, "Knight", 0, null, null, 10))
                .thenThrow(new IllegalArgumentException("history failed"));

        assertStatus(HttpStatus.BAD_REQUEST, () -> controller.getExperienceDaily(" ", DAY, 0, 10));
        assertStatus(HttpStatus.BAD_REQUEST, () -> controller.getExperienceRanks(" ", DAY, 0, 10));
        assertStatus(HttpStatus.BAD_REQUEST, () -> controller.getExperienceGains("Antica", DAY, DAY.minusDays(1), 0, 10));
        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> controller.getHistory("Antica", StatCategory.MAGIC_LEVEL, "Knight", 0, null, null, 10)
        );
    }

    @Test
    void simpleControllersDelegateRemainingEndpoints() {
        ApiQueryService queries = mock(ApiQueryService.class);
        WorldController worlds = new WorldController(queries);
        ScrapeJobController scrapeJobs = new ScrapeJobController(queries);
        AdminScraperService adminScrapers = mock(AdminScraperService.class);
        AdminScraperController admin = new AdminScraperController(adminScrapers);
        ApiQueryService.WorldView world = new ApiQueryService.WorldView(
                1,
                "Antica",
                "Open PvP",
                "EU",
                null,
                DAY,
                "blocked",
                "regular",
                100,
                FROM
        );
        ApiQueryService.ScrapeJobView job = new ApiQueryService.ScrapeJobView(
                1L,
                "WORLD_SCRAPER",
                "SUCCESS",
                FROM,
                TO,
                1000L,
                1,
                1,
                0,
                0,
                null
        );
        AdminScraperService.ManualRunResponse accepted = new AdminScraperService.ManualRunResponse(
                "character-details",
                null,
                true,
                "accepted",
                FROM
        );
        when(queries.findWorlds()).thenReturn(List.of(world));
        when(queries.findWorld("Antica")).thenReturn(Optional.of(world));
        when(queries.findScrapeJobs("WORLD_SCRAPER", "SUCCESS", 5)).thenReturn(List.of(job));
        when(adminScrapers.triggerCharacterDetails()).thenReturn(accepted);

        assertThat(worlds.listWorlds()).containsExactly(world);
        assertThat(worlds.getWorld("Antica")).isSameAs(world);
        assertThat(scrapeJobs.listJobs("WORLD_SCRAPER", "SUCCESS", 5)).containsExactly(job);
        ResponseEntity<AdminScraperService.ManualRunResponse> response = admin.runCharacterDetails();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isSameAs(accepted);
        verify(adminScrapers).triggerCharacterDetails();
    }

    private static ApiQueryService.CharacterView character() {
        return new ApiQueryService.CharacterView(
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
                FROM.minusSeconds(3600),
                FROM,
                "UPDATED"
        );
    }

    private static void assertStatus(HttpStatus status, ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(status));
    }

    private static HighscoreApiQueryService extractHighscores(CharacterController controller) {
        try {
            java.lang.reflect.Field field = CharacterController.class.getDeclaredField("highscores");
            field.setAccessible(true);
            return (HighscoreApiQueryService) field.get(controller);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
