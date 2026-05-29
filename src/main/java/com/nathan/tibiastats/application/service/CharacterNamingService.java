package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.CharacterName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CharacterNamingService {
    private final CharacterNameParser nameParser;
    private final CharacterObservedNameResolver observedNameResolver;
    private final CharacterIdentityReconciliationService reconciliationService;

    public CharacterNamingService(CharacterNameParser nameParser,
                                  CharacterObservedNameResolver observedNameResolver,
                                  CharacterIdentityReconciliationService reconciliationService) {
        this.nameParser = nameParser;
        this.observedNameResolver = observedNameResolver;
        this.reconciliationService = reconciliationService;
    }

    /**
     * Resolve a name observed in scrapes. If formerNames came from an official profile,
     * the current name and former names are reconciled as the same identity.
     */
    @Transactional
    public CharacterEntity ensureCharacterForName(String name, String formerNames) {
        List<String> parsedFormerNames = nameParser.parseFormerNames(formerNames, name);

        if (parsedFormerNames.isEmpty()) {
            return resolveObservedName(name);
        }

        return reconcileOfficialNames(name, parsedFormerNames);
    }

    /**
     * Resolve a plain mention. Inactive former names only resolve automatically inside
     * the safe window; after that CipSoft may release/reuse the name.
     */
    @Transactional
    public CharacterEntity resolveObservedName(String observedName) {
        return observedNameResolver.resolveObservedName(observedName);
    }

    @Transactional
    public CharacterEntity reconcileOfficialNames(String currentName, List<String> formerNames) {
        return reconciliationService.reconcileOfficialNames(currentName, formerNames);
    }

    /**
     * Official Tibia.com profile rule:
     * Name + Former Names refer to the same CharacterEntity and must not create duplicates.
     */
    @Transactional
    public CharacterEntity reconcileOfficialNames(CharacterEntity knownCharacter, String currentName, List<String> formerNames) {
        return reconciliationService.reconcileOfficialNames(knownCharacter, currentName, formerNames);
    }

    /** Existing entry point kept for older callers. */
    @Transactional
    public void handleRenamed(CharacterEntity character, String newActiveName, CharacterName oldName) {
        List<String> formerNames = oldName == null ? List.of() : List.of(oldName.getName());
        reconciliationService.reconcileOfficialNames(character, newActiveName, formerNames);
    }
}
