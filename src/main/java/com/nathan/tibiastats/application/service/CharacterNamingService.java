package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.model.CharacterNameNormalizer;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
public class CharacterNamingService {
    private static final Logger log = LoggerFactory.getLogger(CharacterNamingService.class);
    private static final int FORMER_NAME_SAFE_RESOLUTION_MONTHS = 6;

    private final CharacterRepositoryPort repo;

    public CharacterNamingService(CharacterRepositoryPort repo) {
        this.repo = repo;
    }

    /**
     * Resolve a name observed in scrapes. If formerNames came from an official profile,
     * the current name and former names are reconciled as the same identity.
     */
    @Transactional
    public CharacterEntity ensureCharacterForName(String name, String formerNames) {
        List<String> parsedFormerNames = parseFormerNames(formerNames, name);

        if (parsedFormerNames.isEmpty()) {
            return resolveObservedName(name);
        }

        return reconcileOfficialNames(name, parsedFormerNames);
    }

    /**
     * Resolve a plain mention. Inactive former names only resolve automatically inside
     * the 6-month safe window; after that CipSoft may release/reuse the name.
     */
    @Transactional
    public CharacterEntity resolveObservedName(String observedName) {
        String normalizedName = normalizeName(observedName);

        return repo.findByAnyName(normalizedName, formerNameCutoff())
                .orElseGet(() -> createCharacterWithActiveName(normalizedName));
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
        String normalizedCurrentName = normalizeName(currentName);
        List<String> normalizedFormerNames = normalizeFormerNames(formerNames, normalizedCurrentName);

        CharacterEntity canonical = findCanonicalCharacter(knownCharacter, normalizedCurrentName, normalizedFormerNames)
                .orElseGet(() -> createCharacterWithActiveName(normalizedCurrentName));

        canonical = mergeDuplicateCandidatesIntoCanonical(canonical, knownCharacter, normalizedCurrentName, normalizedFormerNames);

        Instant now = Instant.now();
        deactivateActiveNamesExcept(canonical, normalizedCurrentName, now);
        ensureActiveName(canonical, normalizedCurrentName);

        for (String formerName : normalizedFormerNames) {
            ensureInactiveName(canonical, formerName, now);
        }

        return repo.save(canonical);
    }

    /** Existing entry point kept for older callers. */
    @Transactional
    public void handleRenamed(CharacterEntity character, String newActiveName, CharacterName oldName) {
        List<String> formerNames = oldName == null ? List.of() : List.of(oldName.getName());
        reconcileOfficialNames(character, newActiveName, formerNames);
    }

    private Optional<CharacterEntity> findCanonicalCharacter(CharacterEntity knownCharacter,
                                                            String currentName,
                                                            List<String> formerNames) {
        List<CharacterEntity> candidates = findIdentityCandidates(knownCharacter, currentName, formerNames);

        return candidates.stream()
                .filter(c -> c.getId() != null)
                .min((a, b) -> Long.compare(a.getId(), b.getId()));
    }

    private CharacterEntity mergeDuplicateCandidatesIntoCanonical(CharacterEntity canonical,
                                                                  CharacterEntity knownCharacter,
                                                                  String currentName,
                                                                  List<String> formerNames) {
        List<CharacterEntity> candidates = findIdentityCandidates(knownCharacter, currentName, formerNames);
        CharacterEntity managedCanonical = reload(canonical);

        for (CharacterEntity candidate : candidates) {
            if (candidate.getId() == null || candidate.getId().equals(managedCanonical.getId())) {
                continue;
            }

            CharacterEntity duplicate = reload(candidate);
            copyMissingDetails(managedCanonical, duplicate);
            repo.save(managedCanonical);

            log.warn(
                    "[CHARACTER_NAMING] Merging duplicated character fromCharacterId={} into toCharacterId={} based on official names currentName='{}', formerNames={}",
                    duplicate.getId(), managedCanonical.getId(), currentName, formerNames
            );

            repo.mergeCharacterReferences(duplicate.getId(), managedCanonical.getId());
            repo.deleteCharacter(duplicate.getId());
            managedCanonical = reload(managedCanonical);
        }

        return managedCanonical;
    }

