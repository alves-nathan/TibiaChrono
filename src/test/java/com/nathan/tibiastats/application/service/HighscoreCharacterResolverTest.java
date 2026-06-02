package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HighscoreCharacterResolverTest {
    @Test
    void normalizeCharacterNameTrimsWhitespaceAndRemovesTradedSuffix() {
        HighscoreCharacterResolver resolver = new HighscoreCharacterResolver(mock(CharacterNamingService.class));

        assertThat(resolver.normalizeCharacterName(null)).isEmpty();
        assertThat(resolver.normalizeCharacterName("  Knight   One   ( traded ) ")).isEqualTo("Knight One");
        assertThat(resolver.normalizeCharacterName("Mage Two")).isEqualTo("Mage Two");
    }

    @Test
    void resolveCharacterIdCachesByNormalizedLookupKey() {
        CharacterNamingService naming = mock(CharacterNamingService.class);
        CharacterEntity character = new CharacterEntity();
        character.setId(42L);
        when(naming.ensureCharacterForName(" Knight One (traded) ", " Knight One (traded) ")).thenReturn(character);
        HighscoreCharacterResolver resolver = new HighscoreCharacterResolver(naming);
        Map<String, Long> cache = new HashMap<>();

        Long first = resolver.resolveCharacterId(" Knight One (traded) ", cache);
        Long second = resolver.resolveCharacterId("Knight One", cache);

        assertThat(first).isEqualTo(42L);
        assertThat(second).isEqualTo(42L);
        assertThat(cache).containsEntry("knight one", 42L);
        verify(naming, times(1)).ensureCharacterForName(" Knight One (traded) ", " Knight One (traded) ");
    }

    @Test
    void resolveCharacterIdRejectsResolvedCharacterWithoutId() {
        CharacterNamingService naming = mock(CharacterNamingService.class);
        when(naming.ensureCharacterForName("No Id", "No Id")).thenReturn(new CharacterEntity());
        HighscoreCharacterResolver resolver = new HighscoreCharacterResolver(naming);

        assertThatThrownBy(() -> resolver.resolveCharacterId("No Id", new HashMap<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Character was resolved without id: No Id");
    }
}
