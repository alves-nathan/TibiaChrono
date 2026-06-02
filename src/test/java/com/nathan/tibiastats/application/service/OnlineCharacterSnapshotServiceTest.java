package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.model.Vocation;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnlineCharacterSnapshotServiceTest {
    @Test
    void resolveAndUpdateRenamesAndPersistsLevelAndVocationChanges() {
        CharacterRepositoryPort characters = mock(CharacterRepositoryPort.class);
        CharacterNamingService naming = mock(CharacterNamingService.class);
        CharacterEntity character = character(10L, 250, vocation(1, "Knight"));
        CharacterName oldName = CharacterName.createActive("Old Knight", character);
        Vocation scrapedVocation = vocation(2, "Elite Knight");
        when(naming.ensureCharacterForName("Knight One", "Knight One")).thenReturn(character);
        when(characters.findCharacterActiveName(10L)).thenReturn(Optional.of(oldName));
        when(characters.findVocationByNameOrPromotionName("Elite Knight")).thenReturn(Optional.of(scrapedVocation));
        OnlineCharacterSnapshotService service = new OnlineCharacterSnapshotService(characters, naming);

        CharacterEntity resolved = service.resolveAndUpdate(new WorldScrapeSnapshot.OnlineCharacter(
                "  Knight One  ",
                300,
                " Elite Knight "
        ));

        assertThat(resolved).isSameAs(character);
        assertThat(character.getLevel()).isEqualTo(300);
        assertThat(character.getVocation()).isSameAs(scrapedVocation);
        verify(naming).handleRenamed(character, "Knight One", oldName);
        verify(characters).save(character);
    }

    @Test
    void resolveAndUpdateDoesNotSaveWhenSnapshotDoesNotChangeCharacter() {
        CharacterRepositoryPort characters = mock(CharacterRepositoryPort.class);
        CharacterNamingService naming = mock(CharacterNamingService.class);
        Vocation currentVocation = vocation(2, "Elite Knight");
        CharacterEntity character = character(10L, 300, currentVocation);
        CharacterName activeName = CharacterName.createActive("Knight One", character);
        when(naming.ensureCharacterForName("Knight One", "Knight One")).thenReturn(character);
        when(characters.findCharacterActiveName(10L)).thenReturn(Optional.of(activeName));
        when(characters.findVocationByNameOrPromotionName("Elite Knight")).thenReturn(Optional.of(currentVocation));
        OnlineCharacterSnapshotService service = new OnlineCharacterSnapshotService(characters, naming);

        CharacterEntity resolved = service.resolveAndUpdate(new WorldScrapeSnapshot.OnlineCharacter(
                "Knight One",
                300,
                "Elite Knight"
        ));

        assertThat(resolved).isSameAs(character);
        verify(naming, never()).handleRenamed(org.mockito.Mockito.any(), org.mockito.Mockito.anyString(), org.mockito.Mockito.any());
        verify(characters, never()).save(character);
    }

    @Test
    void resolveAndUpdateIgnoresBlankOrUnknownVocationAndNullLevel() {
        CharacterRepositoryPort characters = mock(CharacterRepositoryPort.class);
        CharacterNamingService naming = mock(CharacterNamingService.class);
        CharacterEntity character = character(10L, 300, null);
        when(naming.ensureCharacterForName("Knight One", "Knight One")).thenReturn(character);
        when(characters.findCharacterActiveName(10L)).thenReturn(Optional.empty());
        OnlineCharacterSnapshotService service = new OnlineCharacterSnapshotService(characters, naming);

        CharacterEntity resolved = service.resolveAndUpdate(new WorldScrapeSnapshot.OnlineCharacter(
                "Knight One",
                null,
                " "
        ));

        assertThat(resolved).isSameAs(character);
        verify(characters, never()).findVocationByNameOrPromotionName(org.mockito.Mockito.anyString());
        verify(characters, never()).save(character);
    }

    private static CharacterEntity character(Long id, Integer level, Vocation vocation) {
        CharacterEntity character = new CharacterEntity();
        character.setId(id);
        character.setLevel(level);
        character.setVocation(vocation);
        return character;
    }

    private static Vocation vocation(Integer id, String name) {
        Vocation vocation = new Vocation();
        vocation.setId(id);
        vocation.setName(name);
        vocation.setPromotionName(name);
        return vocation;
    }
}
