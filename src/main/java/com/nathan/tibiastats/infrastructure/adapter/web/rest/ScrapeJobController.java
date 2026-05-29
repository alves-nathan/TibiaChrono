package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.query.ApiQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scrape-jobs")
public class ScrapeJobController {
    private final ApiQueryService queries;

    public ScrapeJobController(ApiQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<ApiQueryService.ScrapeJobView> listJobs(
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return queries.findScrapeJobs(jobName, status, limit);
    }
}
