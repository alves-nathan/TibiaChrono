package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.AppProperties;
import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.model.Vocation;
import com.nathan.tibiastats.domain.port.CharacterDetailPort;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class CharacterDetailsService {
    private static final Logger log = LoggerFactory.getLogger(CharacterDetailsService.class);
    private static final String LOG_PREFIX = "[CHARACTER_DETAILS_SCRAPER]";

    private final CharacterRepositoryPort characterRepo;
    private final CharacterDetailPort characterDetailPort;
    private final CharacterNamingService characterNamingService;
    private final AppProperties appProperties;
    private final TransactionTemplate transactionTemplate;

    public CharacterDetailsService(CharacterRepositoryPort characterRepo,
                                   CharacterDetailPort characterDetailPort,
                                   CharacterNamingService characterNamingService,
                                   AppProperties appProperties,
                                   TransactionTemplate transactionTemplate) {
        this.characterRepo = characterRepo;
        this.characterDetailPort = characterDetailPort;
        this.characterNamingService = characterNamingService;
        this.appProperties = appProperties;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Kept with the old name for compatibility with the existing scheduler.
     * The implementation no longer selects only rows with missing fields. It selects the
     * least recently attempted characters, so the first character is only retried after
     * all other active characters have also been attempted.
     */
    public void updateMissingDetailsBatch() {
        int batchSize = Math.max(1, appProperties.getCharacterDetails().getBatchSize());
        List<CharacterName> namesToRefresh = characterRepo.findActiveNamesForDetailsRefresh(batchSize);

        if (namesToRefresh.isEmpty()) {
            log.info("{} No active characters available for detail scrape", LOG_PREFIX);
            return;
        }

        log.info("{} Starting character detail scrape batch: selectedNames={}, batchSize={}", LOG_PREFIX, namesToRefresh.size(), batchSize);

        int updated = 0;
        int unchanged = 0;
        int notFound = 0;
        int empty = 0;
        int failed = 0;

        for (CharacterName characterName : namesToRefresh) {
            Long characterId = characterName.getCharacter().getId();
            String activeName = characterName.getName();
            Instant attemptedAt = Instant.now();

            try {
                var details = characterDetailPort.fetchCharacterDetails(activeName);
                if (details.isEmpty()) {
                    notFound++;
                    markAttempt(characterId, attemptedAt, "NOT_FOUND", null);
                    log.warn("{} Character details not found for id={} name='{}'; attempt marked so rotation can continue", LOG_PREFIX, characterId, activeName);
                    continue;
                }

                var characterDetails = details.get();
                if (!hasAnyUsefulDetail(characterDetails)) {
                    empty++;
                    markAttempt(characterId, attemptedAt, "EMPTY", "Profile fetched, but parser found no useful fields");
                    log.warn("{} Parser found no useful fields for id={} name='{}'; attempt marked so rotation can continue", LOG_PREFIX, characterId, activeName);
                    continue;
                }

                SaveResult result = saveCharacterDetails(characterId, activeName, characterDetails, attemptedAt);
                if (result.changed()) {
                    updated++;
                } else {
                    unchanged++;
                }
            } catch (Exception e) {
                failed++;
                markAttempt(characterId, attemptedAt, "FAILED", e.getMessage());
                log.error("{} Failed to scrape character details for id={} name='{}'; attempt marked so rotation can continue", LOG_PREFIX, characterId, activeName, e);
            }
        }

        log.info(
                "{} Finished character detail scrape batch: updated={}, unchanged={}, notFound={}, empty={}, failed={}",
                LOG_PREFIX, updated, unchanged, notFound, empty, failed
        );
    }

    private SaveResult saveCharacterDetails(Long characterId,
                                            String requestedName,
                                            CharacterDetailPort.CharacterDetails details,
                                            Instant attemptedAt) {
        SaveResult result = transactionTemplate.execute(status -> {
            CharacterEntity originalCharacter = characterRepo.findById(characterId).orElse(null);
            if (originalCharacter == null) {
                return new SaveResult(false, "MISSING_LOCAL_CHARACTER");
            }

            String officialCurrentName = firstNonBlank(details.currentName(), requestedName);
            CharacterEntity character = reconcileOfficialNamesIfAvailable(originalCharacter, officialCurrentName, details.formerNames());

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

    @SuppressWarnings("unchecked")
    private CharacterEntity reconcileOfficialNamesIfAvailable(CharacterEntity originalCharacter,
                                                               String officialCurrentName,
                                                               List<String> formerNames) {
        try {
            Method method = characterNamingService.getClass().getMethod(
                    "reconcileOfficialNames",
                    CharacterEntity.class,
                    String.class,
                    List.class
            );
            Object result = method.invoke(characterNamingService, originalCharacter, officialCurrentName, formerNames);
            if (result instanceof CharacterEntity reconciledCharacter) {
                return reconciledCharacter;
            }
        } catch (NoSuchMethodException ignored) {
            // Older codebase: keep saving details on the current character.
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reconcile official character names", e);
        }
        return originalCharacter;
    }

    private void markAttempt(Long characterId, Instant attemptedAt, String status, String error) {
        transactionTemplate.executeWithoutResult(tx ->
                characterRepo.markDetailsScrapeAttempt(characterId, attemptedAt, status, error)
        );
    }

    private boolean hasAnyUsefulDetail(CharacterDetailPort.CharacterDetails details) {
        return isNonBlank(details.currentName())
                || (details.formerNames() != null && !details.formerNames().isEmpty())
                || details.sex() != null
                || details.vocation() != null
                || details.level() != null
                || details.achievementPoints() != null
                || isNonBlank(details.residence())
                || details.lastLogin() != null
                || isNonBlank(details.accountStatus())
                || details.creationDate() != null
                || isNonBlank(details.world());
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

    private record SaveResult(boolean changed, String status) {}
}
