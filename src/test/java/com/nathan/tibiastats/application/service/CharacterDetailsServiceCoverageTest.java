package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CharacterDetailsServiceCoverageTest {
    @Test
    void updateMissingDetailsBatchReturnsEmptyWhenSelectorFindsNoNames() {
        CharacterDetailsBatchSelector selector = mock(CharacterDetailsBatchSelector.class);
        CharacterDetailsBatchProcessor processor = mock(CharacterDetailsBatchProcessor.class);
        CharacterDetailsService service = new CharacterDetailsService(selector, processor);
        when(selector.select()).thenReturn(new CharacterDetailsBatchSelector.Selection(List.of(), 25));

        ScrapeJobResult result = service.updateMissingDetailsBatch();

        assertThat(result).isEqualTo(ScrapeJobResult.empty());
        verifyNoInteractions(processor);
    }

    @Test
    void updateMissingDetailsBatchDelegatesSelectedNamesToProcessor() {
        CharacterDetailsBatchSelector selector = mock(CharacterDetailsBatchSelector.class);
        CharacterDetailsBatchProcessor processor = mock(CharacterDetailsBatchProcessor.class);
        CharacterDetailsService service = new CharacterDetailsService(selector, processor);
        CharacterName name = new CharacterName();
        name.setName("Example Character");
        List<CharacterName> names = List.of(name);
        ScrapeJobResult expected = ScrapeJobResult.of(1, 0, 1, 0);
        when(selector.select()).thenReturn(new CharacterDetailsBatchSelector.Selection(names, 1));
        when(processor.process(names)).thenReturn(expected);

        ScrapeJobResult result = service.updateMissingDetailsBatch();

        assertThat(result).isEqualTo(expected);
        verify(processor).process(names);
    }
}
