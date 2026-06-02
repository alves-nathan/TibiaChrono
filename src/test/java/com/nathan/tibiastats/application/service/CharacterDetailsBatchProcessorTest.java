package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.port.CharacterDetailPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CharacterDetailsBatchProcessorTest {
    @Test
    void processCountsUpdatedUnchangedNotFoundEmptyAndFailedAttempts() {
        CharacterDetailPort detailPort = mock(CharacterDetailPort.class);
        CharacterDetailsPersistenceService persistence = mock(CharacterDetailsPersistenceService.class);
        CharacterDetailsBatchProcessor processor = new CharacterDetailsBatchProcessor(detailPort, persistence);
        CharacterName changed = activeName(1L, "Changed");
        CharacterName unchanged = activeName(2L, "Unchanged");
        CharacterName missing = activeName(3L, "Missing");
        CharacterName empty = activeName(4L, "Empty");
        CharacterName broken = activeName(5L, "Broken");
        CharacterDetailPort.CharacterDetails changedDetails = usefulDetails("Changed");
        CharacterDetailPort.CharacterDetails unchangedDetails = usefulDetails("Unchanged");
        CharacterDetailPort.CharacterDetails emptyDetails = emptyDetails();
        when(detailPort.fetchCharacterDetails("Changed")).thenReturn(Optional.of(changedDetails));
        when(detailPort.fetchCharacterDetails("Unchanged")).thenReturn(Optional.of(unchangedDetails));
        when(detailPort.fetchCharacterDetails("Missing")).thenReturn(Optional.empty());
        when(detailPort.fetchCharacterDetails("Empty")).thenReturn(Optional.of(emptyDetails));
        when(detailPort.fetchCharacterDetails("Broken")).thenThrow(new IllegalStateException("boom"));
        when(persistence.saveCharacterDetails(eq(1L), eq("Changed"), eq(changedDetails), any(Instant.class)))
                .thenReturn(new CharacterDetailsPersistenceService.SaveResult(true, "UPDATED"));
        when(persistence.saveCharacterDetails(eq(2L), eq("Unchanged"), eq(unchangedDetails), any(Instant.class)))
                .thenReturn(new CharacterDetailsPersistenceService.SaveResult(false, "UNCHANGED"));

        ScrapeJobResult result = processor.process(List.of(changed, unchanged, missing, empty, broken));

        assertThat(result).isEqualTo(ScrapeJobResult.of(5, 0, 2, 3));
        verify(persistence).saveCharacterDetails(eq(1L), eq("Changed"), eq(changedDetails), any(Instant.class));
        verify(persistence).saveCharacterDetails(eq(2L), eq("Unchanged"), eq(unchangedDetails), any(Instant.class));
        verify(persistence).markAttempt(eq(3L), any(Instant.class), eq("NOT_FOUND"), isNull());
        verify(persistence).markAttempt(
                eq(4L),
                any(Instant.class),
                eq("EMPTY"),
                eq("Profile fetched, but parser found no useful fields")
        );
        verify(persistence).markAttempt(eq(5L), any(Instant.class), eq("FAILED"), eq("boom"));
    }

    @Test
    void processReturnsEmptyResultForEmptyBatch() {
        CharacterDetailPort detailPort = mock(CharacterDetailPort.class);
        CharacterDetailsPersistenceService persistence = mock(CharacterDetailsPersistenceService.class);
        CharacterDetailsBatchProcessor processor = new CharacterDetailsBatchProcessor(detailPort, persistence);

        ScrapeJobResult result = processor.process(List.of());

        assertThat(result).isEqualTo(ScrapeJobResult.empty());
        verifyNoInteractions(detailPort, persistence);
    }

    @Test
    void usefulDetailCanComeFromAnySupportedProfileField() {
        CharacterDetailPort detailPort = mock(CharacterDetailPort.class);
        CharacterDetailsPersistenceService persistence = mock(CharacterDetailsPersistenceService.class);
        CharacterDetailsBatchProcessor processor = new CharacterDetailsBatchProcessor(detailPort, persistence);
        CharacterName characterName = activeName(10L, "World Only");
        CharacterDetailPort.CharacterDetails worldOnlyDetails = new CharacterDetailPort.CharacterDetails(
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Antica"
        );
        when(detailPort.fetchCharacterDetails("World Only")).thenReturn(Optional.of(worldOnlyDetails));
        when(persistence.saveCharacterDetails(eq(10L), eq("World Only"), eq(worldOnlyDetails), any(Instant.class)))
                .thenReturn(new CharacterDetailsPersistenceService.SaveResult(false, "UNCHANGED"));

        ScrapeJobResult result = processor.process(List.of(characterName));

        assertThat(result).isEqualTo(ScrapeJobResult.of(1, 0, 1, 0));
        verify(persistence).saveCharacterDetails(eq(10L), eq("World Only"), eq(worldOnlyDetails), any(Instant.class));
    }

    private CharacterName activeName(Long id, String name) {
        CharacterEntity character = new CharacterEntity();
        character.setId(id);
        return CharacterName.createActive(name, character);
    }

    private CharacterDetailPort.CharacterDetails usefulDetails(String currentName) {
        return new CharacterDetailPort.CharacterDetails(
                currentName,
                List.of("Former Name"),
                CharacterEntity.Sex.male,
                "Elite Knight",
                300,
                100,
                "Thais",
                null,
                "Premium Account",
                Instant.parse("2020-01-01T00:00:00Z"),
                "Antica"
        );
    }

    private CharacterDetailPort.CharacterDetails emptyDetails() {
        return new CharacterDetailPort.CharacterDetails(
                null,
                List.of(),
                null,
                null,
                null,
                null,
                " ",
                null,
                " ",
                null,
                " "
        );
    }
}
