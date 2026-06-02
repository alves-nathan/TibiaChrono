package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.Vocation;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterIdentityMergeServiceTest {
    @Test
    void mergeDuplicateCandidatesCopiesMissingDetailsAndRepointsReferences() {
        CharacterRepositoryPort repository = mock(CharacterRepositoryPort.class);
        CharacterIdentityMergeService service = new CharacterIdentityMergeService(repository);
        CharacterEntity canonical = character(1L);
        CharacterEntity duplicate = character(2L);
        duplicate.setSex(CharacterEntity.Sex.female);
        duplicate.setVocation(vocation(4, "Elite Knight"));
        duplicate.setLevel(300);
        duplicate.setAchievementPoints(100);
        duplicate.setResidence("Thais");
        duplicate.setLastLogin(OffsetDateTime.parse("2026-06-01T12:00:00Z"));
        duplicate.setAccStatus("Premium Account");
        duplicate.setCreationDate(Instant.parse("2020-01-01T00:00:00Z"));
        when(repository.findById(1L)).thenReturn(Optional.of(canonical));
        when(repository.findById(2L)).thenReturn(Optional.of(duplicate));
        when(repository.save(any(CharacterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CharacterEntity result = service.mergeDuplicateCandidatesIntoCanonical(
                canonical,
                List.of(canonical, duplicate),
                "Current Name",
                List.of("Former Name")
        );

        assertThat(result).isSameAs(canonical);
        assertThat(canonical.getSex()).isEqualTo(CharacterEntity.Sex.female);
        assertThat(canonical.getVocation()).isSameAs(duplicate.getVocation());
        assertThat(canonical.getLevel()).isEqualTo(300);
        assertThat(canonical.getAchievementPoints()).isEqualTo(100);
        assertThat(canonical.getResidence()).isEqualTo("Thais");
        assertThat(canonical.getLastLogin()).isEqualTo(duplicate.getLastLogin());
        assertThat(canonical.getAccStatus()).isEqualTo("Premium Account");
        assertThat(canonical.getCreationDate()).isEqualTo(duplicate.getCreationDate());
        verify(repository).save(canonical);
        verify(repository).mergeCharacterReferences(2L, 1L);
        verify(repository).deleteCharacter(2L);
    }

    @Test
    void mergeDuplicateCandidatesIgnoresNullIdsAndCanonicalItself() {
        CharacterRepositoryPort repository = mock(CharacterRepositoryPort.class);
        CharacterIdentityMergeService service = new CharacterIdentityMergeService(repository);
        CharacterEntity canonical = character(10L);
        CharacterEntity withoutId = new CharacterEntity();
        when(repository.findById(10L)).thenReturn(Optional.of(canonical));

        CharacterEntity result = service.mergeDuplicateCandidatesIntoCanonical(
                canonical,
                List.of(canonical, withoutId),
                "Current Name",
                List.of()
        );

        assertThat(result).isSameAs(canonical);
        verify(repository, never()).mergeCharacterReferences(any(), any());
        verify(repository, never()).deleteCharacter(any());
        verify(repository, never()).save(any(CharacterEntity.class));
    }

    private CharacterEntity character(Long id) {
        CharacterEntity character = new CharacterEntity();
        character.setId(id);
        return character;
    }

    private Vocation vocation(Integer id, String name) {
        Vocation vocation = new Vocation();
        vocation.setId(id);
        vocation.setName(name);
        return vocation;
    }
}
