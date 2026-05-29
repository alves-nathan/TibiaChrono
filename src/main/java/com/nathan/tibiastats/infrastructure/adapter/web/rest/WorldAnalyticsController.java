package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.query.WorldOnlineAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/analytics/worlds")
public class WorldAnalyticsController {
    private final WorldOnlineAnalyticsService analytics;

    public WorldAnalyticsController(WorldOnlineAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/{world}/online/buckets")
    public List<WorldOnlineAnalyticsService.WorldOnlineBucketView> worldOnlineBuckets(
            @PathVariable String world,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "hour") String bucket
    ) {
        return analytics.buckets(world, from, to, bucket);
    }

    @GetMapping("/{world}/online/summary")
    public WorldOnlineAnalyticsService.WorldOnlineSummaryView worldOnlineSummary(
            @PathVariable String world,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return analytics.summary(world, from, to);
    }

    @GetMapping("/compare")
    public List<WorldOnlineAnalyticsService.WorldOnlineBucketView> compareWorlds(
            @RequestParam String worlds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "hour") String bucket
    ) {
        return analytics.compare(worlds, from, to, bucket);
    }

    @GetMapping("/ranking")
    public List<WorldOnlineAnalyticsService.WorldOnlineRankingView> rankWorlds(
            @RequestParam(defaultValue = "peak") String metric,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer limit
    ) {
        return analytics.ranking(metric, from, to, limit);
    }
}
