package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.AppProperties;
import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CharacterDetailsBatchSelector {
    private final CharacterRepositoryPort characterRepo;
    private final AppProperties appProperties;

    public CharacterDetailsBatchSelector(CharacterRepositoryPort characterRepo,
                                         AppProperties appProperties) {
        this.characterRepo = characterRepo;
        this.appProperties = appProperties;
    }

    public Selection select() {
        int batchSize = Math.max(1, appProperties.getCharacterDetails().getBatchSize());
        List<CharacterName> names = characterRepo.findActiveNamesForDetailsRefresh(batchSize);
        return new Selection(names, batchSize);
    }

    public record Selection(List<CharacterName> names, int batchSize) {}
}
