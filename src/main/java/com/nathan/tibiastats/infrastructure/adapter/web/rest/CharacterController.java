package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.ApiQueryService;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {
    private final ApiQueryService queries;

    public CharacterController(ApiQueryService queries) {
        this.queries = queries;
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
    public List<ApiQueryService.HighscoreView> getCharacterHighscores(
            @PathVariable String name,
            @RequestParam(required = false) StatCategory category,
            @RequestParam(required = false) String world,
            @RequestParam(required = false) Integer vocationFilterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return queries.findCharacterHighscores(name, category, world, vocationFilterId, from, to, limit);
    }
}
