package com.nathan.tibiastats.application.query;

import java.time.Instant;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class CharacterOnlineReadModelService {
    private final CharacterOnlineHistoryReadModelService history;
    private final CharacterOnlineSessionReadModelService sessions;
    private final CharacterOnlineWorldSummaryReadModelService worldSummaries;

    public CharacterOnlineReadModelService(CharacterOnlineHistoryReadModelService history,
                                           CharacterOnlineSessionReadModelService sessions,
                                           CharacterOnlineWorldSummaryReadModelService worldSummaries) {
        this.history = history;
        this.sessions = sessions;
        this.worldSummaries = worldSummaries;
    }

    public List<ApiQueryService.CharacterOnlinePointView> findHistory(String characterName,
                                                                      String world,
                                                                      Instant from,
                                                                      Instant to,
                                                                      int limit) {
        return history.findHistory(characterName, world, from, to, limit);
    }

    public List<ApiQueryService.CharacterOnlineSessionView> findSessions(String characterName,
                                                                         String world,
                                                                         Instant from,
                                                                         Instant to,
                                                                         int maxGapMinutes,
                                                                         int limit) {
        return sessions.findSessions(characterName, world, from, to, maxGapMinutes, limit);
    }

    public List<ApiQueryService.CharacterOnlineWorldSummaryView> findWorldSummaries(String characterName,
                                                                                   String world,
                                                                                   Instant from,
                                                                                   Instant to,
                                                                                   int maxGapMinutes) {
        return worldSummaries.findWorldSummaries(characterName, world, from, to, maxGapMinutes);
    }
}
