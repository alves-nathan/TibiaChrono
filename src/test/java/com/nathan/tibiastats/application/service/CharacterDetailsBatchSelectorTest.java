package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.AppProperties;
import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.CharacterName;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterDetailsBatchSelectorTest {
    @Test
    void selectUsesConfiguredBatchSizeAndRepositoryRotation() {
        CharacterRepositoryPort repository = mock(CharacterRepositoryPort.class);
        AppProperties properties = new AppProperties();
        properties.getCharacterDetails().setBatchSize(7);
        List<CharacterName> names = List.of(activeName("One"), activeName("Two"));
        when(repository.findActiveNamesForDetailsRefresh(7)).thenReturn(names);
        CharacterDetailsBatchSelector selector = new CharacterDetailsBatchSelector(repository, properties);

        CharacterDetailsBatchSelector.Selection selection = selector.select();

        assertThat(selection.batchSize()).isEqualTo(7);
        assertThat(selection.names()).isSameAs(names);
        verify(repository).findActiveNamesForDetailsRefresh(7);
    }

    @Test
    void selectClampsInvalidBatchSizeToOne() {
        CharacterRepositoryPort repository = mock(CharacterRepositoryPort.class);
        AppProperties properties = new AppProperties();
        properties.getCharacterDetails().setBatchSize(0);
        when(repository.findActiveNamesForDetailsRefresh(1)).thenReturn(List.of());
        CharacterDetailsBatchSelector selector = new CharacterDetailsBatchSelector(repository, properties);

        CharacterDetailsBatchSelector.Selection selection = selector.select();

        assertThat(selection.batchSize()).isOne();
        assertThat(selection.names()).isEmpty();
        verify(repository).findActiveNamesForDetailsRefresh(1);
    }

    private CharacterName activeName(String name) {
        CharacterEntity character = new CharacterEntity();
        return CharacterName.createActive(name, character);
    }
}
