package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.Character;
import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class NameService {
    private final CharacterRepositoryPort charRepo;
    private final Duration renameCutoff;

    public NameService(CharacterRepositoryPort charRepo, Duration renameCutoff) {
        this.charRepo = charRepo;
        this.renameCutoff = renameCutoff;
    }

    public Character resolve(String name) {
        Optional<CharacterName> active = charRepo.findActiveName(name);
        if (active.isPresent()) {
            return active.get().getCharacter();
        }

        Optional<Character> byAnyName = charRepo.findByName(name);
        if (byAnyName.isPresent()) {
            Character existing = byAnyName.get();
            Instant cutoff = Instant.now().minus(renameCutoff);
            List<CharacterName> oldInactive = charRepo.findCharacterNamesNewerThan(existing.getId(), cutoff);
            boolean matchesFormer = oldInactive.stream().anyMatch(nm -> nm.getName().equalsIgnoreCase(name));
            if (matchesFormer) {
                CharacterName oldActive = existing.getActiveName();
                if (oldActive != null) {
                    charRepo.deactivateName(oldActive);
                }
                CharacterName newName = new CharacterName();
                newName.setCharacter(existing);
                newName.setName(name);
                newName.setActive(true);
                newName.setTimestamp(null);
                charRepo.saveName(newName);
                return existing;
            }
            //TODO: else: treat as new character
        }

        Character c = new Character();
        c = charRepo.save(c);
        CharacterName nm = CharacterName.createActive(name, c);
        charRepo.saveName(nm);
        return c;
    }
}