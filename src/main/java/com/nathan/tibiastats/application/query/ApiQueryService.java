package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.StatCategory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@ReadModelService
@ReadModelComponent
public class ApiQueryService {
    private final CharacterOnlineReadModelService characterOnline;
    private final ScrapeJobReadModelService scrapeJobs;
    private final WorldReadModelService worlds;
    private final CharacterIdentityReadModelService characters;
    private final LegacyHighscoreReadModelService legacyHighscores;

    public ApiQueryService(CharacterOnlineReadModelService characterOnline,
                           ScrapeJobReadModelService scrapeJobs,
                           WorldReadModelService worlds,
                           CharacterIdentityReadModelService characters,
                           LegacyHighscoreReadModelService legacyHighscores) {
        this.characterOnline = characterOnline;
        this.scrapeJobs = scrapeJobs;
        this.worlds = worlds;
        this.characters = characters;
        this.legacyHighscores = legacyHighscores;
    }

    public List<WorldView> findWorlds() {
        return worlds.findWorlds();
    }

    public Optional<WorldView> findWorld(String name) {
        return worlds.findWorld(name);
    }

    public Optional<CharacterView> findCharacter(String name) {
        return characters.findCharacter(name);
    }

    public List<CharacterNameView> findCharacterNames(String name) {
        return characters.findCharacterNames(name);
    }

    public List<CharacterNameView> findCharacterNames(Long characterId) {
        return characters.findCharacterNames(characterId);
    }

    public List<HighscoreView> findCharacterHighscores(String characterName,
                                                       StatCategory category,
                                                       String world,
                                                       Integer vocationFilterId,
                                                       LocalDate from,
                                                       LocalDate to,
                                                       int limit) {
        return legacyHighscores.findCharacterHighscores(characterName, category, world, vocationFilterId, from, to, limit);
    }

    public List<HighscoreView> findHighscores(String world,
                                              StatCategory category,
                                              Integer vocationFilterId,
                                              LocalDate date,
                                              int limit) {
        return legacyHighscores.findHighscores(world, category, vocationFilterId, date, limit);
    }

    public List<ScrapeJobView> findScrapeJobs(String jobName, String status, int limit) {
        return scrapeJobs.findScrapeJobs(jobName, status, limit);
    }




    public List<CharacterOnlinePointView> findCharacterOnlineHistory(String characterName,
                                                                     String world,
                                                                     Instant from,
                                                                     Instant to,
                                                                     int limit) {
        return characterOnline.findHistory(characterName, world, from, to, limit);
    }

    public List<CharacterOnlineSessionView> findCharacterOnlineSessions(String characterName,
                                                                        String world,
                                                                        Instant from,
                                                                        Instant to,
                                                                        int maxGapMinutes,
                                                                        int limit) {
        return characterOnline.findSessions(characterName, world, from, to, maxGapMinutes, limit);
    }

    public List<CharacterOnlineWorldSummaryView> findCharacterOnlineWorldSummaries(String characterName,
                                                                                  String world,
                                                                                  Instant from,
                                                                                  Instant to,
                                                                                  int maxGapMinutes) {
        return characterOnline.findWorldSummaries(characterName, world, from, to, maxGapMinutes);
    }



    public record WorldView(
            Integer id,
            String name,
            String pvpType,
            String location,
            String onlineRecord,
            LocalDate creationDate,
            String transferType,
            String gameWorldType,
            Integer playersOnline,
            Instant lastScrapedAt
    ) {}

    public record CharacterView(
            Long id,
            String activeName,
            Integer level,
            String sex,
            String vocation,
            String vocationPromotionName,
            Integer achievementPoints,
            String residence,
            OffsetDateTime lastLogin,
            String accStatus,
            Instant creationDate,
            Instant detailsLastScrapedAt,
            String detailsLastScrapeStatus
    ) {}

    public record CharacterNameView(
            Long id,
            Long characterId,
            String name,
            Boolean active,
            Instant inactiveDate
    ) {}

    public record HighscoreView(
            Long id,
            Integer rank,
            String characterName,
            Long characterId,
            String world,
            String category,
            Integer vocationFilterId,
            LocalDate date,
            Long value,
            Instant scrapedAt
    ) {
        public String valueText() {
            return value == null ? null : value.toString();
        }
    }


    public record CharacterOnlinePointView(
            Long characterId,
            String characterName,
            Long scrapeId,
            String world,
            Instant timestamp,
            Integer playersOnline
    ) {}

    public record CharacterOnlineSessionView(
            Long characterId,
            String characterName,
            String world,
            Instant startedAt,
            Instant endedAt,
            Long observedMinutes,
            Integer samples
    ) {}

    public record CharacterOnlineWorldSummaryView(
            Long characterId,
            String characterName,
            String world,
            Integer appearances,
            Integer sessions,
            Long observedMinutes,
            Instant firstSeenAt,
            Instant lastSeenAt
    ) {}

    public record ScrapeJobView(
            Long id,
            String jobName,
            String status,
            Instant startedAt,
            Instant finishedAt,
            Long durationMs,
            Integer itemsProcessed,
            Integer itemsCreated,
            Integer itemsUpdated,
            Integer itemsFailed,
            String errorMessage
    ) {}
}
