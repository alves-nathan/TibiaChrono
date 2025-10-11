package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.*;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.*; import java.util.List;

interface CharacterNameJpa extends JpaRepository<CharacterName, Integer>{ Optional<CharacterName> findByNameAndActiveTrue(String name); }
interface CharacterJpa extends JpaRepository<CharacterEntity, Integer>{
    @Query("select c from CharacterEntity c where c.nameId = (select n.id from CharacterName n where n.name = :name and n.active = true)")
    Optional<CharacterEntity> findByActiveName(String name);
}
interface CharacterStatJpa extends JpaRepository<CharacterStatRecord, Integer> {
    @Query("select r from CharacterStatRecord r where r.character = :c and r.category = :cat order by r.date asc")
    List<CharacterStatRecord> findByCat(CharacterEntity c, StatCategory cat);
}

@Repository
public class SpringCharacterRepository implements CharacterRepositoryPort {
    private final CharacterNameJpa names; private final CharacterJpa chars; private final CharacterStatJpa stats;
    public SpringCharacterRepository(CharacterNameJpa n, CharacterJpa c, CharacterStatJpa s){this.names=n; this.chars=c; this.stats=s;}

    public Optional<CharacterName> findActiveName(String name){return names.findByNameAndActiveTrue(name);}
    public CharacterName saveName(CharacterName name){return names.save(name);}
    public CharacterEntity saveCharacter(CharacterEntity c){return chars.save(c);}
    public Optional<CharacterEntity> findByActiveName(String name){return chars.findByActiveName(name);}
    public CharacterStatRecord saveStat(CharacterStatRecord r){return stats.save(r);}
    public List<CharacterStatRecord> findStatsBy(CharacterEntity c, StatCategory category){return stats.findByCat(c, category);}
}