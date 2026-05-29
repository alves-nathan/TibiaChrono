package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Service
public class CharacterObservedNameResolver {
    static final int FORMER_NAME_SAFE_RESOLUTION_MONTHS = 6;

    private final CharacterRepositoryPort repo;
    private final CharacterNameParser nameParser;

    public CharacterObservedNameResolver(CharacterRepositoryPort repo, CharacterNameParser nameParser) {
        this.repo = repo;
        this.nameParser = nameParser;
    }

    /**
     * Resolve a plain mention. Inactive former names only resolve automatically inside
     * the 6-month safe window; after that CipSoft may release/reuse the name.
     */
    @Transactional
    public CharacterEntity resolveObservedName(String observedName) {
        String normalizedName = nameParser.normalizeName(observedName);

        return repo.findByAnyName(normalizedName, formerNameCutoff())
                .orElseGet(() -> createCharacterWithActiveName(normalizedName));
    }

    CharacterEntity createCharacterWithActiveName(String name) {
        CharacterEntity character = repo.save(new CharacterEntity());
        CharacterName characterName = CharacterName.createActive(name, character);
        repo.saveName(characterName);
        character.addName(characterName);
        return repo.save(character);
    }

    Instant formerNameCutoff() {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .minusMonths(FORMER_NAME_SAFE_RESOLUTION_MONTHS)
                .toInstant();
    }
}
