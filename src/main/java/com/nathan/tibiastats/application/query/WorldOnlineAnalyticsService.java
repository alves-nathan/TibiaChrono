package com.nathan.tibiastats.application.query;

import java.time.Instant;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class WorldOnlineAnalyticsService {
    private final WorldOnlineBucketReadModelService buckets;
    private final WorldOnlineSummaryReadModelService summaries;
    private final WorldOnlineRankingReadModelService rankings;

    public WorldOnlineAnalyticsService(
            WorldOnlineBucketReadModelService buckets,
            WorldOnlineSummaryReadModelService summaries,
            WorldOnlineRankingReadModelService rankings
    ) {
        this.buckets = buckets;
        this.summaries = summaries;
        this.rankings = rankings;
    }

    public List<WorldOnlineBucketView> buckets(String world, Instant from, Instant to, String bucket) {
        return buckets.buckets(world, from, to, bucket);
    }

    public WorldOnlineSummaryView summary(String world, Instant from, Instant to) {
        return summaries.summary(world, from, to);
    }

    public List<WorldOnlineBucketView> compare(String worlds, Instant from, Instant to, String bucket) {
        return buckets.compare(worlds, from, to, bucket);
    }

    public List<WorldOnlineRankingView> ranking(String metric, Instant from, Instant to, Integer limit) {
        return rankings.ranking(metric, from, to, limit);
    }

    public record WorldOnlineBucketView(
            String world,
            Instant bucketStart,
            Integer samples,
            Double averagePlayersOnline,
            Integer minPlayersOnline,
            Integer maxPlayersOnline,
            Integer firstPlayersOnline,
            Integer lastPlayersOnline,
            Integer changePlayersOnline
    ) {}

    public record WorldOnlineSummaryView(
            String world,
            Integer samples,
            Instant firstScrapeAt,
            Instant lastScrapeAt,
            Integer minPlayersOnline,
            Integer peakPlayersOnline,
            Instant peakAt,
            Double averagePlayersOnline,
            Integer firstPlayersOnline,
            Integer latestPlayersOnline,
            Integer changePlayersOnline
    ) {
        static WorldOnlineSummaryView empty(String world) {
            return new WorldOnlineSummaryView(world, 0, null, null, null, null, null, null, null, null, null);
        }
    }

    public record WorldOnlineRankingView(
            String world,
            String metric,
            Double metricValue,
            Integer samples,
            Instant firstScrapeAt,
            Instant lastScrapeAt,
            Integer peakPlayersOnline,
            Double averagePlayersOnline,
            Integer firstPlayersOnline,
            Integer latestPlayersOnline,
            Integer changePlayersOnline
    ) {}
}
