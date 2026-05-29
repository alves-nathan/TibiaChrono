package com.nathan.tibiastats.application.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ReadModelService
@ReadModelComponent
public class CharacterTimelineService {
    private static final int DEFAULT_LIMIT = 200;
    private static final int DEFAULT_MAX_GAP_MINUTES = 15;

    private final ApiQueryService queries;
    private final CharacterHistoryReadModelService history;
    private final CharacterTimelineCoreReadModelService coreTimeline;
    private final CharacterTimelineHighscoreReadModelService highscoreTimeline;

    public CharacterTimelineService(
            ApiQueryService queries,
            CharacterHistoryReadModelService history,
            CharacterTimelineCoreReadModelService coreTimeline,
            CharacterTimelineHighscoreReadModelService highscoreTimeline
    ) {
        this.queries = queries;
        this.history = history;
        this.coreTimeline = coreTimeline;
        this.highscoreTimeline = highscoreTimeline;
    }

    public List<CharacterDeathView> deaths(String characterName, Instant from, Instant to, Integer limit) {
        return history.deaths(characterName, from, to, limit);
    }

    public List<CharacterWorldHistoryView> worldHistory(String characterName, Instant from, Instant to, Integer limit) {
        return history.worldHistory(characterName, from, to, limit);
    }

    public List<CharacterGuildHistoryView> guildHistory(String characterName, Instant from, Instant to, Integer limit) {
        return history.guildHistory(characterName, from, to, limit);
    }

    public List<CharacterTimelineEvent> timeline(String characterName,
                                                 Instant from,
                                                 Instant to,
                                                 Integer limit,
                                                 boolean includeOnlineSessions,
                                                 boolean includeHighscores,
                                                 Integer maxGapMinutes) {
        int safeLimit = safeLimit(limit == null ? DEFAULT_LIMIT : limit);
        List<CharacterTimelineEvent> events = new ArrayList<>();
        events.addAll(coreTimeline.coreTimeline(characterName, from, to, safeLimit));

        if (includeHighscores) {
            events.addAll(highscoreTimeline.experienceTimeline(characterName, from, to, safeLimit));
            events.addAll(highscoreTimeline.nonExperienceHighscoreTimeline(characterName, from, to, safeLimit));
        }

        if (includeOnlineSessions) {
            int gap = normalizeMaxGapMinutes(maxGapMinutes);
            queries.findCharacterOnlineSessions(characterName, null, from, to, gap, safeLimit).stream()
                    .map(session -> new CharacterTimelineEvent(
                            session.characterId(),
                            session.characterName(),
                            "ONLINE_SESSION",
                            session.startedAt(),
                            session.world(),
                            null,
                            null,
                            null,
                            session.observedMinutes(),
                            null,
                            "Online session observed from scrape samples",
                            metadata(
                                    "endedAt", session.endedAt(),
                                    "observedMinutes", session.observedMinutes(),
                                    "samples", session.samples(),
                                    "maxGapMinutes", gap
                            )
                    ))
                    .filter(event -> event.occurredAt() != null)
                    .forEach(events::add);
        }

        return events.stream()
                .filter(event -> event.occurredAt() != null)
                .filter(event -> from == null || !event.occurredAt().isBefore(from))
                .filter(event -> to == null || !event.occurredAt().isAfter(to))
                .sorted(Comparator.comparing(CharacterTimelineEvent::occurredAt).reversed()
                        .thenComparing(CharacterTimelineEvent::eventType))
                .limit(safeLimit)
                .toList();
    }

    private int safeLimit(int requested) {
        if (requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, 1000);
    }

    private int normalizeMaxGapMinutes(Integer maxGapMinutes) {
        if (maxGapMinutes == null || maxGapMinutes <= 0) {
            return DEFAULT_MAX_GAP_MINUTES;
        }
        return Math.min(maxGapMinutes, 24 * 60);
    }

    private static Map<String, Object> metadata(Object... keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i] != null && keyValues[i + 1] != null) {
                values.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return values;
    }

    public record CharacterDeathView(
            Long id,
            Long characterId,
            String characterName,
            Instant deathDate,
            String killedBy
    ) {}

    public record CharacterWorldHistoryView(
            Long id,
            Long characterId,
            String characterName,
            String world,
            Boolean active,
            Instant inactiveDate
    ) {}

    public record CharacterGuildHistoryView(
            Long membershipId,
            Long characterId,
            String characterName,
            Long guildId,
            String guildName,
            String world,
            String rankName,
            String title,
            String vocation,
            Integer level,
            Instant joinedAt,
            Instant firstSeenAt,
            Instant lastSeenAt,
            Instant leftAt,
            Boolean active
    ) {}

    public record CharacterTimelineEvent(
            Long characterId,
            String characterName,
            String eventType,
            Instant occurredAt,
            String world,
            String guildName,
            String category,
            Integer rank,
            Long value,
            String title,
            String description,
            Map<String, Object> metadata
    ) {}
}
