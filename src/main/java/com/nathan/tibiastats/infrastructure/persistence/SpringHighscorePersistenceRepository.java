package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.port.HighscorePersistencePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

@Repository
public class SpringHighscorePersistenceRepository implements HighscorePersistencePort {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void upsertDailyStat(Long characterId,
                                Integer worldId,
                                StatCategory category,
                                int vocationFilterId,
                                LocalDate date,
                                long value,
                                int rank,
                                Instant scrapedAt) {
        entityManager.createNativeQuery("""
            insert into character_statrecords
                (character_id, category, vocation_filter_id, date, value, rank, world_id, scraped_at)
            values
                (:characterId, :category, :vocationFilterId, :date, :value, :rank, :worldId, :scrapedAt)
            on conflict (character_id, world_id, category, vocation_filter_id, date)
            do update set
                value = excluded.value,
                rank = excluded.rank,
                scraped_at = excluded.scraped_at
            """)
            .setParameter("characterId", characterId)
            .setParameter("category", category.name())
            .setParameter("vocationFilterId", vocationFilterId)
            .setParameter("date", Date.valueOf(date))
            .setParameter("value", value)
            .setParameter("rank", rank)
            .setParameter("worldId", worldId)
            .setParameter("scrapedAt", Timestamp.from(scrapedAt))
            .executeUpdate();
    }
}
