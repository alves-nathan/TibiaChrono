package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.StatCategory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringHighscorePersistenceRepositoryCoverageTest {

    @Test
    void upsertDailyStatBuildsNativeUpsertWithAllExpectedParameters() throws Exception {
        SpringHighscorePersistenceRepository repository = new SpringHighscorePersistenceRepository();
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        injectEntityManager(repository, entityManager);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);

        Instant scrapedAt = Instant.parse("2026-06-05T12:34:56Z");
        LocalDate date = LocalDate.parse("2026-06-05");
        repository.upsertDailyStat(10L, 20, StatCategory.EXPERIENCE, 0, date, 123_456L, 7, scrapedAt);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue())
                .contains("insert into character_statrecords")
                .contains("on conflict (character_id, world_id, category, vocation_filter_id, date)")
                .contains("do update set");

        ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> values = ArgumentCaptor.forClass(Object.class);
        verify(query, org.mockito.Mockito.times(8)).setParameter(names.capture(), values.capture());
        assertThat(names.getAllValues()).containsExactly(
                "characterId",
                "category",
                "vocationFilterId",
                "date",
                "value",
                "rank",
                "worldId",
                "scrapedAt"
        );
        assertThat(values.getAllValues()).containsExactly(
                10L,
                "EXPERIENCE",
                0,
                Date.valueOf(date),
                123_456L,
                7,
                20,
                Timestamp.from(scrapedAt)
        );
        verify(query).executeUpdate();
    }

    private static void injectEntityManager(SpringHighscorePersistenceRepository repository, EntityManager entityManager)
            throws Exception {
        Field field = SpringHighscorePersistenceRepository.class.getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(repository, entityManager);
    }
}
