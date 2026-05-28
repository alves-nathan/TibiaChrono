package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.ApiQueryService;
import com.nathan.tibiastats.application.service.HighscoreApiQueryService;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {
    private final ApiQueryService queries;
    private final HighscoreApiQueryService highscores;

    public CharacterController(ApiQueryService queries, HighscoreApiQueryService highscores) {
        this.queries = queries;
        this.highscores = highscores;
    }

    @GetMapping("/{name}")
    public ApiQueryService.CharacterView getCharacter(@PathVariable String name) {
        return queries.findCharacter(name).orElseThrow();
    }

    @GetMapping("/{name}/names")
    public List<ApiQueryService.CharacterNameView> getCharacterNames(@PathVariable String name) {
        return queries.findCharacterNames(name);
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
