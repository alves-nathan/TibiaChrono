package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.HighscoreApiQueryService;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/highscores")
public class HighscoreController {
    private final HighscoreApiQueryService highscores;

    public HighscoreController(HighscoreApiQueryService highscores) {
        this.highscores = highscores;
    }

    /**
     * Backward-compatible endpoint.
     *
     * EXPERIENCE reads from the compact EXP rank/daily tables.
     * Non-EXP categories read from highscore_current_records.
     */
    @GetMapping
    public List<?> getHighscores(
            @RequestParam String world,
            @RequestParam StatCategory category,
            @RequestParam(required = false) Integer vocationFilterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            if (category == StatCategory.EXPERIENCE) {
                return highscores.findExperienceRanks(world, date, vocationFilterId, limit);
            }
            return highscores.findCurrent(world, category, vocationFilterId, limit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/exp/daily")
    public List<HighscoreApiQueryService.ExperienceDailyView> getExperienceDaily(
            @RequestParam String world,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") Integer vocationFilterId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            return highscores.findExperienceDaily(world, date, vocationFilterId, limit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/exp/ranks")
    public List<HighscoreApiQueryService.ExperienceDailyView> getExperienceRanks(
            @RequestParam String world,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") Integer vocationFilterId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            return highscores.findExperienceRanks(world, date, vocationFilterId, limit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/exp/gains")
    public List<HighscoreApiQueryService.ExperienceGainView> getExperienceGains(
            @RequestParam String world,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") Integer vocationFilterId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            return highscores.findExperienceGains(world, startDate, endDate, vocationFilterId, limit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/current")
    public List<HighscoreApiQueryService.CurrentHighscoreView> getCurrent(
            @RequestParam String world,
            @RequestParam StatCategory category,
            @RequestParam(defaultValue = "0") Integer vocationFilterId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            return highscores.findCurrent(world, category, vocationFilterId, limit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/history")
    public List<HighscoreApiQueryService.PeriodHighscoreView> getHistory(
            @RequestParam String world,
            @RequestParam StatCategory category,
            @RequestParam(required = false) String characterName,
            @RequestParam(defaultValue = "0") Integer vocationFilterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            return highscores.findHistory(world, category, characterName, vocationFilterId, from, to, limit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
