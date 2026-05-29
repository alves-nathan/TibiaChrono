package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.query.ApiQueryService;
import com.nathan.tibiastats.application.service.CharacterOnlineActivityService;
import com.nathan.tibiastats.application.query.CharacterTimelineService;
import com.nathan.tibiastats.application.query.HighscoreApiQueryService;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {
    private final ApiQueryService queries;
    private final HighscoreApiQueryService highscores;
    private final CharacterOnlineActivityService onlineActivity;
    private final CharacterTimelineService timeline;

    public CharacterController(ApiQueryService queries,
                               HighscoreApiQueryService highscores,
                               CharacterOnlineActivityService onlineActivity,
                               CharacterTimelineService timeline) {
        this.queries = queries;
        this.highscores = highscores;
        this.onlineActivity = onlineActivity;
        this.timeline = timeline;
    }

    @GetMapping("/{name}")
    public ApiQueryService.CharacterView getCharacter(@PathVariable String name) {
        return queries.findCharacter(name).orElseThrow();
    }

    @GetMapping("/{name}/names")
    public List<ApiQueryService.CharacterNameView> getCharacterNames(@PathVariable String name) {
        return queries.findCharacterNames(name);
    }



    @GetMapping("/{name}/deaths")
    public List<CharacterTimelineService.CharacterDeathView> getCharacterDeaths(
            @PathVariable String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit
    ) {
        requireCharacter(name);
        validateRange(from, to);
        return timeline.deaths(name, from, to, limit);
    }

    @GetMapping("/{name}/world-history")
    public List<CharacterTimelineService.CharacterWorldHistoryView> getCharacterWorldHistory(
            @PathVariable String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit
    ) {
        requireCharacter(name);
        validateRange(from, to);
        return timeline.worldHistory(name, from, to, limit);
    }

    @GetMapping("/{name}/guild-history")
    public List<CharacterTimelineService.CharacterGuildHistoryView> getCharacterGuildHistory(
            @PathVariable String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit
    ) {
        requireCharacter(name);
        validateRange(from, to);
        return timeline.guildHistory(name, from, to, limit);
    }

    @GetMapping("/{name}/timeline")
    public List<CharacterTimelineService.CharacterTimelineEvent> getCharacterTimeline(
            @PathVariable String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "false") boolean includeOnlineSessions,
            @RequestParam(defaultValue = "true") boolean includeHighscores,
            @RequestParam(required = false) Integer maxGapMinutes
    ) {
        requireCharacter(name);
        validateRange(from, to);
        return timeline.timeline(name, from, to, limit, includeOnlineSessions, includeHighscores, maxGapMinutes);
    }

    @GetMapping("/{name}/online-history")
    public List<ApiQueryService.CharacterOnlinePointView> getCharacterOnlineHistory(
            @PathVariable String name,
            @RequestParam(required = false) String world,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit
    ) {
        requireCharacter(name);
        validateRange(from, to);
        return onlineActivity.history(name, world, from, to, limit);
    }

    @GetMapping("/{name}/online-sessions")
    public List<ApiQueryService.CharacterOnlineSessionView> getCharacterOnlineSessions(
            @PathVariable String name,
            @RequestParam(required = false) String world,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer maxGapMinutes,
            @RequestParam(required = false) Integer limit
    ) {
        requireCharacter(name);
        validateRange(from, to);
        return onlineActivity.sessions(name, world, from, to, maxGapMinutes, limit);
    }

    @GetMapping("/{name}/activity-summary")
    public CharacterOnlineActivityService.CharacterOnlineActivitySummary getCharacterActivitySummary(
            @PathVariable String name,
            @RequestParam(required = false) String world,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer maxGapMinutes
    ) {
        requireCharacter(name);
        validateRange(from, to);
        return onlineActivity.summary(name, world, from, to, maxGapMinutes);
    }

    private ApiQueryService.CharacterView requireCharacter(String name) {
        return queries.findCharacter(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found: " + name));
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before or equal to to");
        }
    }

    @GetMapping("/{name}/highscores")
    public List<?> getCharacterHighscores(
            @PathVariable String name,
            @RequestParam(required = false) StatCategory category,
            @RequestParam(required = false) String world,
            @RequestParam(defaultValue = "0") Integer vocationFilterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            if (category == null || category == StatCategory.EXPERIENCE) {
                return highscores.findCharacterExperienceDaily(name, world, vocationFilterId, from, to, limit);
            }
            if (world == null || world.isBlank()) {
                throw new IllegalArgumentException("world is required for non-EXPERIENCE character highscore history");
            }
            return highscores.findHistory(world, category, name, vocationFilterId, from, to, limit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
