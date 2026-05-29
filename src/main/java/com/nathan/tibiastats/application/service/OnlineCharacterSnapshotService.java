package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.Vocation;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class OnlineCharacterSnapshotService {
    private final CharacterRepositoryPort characterRepo;
    private final CharacterNamingService namingService;

    public OnlineCharacterSnapshotService(CharacterRepositoryPort characterRepo,
                                          CharacterNamingService namingService) {
        this.characterRepo = characterRepo;
        this.namingService = namingService;
    }

    public CharacterEntity resolveAndUpdate(WorldScrapeSnapshot.OnlineCharacter player) {
        String normalizedPlayerName = player.name().trim();

        // Avoid one HTTP request per online character during the scheduled world scrape.
        // Rename reconciliation can be done later by a dedicated character-detail job.
        CharacterEntity character = namingService.ensureCharacterForName(normalizedPlayerName, normalizedPlayerName);

        characterRepo.findCharacterActiveName(character.getId()).ifPresent(name -> {
            if (!name.getName().equals(normalizedPlayerName)) {
                namingService.handleRenamed(character, normalizedPlayerName, name);
            }
        });

        boolean characterChanged = false;
        if (player.level() != null && !Objects.equals(character.getLevel(), player.level())) {
            character.setLevel(player.level());
            characterChanged = true;
        }

        if (player.vocation() != null && !player.vocation().isBlank()) {
            var vocation = characterRepo.findVocationByNameOrPromotionName(player.vocation().trim());
            if (vocation.isPresent() && !sameVocation(character.getVocation(), vocation.get())) {
                character.setVocation(vocation.get());
                characterChanged = true;
            }
        }

        if (characterChanged) {
            characterRepo.save(character);
        }

        return character;
    }

    private boolean sameVocation(Vocation current, Vocation scraped) {
        return current != null && scraped != null && Objects.equals(current.getId(), scraped.getId());
    }
}
