package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.ApiQueryService;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/highscores")
public class HighscoreController {
    private final ApiQueryService queries;

    public HighscoreController(ApiQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<ApiQueryService.HighscoreView> getHighscores(
            @RequestParam String world,
            @RequestParam StatCategory category,
            @RequestParam(required = false) Integer vocationFilterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return queries.findHighscores(world, category, vocationFilterId, date, limit);
    }
}
