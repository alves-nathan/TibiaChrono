package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.port.CharacterDetailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class CharacterDetailsBatchProcessor {
    private static final Logger log = LoggerFactory.getLogger(CharacterDetailsBatchProcessor.class);
    private static final String LOG_PREFIX = "[CHARACTER_DETAILS_SCRAPER]";

    private final CharacterDetailPort characterDetailPort;
    private final CharacterDetailsPersistenceService persistenceService;

    public CharacterDetailsBatchProcessor(CharacterDetailPort characterDetailPort,
                                          CharacterDetailsPersistenceService persistenceService) {
        this.characterDetailPort = characterDetailPort;
        this.persistenceService = persistenceService;
    }

    public ScrapeJobResult process(List<CharacterName> namesToRefresh) {
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
                    persistenceService.markAttempt(characterId, attemptedAt, "NOT_FOUND", null);
                    log.warn(
                            "{} Character details not found for id={} name='{}'; attempt marked so rotation can continue",
                            LOG_PREFIX,
                            characterId,
                            activeName
                    );
                    continue;
                }

                var characterDetails = details.get();
                if (!hasAnyUsefulDetail(characterDetails)) {
                    empty++;
                    persistenceService.markAttempt(characterId, attemptedAt, "EMPTY", "Profile fetched, but parser found no useful fields");
                    log.warn(
                            "{} Parser found no useful fields for id={} name='{}'; attempt marked so rotation can continue",
                            LOG_PREFIX,
                            characterId,
                            activeName
                    );
                    continue;
                }

                CharacterDetailsPersistenceService.SaveResult result = persistenceService.saveCharacterDetails(
                        characterId,
                        activeName,
                        characterDetails,
                        attemptedAt
                );
                if (result.changed()) {
                    updated++;
                } else {
                    unchanged++;
                }
            } catch (Exception e) {
                failed++;
                persistenceService.markAttempt(characterId, attemptedAt, "FAILED", e.getMessage());
                log.error(
                        "{} Failed to scrape character details for id={} name='{}'; attempt marked so rotation can continue",
                        LOG_PREFIX,
                        characterId,
                        activeName,
                        e
                );
            }
        }

        log.info(
                "{} Finished character detail scrape batch: updated={}, unchanged={}, notFound={}, empty={}, failed={}",
                LOG_PREFIX,
                updated,
                unchanged,
                notFound,
                empty,
                failed
        );
        int processed = updated + unchanged + notFound + empty + failed;
        return ScrapeJobResult.of(processed, 0, updated + unchanged, notFound + empty + failed);
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

    private boolean isNonBlank(String value) {
        return value != null && !value.trim().isBlank();
    }
}