    private List<CharacterEntity> findIdentityCandidates(CharacterEntity knownCharacter,
                                                         String currentName,
                                                         List<String> formerNames) {
        LinkedHashSet<Long> seenIds = new LinkedHashSet<>();
        List<CharacterEntity> candidates = new ArrayList<>();

        addCandidate(candidates, seenIds, knownCharacter);
        repo.findByAnyName(currentName, formerNameCutoff()).ifPresent(c -> addCandidate(candidates, seenIds, c));

        for (String formerName : formerNames) {
            repo.findByAnyName(formerName, formerNameCutoff()).ifPresent(c -> addCandidate(candidates, seenIds, c));
        }

        return candidates;
    }

    private void addCandidate(List<CharacterEntity> candidates, LinkedHashSet<Long> seenIds, CharacterEntity candidate) {
        if (candidate == null || candidate.getId() == null || !seenIds.add(candidate.getId())) {
            return;
        }
        candidates.add(candidate);
    }

    private CharacterEntity createCharacterWithActiveName(String name) {
        CharacterEntity character = repo.save(new CharacterEntity());
        CharacterName characterName = CharacterName.createActive(name, character);
        repo.saveName(characterName);
        character.addName(characterName);
        return repo.save(character);
    }

    private CharacterEntity reload(CharacterEntity character) {
        if (character == null || character.getId() == null) {
            return character;
        }
        return repo.findById(character.getId()).orElse(character);
    }

    private void deactivateActiveNamesExcept(CharacterEntity character, String activeName, Instant when) {
        for (CharacterName name : repo.findNamesForCharacter(character.getId())) {
            if (name.isActive() && !sameName(name.getName(), activeName)) {
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
        if (isBlank(formerName)) {
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

    private void copyMissingDetails(CharacterEntity target, CharacterEntity source) {
        if (target.getSex() == null) target.setSex(source.getSex());
        if (target.getVocation() == null) target.setVocation(source.getVocation());
        if (target.getLevel() == null) target.setLevel(source.getLevel());
        if (target.getAchievementPoints() == null) target.setAchievementPoints(source.getAchievementPoints());
        if (target.getResidence() == null) target.setResidence(source.getResidence());
        if (target.getLastLogin() == null) target.setLastLogin(source.getLastLogin());
        if (target.getAccStatus() == null) target.setAccStatus(source.getAccStatus());
        if (target.getCreationDate() == null) target.setCreationDate(source.getCreationDate());
    }

    private Instant formerNameCutoff() {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .minusMonths(FORMER_NAME_SAFE_RESOLUTION_MONTHS)
                .toInstant();
    }

    private List<String> parseFormerNames(String formerNames, String currentName) {
        if (isBlank(formerNames)) {
            return List.of();
        }

        String normalizedCurrentName = normalizeName(currentName);
        List<String> parsed = new ArrayList<>();

        for (String part : formerNames.split(",")) {
            String normalized = normalizeName(part);
            if (!isBlank(normalized) && !sameName(normalized, normalizedCurrentName)) {
                parsed.add(normalized);
            }
        }

        return normalizeFormerNames(parsed, normalizedCurrentName);
    }

    private List<String> normalizeFormerNames(List<String> formerNames, String currentName) {
        if (formerNames == null || formerNames.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String formerName : formerNames) {
            String value = normalizeName(formerName);
            if (!isBlank(value) && !sameName(value, currentName)) {
                normalized.add(value);
            }
        }

        return List.copyOf(normalized);
    }

    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceAll("\\s+", " ");
    }

    private boolean sameName(String a, String b) {
        return Objects.equals(normalizeName(a).toLowerCase(Locale.ROOT), normalizeName(b).toLowerCase(Locale.ROOT));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
