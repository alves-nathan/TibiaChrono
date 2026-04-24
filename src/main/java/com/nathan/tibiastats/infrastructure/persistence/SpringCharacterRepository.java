package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*; import java.util.List;

interface CharacterJpa extends JpaRepository<CharacterEntity, Long> {
    @Query("""
        SELECT n.character
        FROM CharacterName n
        WHERE n.name = :name
          AND (
               n.active = true
               OR (n.active = false AND n.inactiveDate > :cutoff)
          )
        """)
    Optional<CharacterEntity> findByAnyName(String name, Instant cutoff);
}
interface CharacterNameJpa extends JpaRepository<CharacterName, Long> {
    Optional<CharacterName> findByNameAndActiveTrue(String name);

    @Query("select cn from CharacterName cn where cn.character.id = :charId and cn.active=true")
    Optional<CharacterName> findCharacterActiveName(Long charId);

    @Query("select cn from CharacterName cn where cn.name = :name")
    Optional<CharacterName> findName(String name);
}
interface CharacterStatJpa extends JpaRepository<CharacterStatRecord, Long> {
    @Query("select r from CharacterStatRecord r where r.character = :c and r.category = :cat order by r.date asc")
    List<CharacterStatRecord> findByCat(CharacterEntity c, StatCategory cat);
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
    public Optional<CharacterName> findName(String name) {
        return names.findName(name);
    }

    @Override
    public Optional<CharacterName> findCharacterActiveName(Long id) {
        return names.findCharacterActiveName(id);
    }

    @Override
    public Optional<CharacterEntity> findByAnyName(String name, Instant cutoff) {
        return chars.findByAnyName(name, cutoff);
    }

    @Override
    public Optional<CharacterEntity> findById(Long id) {
        return chars.findById(id);
    }

    @Override
    public CharacterEntity save(CharacterEntity c){
        return chars.save(c);
    }
    @Override
    public CharacterStatRecord saveStat(CharacterStatRecord r){
        return stats.save(r);
    }
    @Override
    public List<CharacterStatRecord> findStatsBy(CharacterEntity c, StatCategory category){
        return stats.findByCat(c, category);
    }

    @Override
    public Optional<CharacterName> findActiveName(String name) {
        return names.findByNameAndActiveTrue(name);
    }
}