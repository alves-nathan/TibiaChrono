package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CharacterIdentityMergeService {
    private static final Logger log = LoggerFactory.getLogger(CharacterIdentityMergeService.class);

    private final CharacterRepositoryPort repo;

    public CharacterIdentityMergeService(CharacterRepositoryPort repo) {
        this.repo = repo;
    }

    public CharacterEntity mergeDuplicateCandidatesIntoCanonical(CharacterEntity canonical,
                                                                  List<CharacterEntity> candidates,
                                                                  String currentName,
                                                                  List<String> formerNames) {
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

    private CharacterEntity reload(CharacterEntity character) {
        if (character == null || character.getId() == null) {
            return character;
        }
        return repo.findById(character.getId()).orElse(character);
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
}
