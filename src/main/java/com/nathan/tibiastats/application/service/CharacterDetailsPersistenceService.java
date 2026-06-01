package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.Vocation;
import com.nathan.tibiastats.domain.port.CharacterDetailPort;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;

@Service
public class CharacterDetailsPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(CharacterDetailsPersistenceService.class);
    private static final String LOG_PREFIX = "[CHARACTER_DETAILS_SCRAPER]";

    private final CharacterRepositoryPort characterRepo;
    private final CharacterNamingService characterNamingService;
    private final TransactionTemplate transactionTemplate;

    public CharacterDetailsPersistenceService(CharacterRepositoryPort characterRepo,
                                              CharacterNamingService characterNamingService,
                                              TransactionTemplate transactionTemplate) {
        this.characterRepo = characterRepo;
        this.characterNamingService = characterNamingService;
        this.transactionTemplate = transactionTemplate;
    }

    public SaveResult saveCharacterDetails(Long characterId,
                                           String requestedName,
                                           CharacterDetailPort.CharacterDetails details,
                                           Instant attemptedAt) {
        SaveResult result = transactionTemplate.execute(status -> {
            CharacterEntity originalCharacter = characterRepo.findById(characterId).orElse(null);
            if (originalCharacter == null) {
                return new SaveResult(false, "MISSING_LOCAL_CHARACTER");
            }

            String officialCurrentName = firstNonBlank(details.currentName(), requestedName);
            CharacterEntity character = characterNamingService.reconcileOfficialNames(
                    originalCharacter,
                    officialCurrentName,
                    details.formerNames()
            );

            boolean characterChanged = character.getId() != null
                    && originalCharacter.getId() != null
                    && !Objects.equals(originalCharacter.getId(), character.getId());

            if (details.sex() != null && !Objects.equals(character.getSex(), details.sex())) {
                character.setSex(details.sex());
                characterChanged = true;
            }

            if (details.level() != null && !Objects.equals(character.getLevel(), details.level())) {
                character.setLevel(details.level());
                characterChanged = true;
            }

            if (details.vocation() != null && !details.vocation().isBlank()) {
                var vocation = characterRepo.findVocationByNameOrPromotionName(details.vocation().trim());
                if (vocation.isPresent() && !sameVocation(character.getVocation(), vocation.get())) {
                    character.setVocation(vocation.get());
                    characterChanged = true;
                }
            }

            if (details.achievementPoints() != null
                    && !Objects.equals(character.getAchievementPoints(), details.achievementPoints())) {
                character.setAchievementPoints(details.achievementPoints());
                characterChanged = true;
            }

            if (isNonBlank(details.residence()) && !Objects.equals(character.getResidence(), details.residence().trim())) {
                character.setResidence(details.residence().trim());
                characterChanged = true;
            }

            if (details.lastLogin() != null && !Objects.equals(character.getLastLogin(), details.lastLogin())) {
                character.setLastLogin(details.lastLogin());
                characterChanged = true;
            }

            if (isNonBlank(details.accountStatus()) && !Objects.equals(character.getAccStatus(), details.accountStatus().trim())) {
                character.setAccStatus(details.accountStatus().trim());
                characterChanged = true;
            }

            if (details.creationDate() != null && !Objects.equals(character.getCreationDate(), details.creationDate())) {
                character.setCreationDate(details.creationDate());
                characterChanged = true;
            }

            character.setDetailsLastScrapedAt(attemptedAt);
            character.setDetailsLastScrapeStatus(characterChanged ? "UPDATED" : "UNCHANGED");
            character.setDetailsLastScrapeError(null);
            characterRepo.save(character);

            log.info(
                    "{} Details attempt saved for characterId={} officialName='{}' status={} sex={} vocation={} level={} residence={} accStatus={} created={}",
                    LOG_PREFIX,
                    character.getId(),
                    officialCurrentName,
                    character.getDetailsLastScrapeStatus(),
                    character.getSex(),
                    character.getVocation() != null ? character.getVocation().getName() : null,
                    character.getLevel(),
                    character.getResidence(),
                    character.getAccStatus(),
                    character.getCreationDate()
            );

            return new SaveResult(characterChanged, character.getDetailsLastScrapeStatus());
        });

        return result == null ? new SaveResult(false, "UNKNOWN") : result;
    }

    public void markAttempt(Long characterId, Instant attemptedAt, String status, String error) {
        transactionTemplate.executeWithoutResult(tx ->
                characterRepo.markDetailsScrapeAttempt(characterId, attemptedAt, status, error)
        );
    }

    private boolean sameVocation(Vocation current, Vocation scraped) {
        return current != null && scraped != null && Objects.equals(current.getId(), scraped.getId());
    }

    private String firstNonBlank(String first, String fallback) {
        return isNonBlank(first) ? first.trim() : fallback;
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.trim().isBlank();
    }

    public record SaveResult(boolean changed, String status) {}
}
