package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterIdentityReconciliationServiceTest {
    @Test
    void reconcileOfficialNamesCreatesMissingIdentityAndRewritesNames() {
        CharacterRepositoryPort repository = mock(CharacterRepositoryPort.class);
        CharacterNameParser nameParser = new CharacterNameParser();
        CharacterObservedNameResolver observedNameResolver = mock(CharacterObservedNameResolver.class);
        CharacterIdentityMergeService mergeService = mock(CharacterIdentityMergeService.class);
        CharacterIdentityReconciliationService service = new CharacterIdentityReconciliationService(
                repository,
                nameParser,
                observedNameResolver,
                mergeService
        );
        Instant cutoff = Instant.parse("2026-01-01T00:00:00Z");
        CharacterEntity canonical = character(50L);
        CharacterName oldActiveName = CharacterName.createActive("Old Active", canonical);
        when(observedNameResolver.formerNameCutoff()).thenReturn(cutoff);
        when(observedNameResolver.createCharacterWithActiveName("Knight New")).thenReturn(canonical);
        when(repository.findByAnyName("Knight New", cutoff)).thenReturn(Optional.empty());
        when(repository.findByAnyName("Old Knight", cutoff)).thenReturn(Optional.empty());
        when(mergeService.mergeDuplicateCandidatesIntoCanonical(canonical, List.of(), "Knight New", List.of("Old Knight")))
                .thenReturn(canonical);
        when(repository.findNamesForCharacter(50L)).thenReturn(List.of(oldActiveName));
        when(repository.findNameForCharacter(50L, "Knight New")).thenReturn(Optional.empty());
        when(repository.findNameForCharacter(50L, "Old Knight")).thenReturn(Optional.empty());
        when(repository.save(canonical)).thenReturn(canonical);
        when(repository.saveName(org.mockito.ArgumentMatchers.any(CharacterName.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CharacterEntity result = service.reconcileOfficialNames(
                null,
                "  Knight   New ",
                List.of("Old Knight", "knight new", " ")
        );

        assertThat(result).isSameAs(canonical);
        assertThat(oldActiveName.isActive()).isFalse();
        ArgumentCaptor<CharacterName> savedName = ArgumentCaptor.forClass(CharacterName.class);
        verify(repository, org.mockito.Mockito.atLeast(3)).saveName(savedName.capture());
        assertThat(savedName.getAllValues())
                .extracting(CharacterName::getName)
                .contains("Old Active", "Knight New", "Old Knight");
        CharacterName current = savedName.getAllValues().stream()
                .filter(name -> "Knight New".equals(name.getName()))
                .findFirst()
                .orElseThrow();
        CharacterName former = savedName.getAllValues().stream()
                .filter(name -> "Old Knight".equals(name.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(current.isActive()).isTrue();
        assertThat(former.isActive()).isFalse();
        assertThat(canonical.getNames()).contains(current, former);
    }

    @Test
    void reconcileOfficialNamesChoosesLowestIdCandidateAsCanonicalBeforeMerge() {
        CharacterRepositoryPort repository = mock(CharacterRepositoryPort.class);
        CharacterNameParser nameParser = new CharacterNameParser();
        CharacterObservedNameResolver observedNameResolver = mock(CharacterObservedNameResolver.class);
        CharacterIdentityMergeService mergeService = mock(CharacterIdentityMergeService.class);
        CharacterIdentityReconciliationService service = new CharacterIdentityReconciliationService(
                repository,
                nameParser,
                observedNameResolver,
                mergeService
        );
        Instant cutoff = Instant.parse("2026-01-01T00:00:00Z");
        CharacterEntity known = character(10L);
        CharacterEntity lowerIdCandidate = character(7L);
        when(observedNameResolver.formerNameCutoff()).thenReturn(cutoff);
        when(repository.findByAnyName("New Name", cutoff)).thenReturn(Optional.of(lowerIdCandidate));
        when(mergeService.mergeDuplicateCandidatesIntoCanonical(
                eq(lowerIdCandidate),
                org.mockito.ArgumentMatchers.anyList(),
                eq("New Name"),
                eq(List.of())
        )).thenReturn(lowerIdCandidate);
        when(repository.findNamesForCharacter(7L)).thenReturn(List.of());
        when(repository.findNameForCharacter(7L, "New Name")).thenReturn(Optional.empty());
        when(repository.save(lowerIdCandidate)).thenReturn(lowerIdCandidate);
        when(repository.saveName(org.mockito.ArgumentMatchers.any(CharacterName.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CharacterEntity result = service.reconcileOfficialNames(known, "New Name", List.of());

        assertThat(result).isSameAs(lowerIdCandidate);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CharacterEntity>> candidates = ArgumentCaptor.forClass(List.class);
        verify(mergeService).mergeDuplicateCandidatesIntoCanonical(
                eq(lowerIdCandidate),
                candidates.capture(),
                eq("New Name"),
                eq(List.of())
        );
        assertThat(candidates.getValue()).containsExactly(known, lowerIdCandidate);
    }

    @Test
    void reconcileOfficialNamesReactivatesCurrentNameAndDeactivatesFormerName() {
        CharacterRepositoryPort repository = mock(CharacterRepositoryPort.class);
        CharacterNameParser nameParser = new CharacterNameParser();
        CharacterObservedNameResolver observedNameResolver = mock(CharacterObservedNameResolver.class);
        CharacterIdentityMergeService mergeService = mock(CharacterIdentityMergeService.class);
        CharacterIdentityReconciliationService service = new CharacterIdentityReconciliationService(
                repository,
                nameParser,
                observedNameResolver,
                mergeService
        );
        Instant cutoff = Instant.parse("2026-01-01T00:00:00Z");
        CharacterEntity canonical = character(1L);
        CharacterName inactiveCurrentName = CharacterName.createInactive(
                "Current",
                canonical,
                Instant.parse("2025-01-01T00:00:00Z")
        );
        CharacterName activeFormerName = CharacterName.createActive("Former", canonical);
        when(observedNameResolver.formerNameCutoff()).thenReturn(cutoff);
        when(repository.findByAnyName("Current", cutoff)).thenReturn(Optional.of(canonical));
        when(repository.findByAnyName("Former", cutoff)).thenReturn(Optional.of(canonical));
        when(mergeService.mergeDuplicateCandidatesIntoCanonical(canonical, List.of(canonical), "Current", List.of("Former")))
                .thenReturn(canonical);
        when(repository.findNamesForCharacter(1L)).thenReturn(List.of());
        when(repository.findNameForCharacter(1L, "Current")).thenReturn(Optional.of(inactiveCurrentName));
        when(repository.findNameForCharacter(1L, "Former")).thenReturn(Optional.of(activeFormerName));
        when(repository.save(canonical)).thenReturn(canonical);

        service.reconcileOfficialNames(canonical, "Current", List.of("Former"));

        assertThat(inactiveCurrentName.isActive()).isTrue();
        assertThat(inactiveCurrentName.getInactiveDate()).isNull();
        assertThat(activeFormerName.isActive()).isFalse();
        assertThat(activeFormerName.getInactiveDate()).isNotNull();
        verify(repository).saveName(inactiveCurrentName);
        verify(repository).saveName(activeFormerName);
    }

    @Test
    void reconcileOfficialNamesRejectsBlankNormalizedCurrentName() {
        CharacterRepositoryPort repository = mock(CharacterRepositoryPort.class);
        CharacterIdentityReconciliationService service = new CharacterIdentityReconciliationService(
                repository,
                new CharacterNameParser(),
                mock(CharacterObservedNameResolver.class),
                mock(CharacterIdentityMergeService.class)
        );

        assertThatThrownBy(() -> service.reconcileOfficialNames("   ", List.of("Former")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Current character name cannot be blank");
    }

    private CharacterEntity character(Long id) {
        CharacterEntity character = new CharacterEntity();
        character.setId(id);
        return character;
    }
}
