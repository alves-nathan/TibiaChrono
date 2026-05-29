package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.StatCategory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class HighscoreApiQueryService {
    private final HighscoreExperienceReadModelService experienceReadModel;
    private final HighscoreRecordReadModelService recordReadModel;

    public HighscoreApiQueryService(HighscoreExperienceReadModelService experienceReadModel,
                                    HighscoreRecordReadModelService recordReadModel) {
        this.experienceReadModel = experienceReadModel;
        this.recordReadModel = recordReadModel;
    }

    public List<ExperienceDailyView> findExperienceDaily(String world,
                                                         LocalDate date,
                                                         Integer vocationFilterId,
                                                         int limit) {
        return experienceReadModel.findExperienceDaily(world, date, vocationFilterId, limit);
    }

    public List<ExperienceDailyView> findExperienceRanks(String world,
                                                         LocalDate date,
                                                         Integer vocationFilterId,
                                                         int limit) {
        return experienceReadModel.findExperienceRanks(world, date, vocationFilterId, limit);
    }

    public List<ExperienceGainView> findExperienceGains(String world,
                                                        LocalDate startDate,
                                                        LocalDate endDate,
                                                        Integer vocationFilterId,
                                                        int limit) {
        return experienceReadModel.findExperienceGains(world, startDate, endDate, vocationFilterId, limit);
    }

    public List<CurrentHighscoreView> findCurrent(String world,
                                                  StatCategory category,
                                                  Integer vocationFilterId,
                                                  int limit) {
        return recordReadModel.findCurrent(world, category, vocationFilterId, limit);
    }

    public List<PeriodHighscoreView> findHistory(String world,
                                                 StatCategory category,
                                                 String characterName,
                                                 Integer vocationFilterId,
                                                 LocalDate from,
                                                 LocalDate to,
                                                 int limit) {
        return recordReadModel.findHistory(world, category, characterName, vocationFilterId, from, to, limit);
    }

    public List<ExperienceDailyView> findCharacterExperienceDaily(String characterName,
                                                                  String world,
                                                                  Integer vocationFilterId,
                                                                  LocalDate from,
                                                                  LocalDate to,
                                                                  int limit) {
        return experienceReadModel.findCharacterExperienceDaily(characterName, world, vocationFilterId, from, to, limit);
    }

    public record ExperienceDailyView(
            LocalDate date,
            Integer rank,
            String characterName,
            Long characterId,
            String world,
            Integer vocationFilterId,
            Long experience,
            Integer level,
            Integer firstSeenFilter,
            Instant scrapedAt
    ) {}

    public record ExperienceGainView(
            String characterName,
            Long characterId,
            String world,
            LocalDate startDate,
            LocalDate endDate,
            Long startExperience,
            Long endExperience,
            Long gain,
            Integer startRank,
            Integer endRank,
            Integer vocationFilterId
    ) {}

    public record CurrentHighscoreView(
            Long id,
            Integer rank,
            String characterName,
            Long characterId,
            String world,
            String category,
            Integer categoryId,
            Integer vocationFilterId,
            Long value,
            LocalDate firstSeenDate,
            LocalDate lastSeenDate,
            LocalDate lastChangedDate,
            Instant scrapedAt
    ) {}

    public record PeriodHighscoreView(
            Long id,
            Integer rank,
            String characterName,
            Long characterId,
            String world,
            String category,
            Integer categoryId,
            Integer vocationFilterId,
            Long value,
            LocalDate validFrom,
            LocalDate validUntil,
            Instant createdAt
    ) {}
}
