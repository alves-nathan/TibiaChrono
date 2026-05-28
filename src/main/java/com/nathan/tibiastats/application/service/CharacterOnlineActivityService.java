package com.nathan.tibiastats.application.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class CharacterOnlineActivityService {
    private static final int DEFAULT_HISTORY_LIMIT = 1000;
    private static final int DEFAULT_SESSION_LIMIT = 100;
    private static final int DEFAULT_MAX_GAP_MINUTES = 15;

    private final ApiQueryService queries;

    public CharacterOnlineActivityService(ApiQueryService queries) {
        this.queries = queries;
    }

    public List<ApiQueryService.CharacterOnlinePointView> history(String characterName,
                                                                  String world,
                                                                  Instant from,
                                                                  Instant to,
                                                                  Integer limit) {
        return queries.findCharacterOnlineHistory(
                characterName,
                world,
                defaultFrom(from),
                defaultTo(to),
                limitOrDefault(limit, DEFAULT_HISTORY_LIMIT)
        );
    }

    public List<ApiQueryService.CharacterOnlineSessionView> sessions(String characterName,
                                                                     String world,
                                                                     Instant from,
                                                                     Instant to,
                                                                     Integer maxGapMinutes,
                                                                     Integer limit) {
        return queries.findCharacterOnlineSessions(
                characterName,
                world,
                defaultFrom(from),
                defaultTo(to),
                normalizeMaxGapMinutes(maxGapMinutes),
                limitOrDefault(limit, DEFAULT_SESSION_LIMIT)
        );
    }

    public CharacterOnlineActivitySummary summary(String characterName,
                                                  String world,
                                                  Instant from,
                                                  Instant to,
                                                  Integer maxGapMinutes) {
        ApiQueryService.CharacterView character = queries.findCharacter(characterName).orElseThrow();
        Instant effectiveFrom = defaultFrom(from);
        Instant effectiveTo = defaultTo(to);
        int effectiveMaxGapMinutes = normalizeMaxGapMinutes(maxGapMinutes);

        List<ApiQueryService.CharacterOnlineWorldSummaryView> worlds = queries.findCharacterOnlineWorldSummaries(
                characterName,
                world,
                effectiveFrom,
                effectiveTo,
                effectiveMaxGapMinutes
        );

        int appearances = worlds.stream().mapToInt(ApiQueryService.CharacterOnlineWorldSummaryView::appearances).sum();
        int sessions = worlds.stream().mapToInt(ApiQueryService.CharacterOnlineWorldSummaryView::sessions).sum();
        long observedMinutes = worlds.stream().mapToLong(ApiQueryService.CharacterOnlineWorldSummaryView::observedMinutes).sum();
        Instant firstSeenAt = worlds.stream()
                .map(ApiQueryService.CharacterOnlineWorldSummaryView::firstSeenAt)
                .filter(value -> value != null)
                .min(Instant::compareTo)
                .orElse(null);
        Instant lastSeenAt = worlds.stream()
                .map(ApiQueryService.CharacterOnlineWorldSummaryView::lastSeenAt)
                .filter(value -> value != null)
                .max(Instant::compareTo)
                .orElse(null);

        return new CharacterOnlineActivitySummary(
                character.id(),
                character.activeName(),
                normalizeBlank(world),
                effectiveFrom,
                effectiveTo,
                effectiveMaxGapMinutes,
                appearances,
                sessions,
                observedMinutes,
                firstSeenAt,
                lastSeenAt,
                worlds
        );
    }

    public Instant defaultFrom(Instant from) {
        return from == null ? Instant.now().minusSeconds(24 * 60 * 60) : from;
    }

    public Instant defaultTo(Instant to) {
        return to == null ? Instant.now() : to;
    }

    public int normalizeMaxGapMinutes(Integer maxGapMinutes) {
        if (maxGapMinutes == null || maxGapMinutes <= 0) {
            return DEFAULT_MAX_GAP_MINUTES;
        }
        return Math.min(maxGapMinutes, 24 * 60);
    }

    private int limitOrDefault(Integer limit, int defaultValue) {
        if (limit == null || limit <= 0) {
            return defaultValue;
        }
        return limit;
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CharacterOnlineActivitySummary(
            Long characterId,
            String characterName,
            String world,
            Instant from,
            Instant to,
            Integer maxGapMinutes,
            Integer appearances,
            Integer sessions,
            Long observedMinutes,
            Instant firstSeenAt,
            Instant lastSeenAt,
            List<ApiQueryService.CharacterOnlineWorldSummaryView> worlds
    ) {}
}
