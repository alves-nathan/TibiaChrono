package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.query.ApiQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/worlds")
public class WorldController {
    private final ApiQueryService queries;

    public WorldController(ApiQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<ApiQueryService.WorldView> listWorlds() {
        return queries.findWorlds();
    }

    @GetMapping("/{name}")
    public ApiQueryService.WorldView getWorld(@PathVariable String name) {
        return queries.findWorld(name).orElseThrow();
    }
}
