package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;


@Service
public class CharacterNamingService {
    private final CharacterRepositoryPort repo;

    public CharacterNamingService(CharacterRepositoryPort repo){ this.repo = repo; }

    @Transactional
    public CharacterEntity ensureCharacterForName(String name){
        var character = repo.findByAnyName(name, CharacterName.INACTIVE_HORIZON);

        if (character.isPresent()) {
            return character.get();
        }

        var c = new CharacterEntity();
        c = repo.save(c);

        var cn = CharacterName.createActive(name, c);
        repo.saveName(cn);

        return c;
    }

    @Transactional
    public void handleRenamed(CharacterEntity c, String newActiveName, String oldName){
        // deactivate old name
        repo.findName(oldName).ifPresent(old -> {
            if (old.getActive().equals(Boolean.TRUE)) {
                old.deactivate(Instant.now());
                //create new name
                var newName = CharacterName.createActive(newActiveName, c);
                repo.saveName(newName);
            }
        });

    }
}
