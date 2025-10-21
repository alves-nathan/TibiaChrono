package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.domain.model.Character;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import com.nathan.tibiastats.domain.port.HighscorePort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant; import java.time.LocalDate;

@Service
public class HighscoreService {
    private final HighscorePort hs; private final WorldRepositoryPort worlds; private final CharacterRepositoryPort chars;
    public HighscoreService(HighscorePort hs, WorldRepositoryPort worlds, CharacterRepositoryPort chars){
        this.hs=hs; this.worlds=worlds; this.chars=chars;
    }

    @Transactional
    public void updateAllHighscores(){
        var date = LocalDate.now(); var ts = Instant.now();
        for (var world : worlds.findAll()){
            for (var category : StatCategory.values()){
                for (int vocationId : new int[]{1,2,3,4,5,6}){
                    int page=1; boolean hasData;
                    do {
                        var rows = hs.fetchHighscores(world.getName(), category, vocationId, page);
                        hasData = !rows.isEmpty();
                        for (var row : rows){
                            var nameOpt = chars.findActiveName(row.name());
                            Character character = nameOpt.flatMap(n-> chars.findByAnyName(row.name())).orElseGet(() -> {
                                var n = new CharacterName(); n.setName(row.name()); n.setActive(true); n = chars.saveName(n);
                                var c = new Character(); c.getNames().add(n); return chars.save(c);
                            });
                            var rec = new CharacterStatRecord();
                            rec.setCharacter(character); rec.setWorld(world); rec.setCategory(category);
                            rec.setDate(date); rec.setTimestamp(ts); rec.setValue(row.value()); rec.setRank(row.rank());
                            chars.saveStat(rec);
                        }
                        page++;
                    } while (hasData && page <= 20); // safety page cap
                }
            }
        }
    }
}