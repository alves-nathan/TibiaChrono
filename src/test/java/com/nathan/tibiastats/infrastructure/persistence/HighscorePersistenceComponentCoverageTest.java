package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.HighscoreStatRow;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HighscorePersistenceComponentCoverageTest {

    @Test
    void categoryMapperReturnsStableCompactCodesForAllSupportedCategories() {
        HighscoreStatCategoryCodeMapper mapper = new HighscoreStatCategoryCodeMapper();

        assertThat(mapper.code(StatCategory.ACHIEVEMENTS)).isEqualTo((short) 1);
        assertThat(mapper.code(StatCategory.AXE_FIGHTING)).isEqualTo((short) 2);
        assertThat(mapper.code(StatCategory.CHARM_POINTS)).isEqualTo((short) 3);
        assertThat(mapper.code(StatCategory.CLUB_FIGHTING)).isEqualTo((short) 4);
        assertThat(mapper.code(StatCategory.DISTANCE_FIGHTING)).isEqualTo((short) 5);
        assertThat(mapper.code(StatCategory.EXPERIENCE)).isEqualTo((short) 6);
        assertThat(mapper.code(StatCategory.FISHING)).isEqualTo((short) 7);
        assertThat(mapper.code(StatCategory.FIST_FIGHTING)).isEqualTo((short) 8);
        assertThat(mapper.code(StatCategory.GOSHNARS_TAINT)).isEqualTo((short) 9);
        assertThat(mapper.code(StatCategory.LOYALTY_POINTS)).isEqualTo((short) 10);
        assertThat(mapper.code(StatCategory.MAGIC_LEVEL)).isEqualTo((short) 11);
        assertThat(mapper.code(StatCategory.SHIELDING)).isEqualTo((short) 12);
        assertThat(mapper.code(StatCategory.SWORD_FIGHTING)).isEqualTo((short) 13);
        assertThat(mapper.code(StatCategory.DROME_SCORE)).isEqualTo((short) 14);
        assertThat(mapper.code(StatCategory.BOSS_POINTS)).isEqualTo((short) 15);
        assertThat(mapper.code(StatCategory.BOUNTY_POINTS_EARNED)).isEqualTo((short) 16);
        assertThat(mapper.code(StatCategory.WEEKLY_TASKS_COMPLETED)).isEqualTo((short) 17);
    }

    @Test
    void statRecordWriterRoutesExperienceAndCompactRowsToDedicatedWriters() {
        HighscoreExperienceStatRecordWriter experienceWriter = mock(HighscoreExperienceStatRecordWriter.class);
        HighscoreCompactStatRecordWriter compactWriter = mock(HighscoreCompactStatRecordWriter.class);
        HighscoreStatRecordWriter writer = new HighscoreStatRecordWriter(experienceWriter, compactWriter);
        HighscoreStatRow experience = row(StatCategory.EXPERIENCE);
        HighscoreStatRow sword = row(StatCategory.SWORD_FIGHTING);
        when(experienceWriter.upsert(anyList())).thenReturn(2);
        when(compactWriter.upsert(anyList())).thenReturn(3);

        int affected = writer.upsertBatch(List.of(experience, sword));

        assertThat(affected).isEqualTo(5);
        ArgumentCaptor<List<HighscoreStatRow>> experienceRows = rowsCaptor();
        ArgumentCaptor<List<HighscoreStatRow>> compactRows = rowsCaptor();
        verify(experienceWriter).upsert(experienceRows.capture());
        verify(compactWriter).upsert(compactRows.capture());
        assertThat(experienceRows.getValue()).containsExactly(experience);
        assertThat(compactRows.getValue()).containsExactly(sword);
    }

    @Test
    void statRecordWriterShortCircuitsNullAndEmptyBatches() {
        HighscoreExperienceStatRecordWriter experienceWriter = mock(HighscoreExperienceStatRecordWriter.class);
        HighscoreCompactStatRecordWriter compactWriter = mock(HighscoreCompactStatRecordWriter.class);
        HighscoreStatRecordWriter writer = new HighscoreStatRecordWriter(experienceWriter, compactWriter);

        assertThat(writer.upsertBatch(null)).isZero();
        assertThat(writer.upsertBatch(List.of())).isZero();
        verifyNoInteractions(experienceWriter, compactWriter);
    }

    private HighscoreStatRow row(StatCategory category) {
        return new HighscoreStatRow(
                10L,
                20,
                category,
                0,
                LocalDate.parse("2026-06-02"),
                12345L,
                7,
                Instant.parse("2026-06-02T12:00:00Z")
        );
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<HighscoreStatRow>> rowsCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
