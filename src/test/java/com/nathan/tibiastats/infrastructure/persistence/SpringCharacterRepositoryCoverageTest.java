package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.model.CharacterStatRecord;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.Vocation;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpringCharacterRepositoryCoverageTest {

    @Test
    void delegatesCharacterNameCharacterAndStatOperationsWithNormalizedInputsAndLimitClamping() {
        CharacterNameJpa names = mock(CharacterNameJpa.class);
        CharacterJpa chars = mock(CharacterJpa.class);
        CharacterStatJpa stats = mock(CharacterStatJpa.class);
        VocationJpa vocations = mock(VocationJpa.class);
        CharacterReferenceMaintenanceJpa maintenance = mock(CharacterReferenceMaintenanceJpa.class);
        SpringCharacterRepository repository = new SpringCharacterRepository(names, chars, stats, vocations, maintenance);
        CharacterEntity character = new CharacterEntity();
        character.setId(10L);
        CharacterName characterName = CharacterName.createActive("Sample Name", character);
        CharacterStatRecord stat = new CharacterStatRecord();
        Instant cutoff = Instant.parse("2026-06-02T12:00:00Z");

        when(names.save(characterName)).thenReturn(characterName);
        when(names.findName("Sample Name")).thenReturn(Optional.of(characterName));
        when(names.findAllByNormalizedName("Sample Name")).thenReturn(List.of(characterName));
        when(names.findNameForCharacter(10L, "Sample Name")).thenReturn(Optional.of(characterName));
        when(names.findNamesForCharacter(10L)).thenReturn(List.of(characterName));
        when(names.findCharacterActiveName(10L)).thenReturn(Optional.of(characterName));
        when(names.findActiveNamesForDetailsRefresh(PageRequest.of(0, 1))).thenReturn(List.of(characterName));
        when(names.findByNameAndActiveTrue("Sample Name")).thenReturn(Optional.of(characterName));
        when(chars.findByAnyName("Sample Name", cutoff)).thenReturn(Optional.of(character));
        when(chars.findById(10L)).thenReturn(Optional.of(character));
        when(chars.save(character)).thenReturn(character);
        when(stats.save(stat)).thenReturn(stat);
        when(stats.findByCat(character, StatCategory.EXPERIENCE)).thenReturn(List.of(stat));

        assertThat(repository.saveName(characterName)).isSameAs(characterName);
        assertThat(repository.findName("  Sample   Name (traded)  ")).contains(characterName);
        assertThat(repository.findNames("  Sample   Name (traded)  ")).containsExactly(characterName);
        assertThat(repository.findNameForCharacter(10L, "  Sample   Name (traded)  ")).contains(characterName);
        assertThat(repository.findNamesForCharacter(10L)).containsExactly(characterName);
        assertThat(repository.findCharacterActiveName(10L)).contains(characterName);
        assertThat(repository.findActiveNamesMissingDetails(0)).containsExactly(characterName);
        assertThat(repository.findActiveName("  Sample   Name (traded)  ")).contains(characterName);
        assertThat(repository.findByAnyName("  Sample   Name (traded)  ", cutoff)).contains(character);
        assertThat(repository.findById(10L)).contains(character);
        assertThat(repository.save(character)).isSameAs(character);
        assertThat(repository.saveStat(stat)).isSameAs(stat);
        assertThat(repository.findStatsBy(character, StatCategory.EXPERIENCE)).containsExactly(stat);
    }

    @Test
    void findVocationByNameOrPromotionNameSkipsBlankInputsAndTrimsValidNames() {
        CharacterNameJpa names = mock(CharacterNameJpa.class);
        CharacterJpa chars = mock(CharacterJpa.class);
        CharacterStatJpa stats = mock(CharacterStatJpa.class);
        VocationJpa vocations = mock(VocationJpa.class);
        CharacterReferenceMaintenanceJpa maintenance = mock(CharacterReferenceMaintenanceJpa.class);
        SpringCharacterRepository repository = new SpringCharacterRepository(names, chars, stats, vocations, maintenance);
        Vocation vocation = new Vocation();
        when(vocations.findByNameOrPromotionName("Royal Paladin")).thenReturn(Optional.of(vocation));

        assertThat(repository.findVocationByNameOrPromotionName(null)).isEmpty();
        assertThat(repository.findVocationByNameOrPromotionName("   ")).isEmpty();
        assertThat(repository.findVocationByNameOrPromotionName("  Royal Paladin  ")).contains(vocation);

        verify(vocations).findByNameOrPromotionName("Royal Paladin");
    }

    @Test
    void markDetailsScrapeAttemptSkipsNullIdsMissingCharactersAndTruncatesSavedErrors() {
        CharacterNameJpa names = mock(CharacterNameJpa.class);
        CharacterJpa chars = mock(CharacterJpa.class);
        CharacterStatJpa stats = mock(CharacterStatJpa.class);
        VocationJpa vocations = mock(VocationJpa.class);
        CharacterReferenceMaintenanceJpa maintenance = mock(CharacterReferenceMaintenanceJpa.class);
        SpringCharacterRepository repository = new SpringCharacterRepository(names, chars, stats, vocations, maintenance);
        Instant attemptedAt = Instant.parse("2026-06-02T12:00:00Z");

        repository.markDetailsScrapeAttempt(null, attemptedAt, "FAILED", "ignored");
        verifyNoInteractions(chars);

        when(chars.findById(99L)).thenReturn(Optional.empty());
        repository.markDetailsScrapeAttempt(99L, attemptedAt, "FAILED", "missing");
        verify(chars).findById(99L);
        verify(chars, never()).save(any());

        CharacterEntity character = new CharacterEntity();
        when(chars.findById(10L)).thenReturn(Optional.of(character));
        String longError = "x".repeat(2_100);

        repository.markDetailsScrapeAttempt(10L, attemptedAt, "FAILED", longError);

        assertThat(character.getDetailsLastScrapedAt()).isEqualTo(attemptedAt);
        assertThat(character.getDetailsLastScrapeStatus()).isEqualTo("FAILED");
        assertThat(character.getDetailsLastScrapeError()).hasSize(2_000);
        verify(chars).save(character);
    }

    @Test
    void mergeCharacterReferencesSkipsInvalidPairsAndDelegatesEveryMaintenanceStepForValidPairs() {
        CharacterNameJpa names = mock(CharacterNameJpa.class);
        CharacterJpa chars = mock(CharacterJpa.class);
        CharacterStatJpa stats = mock(CharacterStatJpa.class);
        VocationJpa vocations = mock(VocationJpa.class);
        CharacterReferenceMaintenanceJpa maintenance = mock(CharacterReferenceMaintenanceJpa.class);
        SpringCharacterRepository repository = new SpringCharacterRepository(names, chars, stats, vocations, maintenance);

        repository.mergeCharacterReferences(null, 2L);
        repository.mergeCharacterReferences(1L, null);
        repository.mergeCharacterReferences(3L, 3L);
        verifyNoInteractions(maintenance);

        repository.mergeCharacterReferences(1L, 2L);

        verify(maintenance).reassignScrapePlayers(1L, 2L);
        verify(maintenance).reassignCharacterWorlds(1L, 2L);
        verify(maintenance).reassignCharacterDeaths(1L, 2L);
        verify(maintenance).reassignGuildCharacters(1L, 2L);
        verify(maintenance).reassignCharacterStatRecords(1L, 2L);
        verify(maintenance).reassignNonDuplicateCharacterNames(1L, 2L);
        verify(maintenance).deleteRemainingCharacterNames(1L);

        repository.deleteCharacter(1L);
        verify(maintenance).deleteCharacter(1L);
    }
}
