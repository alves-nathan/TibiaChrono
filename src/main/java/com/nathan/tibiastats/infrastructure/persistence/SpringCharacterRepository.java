package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.domain.model.Character;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*; import java.util.List;

interface CharacterNameJpa extends JpaRepository<CharacterName, Long> {
    Optional<CharacterName> findByNameAndActiveTrue(String name);
    @Query("select cn from CharacterName cn where cn.character.id = :charId and cn.active = false and cn.timestamp < :cutoff")
    List<CharacterName> findCharacterNamesNewerThan(Long charId, Instant cutoff);
}
interface CharacterStatJpa extends JpaRepository<CharacterStatRecord, Long> {
    @Query("select r from CharacterStatRecord r where r.character = :c and r.category = :cat order by r.date asc")
    List<CharacterStatRecord> findByCat(Character c, StatCategory cat);
}

interface CharacterJpa extends JpaRepository<Character, Long> {
    @Query("SELECT c FROM Character c JOIN c.names n WHERE n.name = :name")
    Optional<Character> findByAnyName(String name);

    @Query("SELECT c FROM Character c WHERE c.id = :id")
    Optional<Character> findById(Long id);

}

@Repository
public class SpringCharacterRepository implements CharacterRepositoryPort {
    private final CharacterNameJpa names;
    private final CharacterJpa chars;
    private final CharacterStatJpa stats;

    public SpringCharacterRepository(CharacterNameJpa names, CharacterJpa chars, CharacterStatJpa stats) {
        this.names = names;
        this.chars = chars;
        this.stats = stats;
    }

    public CharacterName saveName(CharacterName name){
        return names.save(name);
    }

    @Override
    public Optional<Character> findByName(String name) {
        return chars.findByAnyName(name);
    }

    @Override
    public Optional<Character> findById(Long id) {
        return chars.findById(id);
    }

    @Override
    public Character save(Character c){
        return chars.save(c);
    }

    public CharacterStatRecord saveStat(CharacterStatRecord r){
        return stats.save(r);
    }
    public List<CharacterStatRecord> findStatsBy(Character c, StatCategory category){
        return stats.findByCat(c, category);
    }

    @Override
    public Optional<CharacterName> findActiveName(String name) {
        return names.findByNameAndActiveTrue(name);
    }

    @Override
    public Optional<Character> findByAnyName(String name) {
        return chars.findByAnyName(name);
    }

    @Override
    public List<CharacterName> findCharacterNamesNewerThan(Long charId, Instant cutoff) {
        return names.findCharacterNamesNewerThan(charId, cutoff);
    }

    @Override
    public void deactivateName(CharacterName name) {
        name.setActive(false);
        name.setTimestamp(Instant.now());
        names.save(name);
    }
}