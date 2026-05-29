package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.model.CharacterNameNormalizer;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
public class CharacterIdentityReconciliationService {
    private final CharacterRepositoryPort repo;
    private final CharacterNameParser nameParser;
    private final CharacterObservedNameResolver observedNameResolver;
    private final CharacterIdentityMergeService mergeService;

    public CharacterIdentityReconciliationService(CharacterRepositoryPort repo,
                                                  CharacterNameParser nameParser,
                                                  CharacterObservedNameResolver observedNameResolver,
                                                  CharacterIdentityMergeService mergeService) {
        this.repo = repo;
        this.nameParser = nameParser;
        this.observedNameResolver = observedNameResolver;
        this.mergeService = mergeService;
    }

    @Transactional
    public CharacterEntity reconcileOfficialNames(String currentName, List<String> formerNames) {
        currentName = CharacterNameNormalizer.normalize(currentName);
        formerNames = CharacterNameNormalizer.normalizeMany(formerNames);
        if (currentName == null || currentName.isBlank()) {
            throw new IllegalArgumentException("Current character name cannot be blank after normalization");
        }
        return reconcileOfficialNames(null, currentName, formerNames);
    }

    /**
     * Official Tibia.com profile rule:
     * Name + Former Names refer to the same CharacterEntity and must not create duplicates.
     */
    @Transactional
    public CharacterEntity reconcileOfficialNames(CharacterEntity knownCharacter, String currentName, List<String> formerNames) {
        String normalizedCurrentName = nameParser.normalizeName(currentName);
        List<String> normalizedFormerNames = nameParser.normalizeFormerNames(formerNames, normalizedCurrentName);
        List<CharacterEntity> candidates = findIdentityCandidates(knownCharacter, normalizedCurrentName, normalizedFormerNames);

        CharacterEntity canonical = findCanonicalCharacter(candidates)
                .orElseGet(() -> observedNameResolver.createCharacterWithActiveName(normalizedCurrentName));

        canonical = mergeService.mergeDuplicateCandidatesIntoCanonical(
                canonical,
                candidates,
                normalizedCurrentName,
                normalizedFormerNames
        );

        Instant now = Instant.now();
        deactivateActiveNamesExcept(canonical, normalizedCurrentName, now);
        ensureActiveName(canonical, normalizedCurrentName);

        for (String formerName : normalizedFormerNames) {
            ensureInactiveName(canonical, formerName, now);
        }

        return repo.save(canonical);
    }

    private Optional<CharacterEntity> findCanonicalCharacter(List<CharacterEntity> candidates) {
        return candidates.stream()
                .filter(c -> c.getId() != null)
                .min((a, b) -> Long.compare(a.getId(), b.getId()));
    }

    private List<CharacterEntity> findIdentityCandidates(CharacterEntity knownCharacter,
                                                         String currentName,
                                                         List<String> formerNames) {
        LinkedHashSet<Long> seenIds = new LinkedHashSet<>();
        List<CharacterEntity> candidates = new ArrayList<>();

        addCandidate(candidates, seenIds, knownCharacter);
        repo.findByAnyName(currentName, observedNameResolver.formerNameCutoff()).ifPresent(c -> addCandidate(candidates, seenIds, c));

        for (String formerName : formerNames) {
            repo.findByAnyName(formerName, observedNameResolver.formerNameCutoff()).ifPresent(c -> addCandidate(candidates, seenIds, c));
        }

        return candidates;
    }

    private void addCandidate(List<CharacterEntity> candidates, LinkedHashSet<Long> seenIds, CharacterEntity candidate) {
        if (candidate == null || candidate.getId() == null || !seenIds.add(candidate.getId())) {
            return;
        }
        candidates.add(candidate);
    }

    private void deactivateActiveNamesExcept(CharacterEntity character, String activeName, Instant when) {
        for (CharacterName name : repo.findNamesForCharacter(character.getId())) {
            if (name.isActive() && !nameParser.sameName(name.getName(), activeName)) {
                name.deactivate(when);
                repo.saveName(name);
            }
        }
    }

    private void ensureActiveName(CharacterEntity character, String activeName) {
        Optional<CharacterName> existingForCharacter = repo.findNameForCharacter(character.getId(), activeName);

        if (existingForCharacter.isPresent()) {
            CharacterName existing = existingForCharacter.get();
            if (!existing.isActive()) {
                existing.activate();
                repo.saveName(existing);
            }
            return;
        }

        CharacterName newActiveName = CharacterName.createActive(activeName, character);
        repo.saveName(newActiveName);
        character.addName(newActiveName);
    }

    private void ensureInactiveName(CharacterEntity character, String formerName, Instant when) {
        if (nameParser.isBlank(formerName)) {
            return;
        }

        Optional<CharacterName> existingForCharacter = repo.findNameForCharacter(character.getId(), formerName);

        if (existingForCharacter.isPresent()) {
            CharacterName existing = existingForCharacter.get();
            if (existing.isActive()) {
                existing.deactivate(when);
                repo.saveName(existing);
            }
            return;
        }

        CharacterName inactiveName = CharacterName.createInactive(formerName, character, when);
        repo.saveName(inactiveName);
        character.addName(inactiveName);
    }
}
